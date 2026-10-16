package com.carlos.DocChat.AI.dto.document;

import org.springframework.ai.document.Document;

public record SearchResultsDto(
        String documentId,
        Double score,
        String excerpt
) {
    public static SearchResultsDto from(Document document) {
        return new SearchResultsDto(
                (String) document.getMetadata().get("documentId"),
                document.getScore(),
                document.getText()
        );
    }
}