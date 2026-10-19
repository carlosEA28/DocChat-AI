package com.carlos.DocChat.AI.dto.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConversationResponse(UUID id, UUID documentId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
