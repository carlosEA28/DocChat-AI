package com.carlos.DocChat.AI.exceptions.service;

public class InvalidPdfException extends RuntimeException {
    public InvalidPdfException(String message) {
        super(message);
    }
}
