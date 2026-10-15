package com.carlos.DocChat.AI.exceptions.service;

public class DocumentPersistenceException extends RuntimeException {
    public DocumentPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
