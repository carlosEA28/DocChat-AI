package com.carlos.DocChat.AI.exceptions.service;

public class DocumentDeletionConflictException extends RuntimeException {
    public DocumentDeletionConflictException() {
        super("O documento está em processamento. Tente excluir novamente após o processamento terminar.");
    }
}
