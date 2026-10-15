package com.carlos.DocChat.AI.dto.document;

import com.carlos.DocChat.AI.model.DocumentStatus;

public record CreateDocumentDto(String filename, String contentType, long size, String s3Key, DocumentStatus status) {
}
