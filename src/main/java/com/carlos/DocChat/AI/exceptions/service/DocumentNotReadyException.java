package com.carlos.DocChat.AI.exceptions.service;

import com.carlos.DocChat.AI.model.DocumentStatus;

public class DocumentNotReadyException extends RuntimeException {
    public DocumentNotReadyException(DocumentStatus status) {
        super("Document is not ready for search. Current status: " + status);
    }
}
