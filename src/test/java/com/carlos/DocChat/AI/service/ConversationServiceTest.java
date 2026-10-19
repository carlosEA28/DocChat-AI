package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.dto.document.RagAnswerDto;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.model.ConversationEntity;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.MessageEntity;
import com.carlos.DocChat.AI.model.MessageRole;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationServiceTest {
    private final DocumentService documentService = mock(DocumentService.class);
    private final ConversationPersistenceService persistence = mock(ConversationPersistenceService.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ConversationService service = new ConversationService(documentService, persistence,
            conversations, messages, documents);

    @Test
    void startsAndContinuesWithDocumentAttachedToConversation() {
        UUID documentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        RagAnswerDto answer = new RagAnswerDto("Pergunta", "Resposta", List.of());
        when(documentService.searchSimilarChunks(documentId, "Pergunta")).thenReturn(answer);
        when(persistence.create(documentId, answer)).thenReturn(conversationId);
        ConversationEntity conversation = new ConversationEntity();
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        conversation.setDocument(document);
        when(conversations.findByIdWithDocument(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.findTop8ByConversationIdOrderBySequenceNumberDesc(conversationId))
                .thenReturn(List.of(message(2, MessageRole.ASSISTANT, "Resposta anterior"),
                        message(1, MessageRole.USER, "Pergunta anterior")));
        when(documentService.answerWithHistory(documentId, "Pergunta",
                "USER: Pergunta anterior\nASSISTANT: Resposta anterior")).thenReturn(answer);

        assertEquals(conversationId, service.start(documentId, "Pergunta").conversationId());
        assertEquals(conversationId, service.continueConversation(conversationId, "Pergunta").conversationId());
        verify(persistence).create(documentId, answer);
        verify(persistence).append(conversationId, answer);
        verify(documentService).answerWithHistory(documentId, "Pergunta",
                "USER: Pergunta anterior\nASSISTANT: Resposta anterior");
    }

    @Test
    void failedAnswerDoesNotCreateConversation() {
        UUID documentId = UUID.randomUUID();
        when(documentService.searchSimilarChunks(documentId, "Pergunta"))
                .thenThrow(new IllegalStateException("model unavailable"));

        assertThrows(IllegalStateException.class, () -> service.start(documentId, "Pergunta"));
        verifyNoInteractions(persistence);
    }

    @Test
    void deletedConversationCannotBeContinuedOrRead() {
        UUID id = UUID.randomUUID();
        assertThrows(ConversationNotFoundException.class, () -> service.continueConversation(id, "Pergunta"));
        assertThrows(ConversationNotFoundException.class, () -> service.messages(id));
        verifyNoInteractions(persistence);
    }

    @Test
    void missingDocumentHasNoConversationList() {
        assertThrows(DocumentNotFoundException.class, () -> service.list(UUID.randomUUID()));
    }

    @Test
    void failedFollowUpDoesNotAppendMessages() {
        UUID documentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setDocument(document);
        when(conversations.findByIdWithDocument(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.findTop8ByConversationIdOrderBySequenceNumberDesc(conversationId)).thenReturn(List.of());
        when(documentService.answerWithHistory(documentId, "Pergunta", ""))
                .thenThrow(new IllegalStateException("model unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.continueConversation(conversationId, "Pergunta"));
        verifyNoInteractions(persistence);
    }

    @Test
    void limitsLongHistorySentToRag() {
        UUID documentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setDocument(document);
        when(conversations.findByIdWithDocument(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.findTop8ByConversationIdOrderBySequenceNumberDesc(conversationId))
                .thenReturn(List.of(message(2, MessageRole.ASSISTANT, "x".repeat(20_000)),
                        message(1, MessageRole.USER, "Pergunta antiga")));
        RagAnswerDto answer = new RagAnswerDto("Pergunta", "Resposta", List.of());
        when(documentService.answerWithHistory(org.mockito.ArgumentMatchers.eq(documentId),
                org.mockito.ArgumentMatchers.eq("Pergunta"), anyString())).thenReturn(answer);

        service.continueConversation(conversationId, "Pergunta");

        org.mockito.ArgumentCaptor<String> history = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(documentService).answerWithHistory(org.mockito.ArgumentMatchers.eq(documentId),
                org.mockito.ArgumentMatchers.eq("Pergunta"), history.capture());
        assertEquals(12_000, history.getValue().length());
        assertEquals("ASSISTANT: ", history.getValue().substring(0, 11));
    }

    private MessageEntity message(long sequence, MessageRole role, String content) {
        MessageEntity message = new MessageEntity();
        message.setSequenceNumber(sequence);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
