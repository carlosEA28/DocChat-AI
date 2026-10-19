package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentDeletionConflictException;
import com.carlos.DocChat.AI.exceptions.service.DocumentDeletionException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DocumentDeletionService {
    private final DocumentRepository documents;
    private final ConversationRepository conversations;
    private final VectorStore vectorStore;
    private final S3Config s3Config;

    public DocumentDeletionService(DocumentRepository documents, ConversationRepository conversations,
                                   VectorStore vectorStore, S3Config s3Config) {
        this.documents = documents;
        this.conversations = conversations;
        this.vectorStore = vectorStore;
        this.s3Config = s3Config;
    }

    public void deleteByConversationId(UUID conversationId) {
        UUID documentId = conversations.findByIdWithDocument(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId))
                .getDocument().getId();
        deleteByDocumentId(documentId);
    }

    public void deleteByDocumentId(UUID documentId) {
        DocumentEntity document = documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentDeletionConflictException();
        }

        try {
            vectorStore.delete("documentId == '" + documentId + "'");
            s3Config.deletePdf(document.getS3Key());
            documents.deleteDocumentById(documentId);
        } catch (RuntimeException exception) {
            throw new DocumentDeletionException(exception);
        }
    }
}
