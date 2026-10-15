package com.carlos.DocChat.AI.exceptions.service;

public class PdfTooLargeException extends RuntimeException {
    public PdfTooLargeException(String message) {
        super(message);
    }
}
