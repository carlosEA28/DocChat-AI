package com.carlos.DocChat.AI.exceptions.service;

public class DocumentReadException extends RuntimeException {
    public DocumentReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
