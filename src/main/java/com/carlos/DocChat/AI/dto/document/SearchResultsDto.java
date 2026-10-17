package com.carlos.DocChat.AI.dto.document;

import org.springframework.ai.document.Document;

public record SearchResultsDto(
        String excerpt,
        Double score
) {
    public static SearchResultsDto from(Document document) {
        return new SearchResultsDto(
                document.getText(),
                document.getScore()
        );
    }
}
