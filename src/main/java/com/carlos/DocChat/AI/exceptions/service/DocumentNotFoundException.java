package com.carlos.DocChat.AI.exceptions.service;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Documento não encontrado: " + id);
    }
}
