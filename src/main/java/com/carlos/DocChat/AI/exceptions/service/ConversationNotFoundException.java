package com.carlos.DocChat.AI.exceptions.service;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(UUID id) {
        super("Conversa não encontrada: " + id);
    }
}
