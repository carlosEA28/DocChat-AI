package com.carlos.DocChat.AI.repository;

import com.carlos.DocChat.AI.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
}
