package com.carlos.DocChat.AI.dto.document;

import java.util.List;
import java.util.UUID;

public record ConversationAnswerDto(UUID conversationId, String question, String answer,
                                    List<CitationDTO> citations) {
    public static ConversationAnswerDto from(UUID conversationId, RagAnswerDto answer) {
        return new ConversationAnswerDto(conversationId, answer.question(), answer.answer(), answer.citations());
    }
}
