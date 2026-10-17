package com.carlos.DocChat.AI.exceptions.service;

public class InvalidQuestionException extends RuntimeException {
    public InvalidQuestionException(String message) {
        super(message);
    }
}
