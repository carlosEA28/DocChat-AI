package com.carlos.DocChat.AI.repository;

import com.carlos.DocChat.AI.model.ConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findByDocumentIdOrderByCreatedAtAscIdAsc(UUID documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c join fetch c.document where c.id = :id")
    Optional<ConversationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c from ConversationEntity c join fetch c.document where c.id = :id")
    Optional<ConversationEntity> findByIdWithDocument(@Param("id") UUID id);
}
