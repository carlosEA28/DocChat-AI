package com.carlos.DocChat.AI.repository;

import com.carlos.DocChat.AI.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("delete from DocumentEntity d where d.id = :id")
    void deleteDocumentById(@Param("id") UUID id);
}
