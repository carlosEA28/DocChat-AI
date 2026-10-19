package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentDeletionConflictException;
import com.carlos.DocChat.AI.exceptions.service.DocumentDeletionException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.model.ConversationEntity;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentDeletionServiceTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final S3Config s3Config = mock(S3Config.class);
    private final DocumentDeletionService service = new DocumentDeletionService(documents, conversations,
            vectorStore, s3Config);

    @Test
    void deletesVectorsThenS3ObjectThenMetadata() {
        DocumentEntity document = document(DocumentStatus.READY);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));

        service.deleteByDocumentId(document.getId());

        InOrder order = inOrder(documents, vectorStore, s3Config);
        order.verify(vectorStore).delete("documentId == '" + document.getId() + "'");
        order.verify(s3Config).deletePdf(document.getS3Key());
        order.verify(documents).deleteById(document.getId());
    }

    @Test
    void deletingAnyConversationDeletesItsDocument() {
        DocumentEntity document = document(DocumentStatus.READY);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setDocument(document);
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdWithDocument(conversationId)).thenReturn(Optional.of(conversation));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));

        service.deleteByConversationId(conversationId);

        verify(documents).deleteById(document.getId());
        verify(s3Config).deletePdf(document.getS3Key());
    }

    @Test
    void missingIdsAndProcessingDocumentDoNotTouchExternalStorage() {
        UUID missing = UUID.randomUUID();
        assertThrows(DocumentNotFoundException.class, () -> service.deleteByDocumentId(missing));
        assertThrows(ConversationNotFoundException.class, () -> service.deleteByConversationId(missing));

        DocumentEntity processing = document(DocumentStatus.PROCESSING);
        when(documents.findById(processing.getId())).thenReturn(Optional.of(processing));
        assertThrows(DocumentDeletionConflictException.class,
                () -> service.deleteByDocumentId(processing.getId()));
        verify(documents, never()).deleteById(processing.getId());
        verifyNoInteractions(vectorStore, s3Config);
    }

    @Test
    void vectorFailureLeavesDocumentForRetry() {
        DocumentEntity document = document(DocumentStatus.READY);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        String filter = "documentId == '" + document.getId() + "'";
        doThrow(new IllegalStateException("index offline")).doNothing().when(vectorStore).delete(filter);

        assertThrows(DocumentDeletionException.class, () -> service.deleteByDocumentId(document.getId()));
        verify(s3Config, never()).deletePdf(document.getS3Key());
        verify(documents, never()).deleteById(document.getId());

        service.deleteByDocumentId(document.getId());
        verify(s3Config).deletePdf(document.getS3Key());
        verify(documents).deleteById(document.getId());
    }

    @Test
    void s3FailureCanBeRetriedAfterVectorDeletion() {
        DocumentEntity document = document(DocumentStatus.READY);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        doThrow(new IllegalStateException("S3 offline")).doNothing()
                .when(s3Config).deletePdf(document.getS3Key());

        assertThrows(DocumentDeletionException.class, () -> service.deleteByDocumentId(document.getId()));
        verify(documents, never()).deleteById(document.getId());

        service.deleteByDocumentId(document.getId());
        verify(documents).deleteById(document.getId());
    }

    @Test
    void databaseFailureCanBeRetriedAfterExternalDeletion() {
        DocumentEntity document = document(DocumentStatus.READY);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        doThrow(new IllegalStateException("database offline")).doNothing()
                .when(documents).deleteById(document.getId());

        assertThrows(DocumentDeletionException.class, () -> service.deleteByDocumentId(document.getId()));

        service.deleteByDocumentId(document.getId());
        verify(vectorStore, org.mockito.Mockito.times(2))
                .delete("documentId == '" + document.getId() + "'");
        verify(s3Config, org.mockito.Mockito.times(2)).deletePdf(document.getS3Key());
        verify(documents, org.mockito.Mockito.times(2)).deleteById(document.getId());
    }

    private DocumentEntity document(DocumentStatus status) {
        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        document.setS3Key("uploads/" + document.getId() + ".pdf");
        document.setStatus(status);
        return document;
    }
}
