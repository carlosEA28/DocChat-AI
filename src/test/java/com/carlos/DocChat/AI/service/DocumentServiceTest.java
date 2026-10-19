package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
    private final S3Config s3Config = mock(S3Config.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatClient.Builder chatBuilder = mock(ChatClient.Builder.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    private final DocumentService service = createService();

    private DocumentService createService() {
        when(chatBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Advisor[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Resposta");
        return new DocumentService(repository, s3Config, vectorStore, chatBuilder);
    }

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
                .metadata("documentId", ready.getId().toString())
                .metadata("page_number", 3).score(0.82).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        var results = service.searchSimilarChunks(ready.getId(), "Pergunta");

        assertEquals("Pergunta", results.question());
        assertEquals("Resposta", results.answer());
        assertEquals(1, results.citations().size());
        assertEquals("Trecho relevante", results.citations().getFirst().excerpt());
        assertEquals(0.82, results.citations().getFirst().score());
        assertEquals("arquivo.pdf", results.citations().getFirst().title());
        assertEquals(ready.getId().toString(), results.citations().getFirst().source());
        assertEquals(3, results.citations().getFirst().page());
        assertEquals("RETRIEVED_UNVERIFIED", results.citations().getFirst().evidenceStatus());
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
    void keepsFullRetrievedChunkAndLeavesMissingPageUnknown() {
        DocumentEntity ready = document(DocumentStatus.READY);
        when(repository.findById(ready.getId())).thenReturn(Optional.of(ready));
        String fullText = "Trecho com parágrafo.\n\n" + "conteúdo ".repeat(70);
        Document chunk = Document.builder().text(fullText)
                .metadata("documentId", ready.getId().toString()).score(0.73).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        var citation = service.searchSimilarChunks(ready.getId(), "Pergunta").citations().getFirst();

        assertEquals(fullText, citation.excerpt());
        assertEquals(null, citation.page());
        assertEquals(0.73, citation.score());
    }

    @Test
    void normalizesHorizontalPdfSpacingWithoutJoiningParagraphs() {
        assertEquals("Título\nPrimeiro parágrafo.\n\nSegundo parágrafo.\nTabela A B",
                DocumentService.normalizePdfText(
                        "  Título  \nPrimeiro   parágrafo.\n\nSegundo\tparágrafo.\nTabela   A    B"));
    }

    @Test
    void ingestionKeepsOriginalFilenameAndPhysicalPageOnEachChunk() throws Exception {
        DocumentEntity pending = document(DocumentStatus.PENDING);
        pending.setS3Key("uploads/internal-key.pdf");
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(s3Config.downloadPdf(pending.getS3Key())).thenReturn(twoPagePdf());

        service.ingestDocument(pending.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> chunks = captor.getValue();
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getText().contains("First page")
                && Integer.valueOf(1).equals(chunk.getMetadata().get("page_number"))));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getText().contains("Second page")
                && Integer.valueOf(2).equals(chunk.getMetadata().get("page_number"))));
        for (Document chunk : chunks) {
            assertEquals(pending.getId().toString(), chunk.getMetadata().get("documentId"));
            assertEquals("arquivo.pdf", chunk.getMetadata().get("title"));
            assertTrue(!chunk.getMetadata().containsKey("source"));
            assertTrue(!chunk.getMetadata().containsKey("s3Key"));
        }
        assertEquals(DocumentStatus.READY, pending.getStatus());
    }

    private byte[] twoPagePdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : List.of("First page content for search.", "Second page content for search.")) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void returnsEmptyListWhenNoChunksMatch() {
        DocumentEntity ready = document(DocumentStatus.READY);
        when(repository.findById(ready.getId())).thenReturn(Optional.of(ready));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertTrue(service.searchSimilarChunks(ready.getId(), "Pergunta").citations().isEmpty());
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
