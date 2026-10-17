package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceTest {
    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentService service = new DocumentService(repository, mock(S3Config.class), vectorStore);

    @Test
    void listsEveryRegisteredDocumentWithPublicMetadata() {
        DocumentEntity pending = document(DocumentStatus.PENDING);
        DocumentEntity ready = document(DocumentStatus.READY);
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(pending, ready));

        var result = service.list();

        assertEquals(2, result.size());
        assertEquals(pending.getId(), result.get(0).id());
        assertEquals(pending.getFilename(), result.get(0).filename());
        assertEquals(pending.getSize(), result.get(0).size());
        assertEquals(pending.getCreatedAt(), result.get(0).uploadedAt());
        assertEquals(DocumentStatus.PENDING, result.get(0).status());
        assertEquals(ready.getId(), result.get(1).id());
        assertEquals(DocumentStatus.READY, result.get(1).status());
        verify(repository).findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void getsDocumentById() {
        DocumentEntity document = document(DocumentStatus.PROCESSING);
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = service.get(document.getId());

        assertEquals(document.getId(), result.id());
        assertEquals(DocumentStatus.PROCESSING, result.status());
        assertEquals(document.getUpdatedAt(), result.updatedAt());
    }

    @Test
    void missingDocumentRaisesNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.get(id));
    }

    @Test
    void searchesReadyDocumentWithItsOwnFilterAndMapsResults() {
        DocumentEntity ready = document(DocumentStatus.READY);
        when(repository.findById(ready.getId())).thenReturn(Optional.of(ready));
        Document chunk = Document.builder().text("Trecho relevante")
                .metadata("documentId", ready.getId().toString()).score(0.82).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        var results = service.searchSimilarChunks(ready.getId(), "Pergunta");

        assertEquals(1, results.size());
        assertEquals("Trecho relevante", results.getFirst().excerpt());
        assertEquals(0.82, results.getFirst().score());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest search = captor.getValue();
        assertEquals("Pergunta", search.getQuery());
        assertEquals(5, search.getTopK());
        assertEquals(0.4, search.getSimilarityThreshold());
        assertTrue(search.getFilterExpression().toString().contains(ready.getId().toString()));
        assertTrue(search.getFilterExpression().toString().contains("documentId"));
    }

    @Test
    void returnsEmptyListWhenNoChunksMatch() {
        DocumentEntity ready = document(DocumentStatus.READY);
        when(repository.findById(ready.getId())).thenReturn(Optional.of(ready));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertTrue(service.searchSimilarChunks(ready.getId(), "Pergunta").isEmpty());
    }

    @Test
    void doesNotSearchMissingOrUnreadyDocuments() {
        UUID unknown = UUID.randomUUID();
        assertThrows(DocumentNotFoundException.class, () -> service.searchSimilarChunks(unknown, "Pergunta"));

        for (DocumentStatus status : List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING, DocumentStatus.FAILED)) {
            DocumentEntity document = document(status);
            when(repository.findById(document.getId())).thenReturn(Optional.of(document));
            assertThrows(DocumentNotReadyException.class,
                    () -> service.searchSimilarChunks(document.getId(), "Pergunta"));
        }
        verifyNoInteractions(vectorStore);
    }

    private DocumentEntity document(DocumentStatus status) {
        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        document.setFilename("arquivo.pdf");
        document.setSize(123);
        document.setStatus(status);
        ReflectionTestUtils.setField(document, "createdAt", OffsetDateTime.parse("2026-10-15T10:00:00Z"));
        ReflectionTestUtils.setField(document, "updatedAt", OffsetDateTime.parse("2026-10-15T11:00:00Z"));
        return document;
    }
}
