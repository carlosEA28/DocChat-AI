package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.dto.document.ConversationAnswerDto;
import com.carlos.DocChat.AI.dto.document.ConversationResponse;
import com.carlos.DocChat.AI.dto.document.MessageResponse;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.model.MessageEntity;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private static final int MAX_HISTORY_CHARACTERS = 12_000;
    private final DocumentService documentService;
    private final ConversationPersistenceService persistence;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final DocumentRepository documents;

    public ConversationService(DocumentService documentService, ConversationPersistenceService persistence,
                               ConversationRepository conversations, MessageRepository messages,
                               DocumentRepository documents) {
        this.documentService = documentService;
        this.persistence = persistence;
        this.conversations = conversations;
        this.messages = messages;
        this.documents = documents;
    }

    public ConversationAnswerDto start(UUID documentId, String question) {
        var answer = documentService.searchSimilarChunks(documentId, question);
        UUID conversationId = persistence.create(documentId, answer);
        return ConversationAnswerDto.from(conversationId, answer);
    }

    public ConversationAnswerDto continueConversation(UUID conversationId, String question) {
        var conversation = conversations.findByIdWithDocument(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        String history = recentHistory(conversationId);
        var answer = documentService.answerWithHistory(conversation.getDocument().getId(), question, history);
        persistence.append(conversationId, answer);
        return ConversationAnswerDto.from(conversationId, answer);
    }

    private String recentHistory(UUID conversationId) {
        StringBuilder history = new StringBuilder();
        for (MessageEntity message : messages.findTop8ByConversationIdOrderBySequenceNumberDesc(conversationId)) {
            String line = message.getRole() + ": " + message.getContent();
            int remaining = MAX_HISTORY_CHARACTERS - history.length();
            if (line.length() >= remaining) {
                if (history.isEmpty()) {
                    history.append(line, 0, remaining);
                }
                break;
            }
            history.insert(0, line + "\n");
        }
        return history.toString().strip();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID documentId) {
        if (!documents.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
        return conversations.findByDocumentIdOrderByCreatedAtAscIdAsc(documentId).stream()
                .map(c -> new ConversationResponse(c.getId(), documentId, c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID conversationId) {
        if (!conversations.existsById(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return messages.findByConversationIdOrderBySequenceNumberAsc(conversationId).stream()
                .map(m -> new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt(),
                        m.getSequenceNumber()))
                .toList();
    }
}
