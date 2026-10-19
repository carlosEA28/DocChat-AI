package com.carlos.DocChat.AI.dto.document;

import com.carlos.DocChat.AI.model.MessageRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(UUID id, MessageRole role, String content, OffsetDateTime createdAt,
                              long sequenceNumber) {
}
