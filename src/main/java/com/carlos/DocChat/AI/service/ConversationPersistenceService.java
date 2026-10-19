package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.dto.document.RagAnswerDto;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.model.ConversationEntity;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.model.MessageEntity;
import com.carlos.DocChat.AI.model.MessageRole;
import com.carlos.DocChat.AI.repository.ConversationRepository;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationPersistenceService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final DocumentRepository documents;

    public ConversationPersistenceService(ConversationRepository conversations, MessageRepository messages,
                                          DocumentRepository documents) {
        this.conversations = conversations;
        this.messages = messages;
        this.documents = documents;
    }

    // metodo para criar uma nova conversa e salvar a primeira pergunta e resposta
    @Transactional
    public UUID create(UUID documentId, RagAnswerDto answer) {
        DocumentEntity document = documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        requireReady(document);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setDocument(document);
        conversation = conversations.save(conversation);
        savePair(conversation, 1, answer);
        return conversation.getId();
    }

    // metodo para adicionar uma pergunta e resposta a uma conversa existente
    @Transactional
    public void append(UUID conversationId, RagAnswerDto answer) {
        ConversationEntity conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        requireReady(conversation.getDocument());

        long nextSequence = messages.findFirstByConversationIdOrderBySequenceNumberDesc(conversationId)
                .map(message -> message.getSequenceNumber() + 1)
                .orElse(1L);
        savePair(conversation, nextSequence, answer);
        conversation.touch();
    }

    private void requireReady(DocumentEntity document) {
        if (document.getStatus() != DocumentStatus.READY) {
            throw new DocumentNotReadyException(document.getStatus());
        }
    }

    private void savePair(ConversationEntity conversation, long sequence, RagAnswerDto answer) {
        messages.saveAll(List.of(message(conversation, sequence, MessageRole.USER, answer.question()),
                message(conversation, sequence + 1, MessageRole.ASSISTANT, answer.answer())));
    }

    private MessageEntity message(ConversationEntity conversation, long sequence, MessageRole role, String content) {
        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setSequenceNumber(sequence);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
