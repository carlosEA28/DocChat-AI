package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.dto.document.RagAnswerDto;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.model.ConversationEntity;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.model.MessageEntity;
import com.carlos.DocChat.AI.model.MessageRole;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationPersistenceServiceTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ConversationPersistenceService service = new ConversationPersistenceService(conversations,
            messages, documents);
    private final RagAnswerDto answer = new RagAnswerDto("Pergunta", "Resposta", List.of());

    @Test
    void createsConversationWithFirstQuestionAndAnswer() {
        UUID documentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setStatus(DocumentStatus.READY);
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(conversations.save(org.mockito.ArgumentMatchers.any(ConversationEntity.class))).thenAnswer(call -> {
            ConversationEntity conversation = call.getArgument(0);
            ReflectionTestUtils.setField(conversation, "id", conversationId);
            return conversation;
        });

        assertEquals(conversationId, service.create(documentId, answer));
        assertPair(1, 2);
    }

    @Test
    void appendsAfterLastSequence() {
        UUID conversationId = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setStatus(DocumentStatus.READY);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setDocument(document);
        MessageEntity last = new MessageEntity();
        last.setSequenceNumber(4);
        when(conversations.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.findFirstByConversationIdOrderBySequenceNumberDesc(conversationId))
                .thenReturn(Optional.of(last));

        service.append(conversationId, answer);

        assertPair(5, 6);
        verify(conversations).findByIdForUpdate(conversationId);
    }

    @Test
    void unreadyDocumentDoesNotSaveMessages() {
        UUID id = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setStatus(DocumentStatus.PENDING);
        when(documents.findById(id)).thenReturn(Optional.of(document));

        assertThrows(DocumentNotReadyException.class, () -> service.create(id, answer));
        verify(conversations, never()).save(org.mockito.ArgumentMatchers.any());
        verify(messages, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertPair(long questionSequence, long answerSequence) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(messages).saveAll(captor.capture());
        List<MessageEntity> pair = captor.getValue();
        assertEquals(2, pair.size());
        assertEquals(MessageRole.USER, pair.get(0).getRole());
        assertEquals("Pergunta", pair.get(0).getContent());
        assertEquals(questionSequence, pair.get(0).getSequenceNumber());
        assertEquals(MessageRole.ASSISTANT, pair.get(1).getRole());
        assertEquals("Resposta", pair.get(1).getContent());
        assertEquals(answerSequence, pair.get(1).getSequenceNumber());
    }
}
