package com.carlos.DocChat.AI.repository;

import com.carlos.DocChat.AI.model.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);
}
