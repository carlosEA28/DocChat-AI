package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.dto.document.QuestionRequest;
import com.carlos.DocChat.AI.exceptions.service.InvalidQuestionException;

public final class QuestionValidation {
    private QuestionValidation() {
    }

    public static String validate(QuestionRequest request) {
        if (request == null || request.question() == null) {
            throw new InvalidQuestionException("A pergunta é obrigatória.");
        }
        String question = request.question().strip();
        if (question.isEmpty()) {
            throw new InvalidQuestionException("A pergunta não pode estar vazia.");
        }
        if (question.codePointCount(0, question.length()) > 2000) {
            throw new InvalidQuestionException("A pergunta deve ter no máximo 2.000 caracteres.");
        }
        return question;
    }
}
