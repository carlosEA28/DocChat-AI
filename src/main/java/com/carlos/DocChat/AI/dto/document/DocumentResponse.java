package com.carlos.DocChat.AI.dto.document;

import com.carlos.DocChat.AI.model.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        long size,
        OffsetDateTime uploadedAt,
        DocumentStatus status,
        OffsetDateTime updatedAt
) {
}
