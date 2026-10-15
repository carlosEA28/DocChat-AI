package com.carlos.DocChat.AI.dto.document;

import com.carlos.DocChat.AI.model.DocumentStatus;

import java.util.UUID;

public record DocumentDto(UUID id, String filename, String contentType, long size, String s3Key, DocumentStatus status) {
}
