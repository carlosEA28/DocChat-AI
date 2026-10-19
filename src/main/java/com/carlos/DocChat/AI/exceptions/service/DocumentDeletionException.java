package com.carlos.DocChat.AI.exceptions.service;

public class DocumentDeletionException extends RuntimeException {
    public DocumentDeletionException(Throwable cause) {
        super("Não foi possível concluir a exclusão do documento.", cause);
    }
}
