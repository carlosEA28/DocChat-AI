package com.carlos.DocChat.AI.dto.document;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID documentId, String question, List<SearchResultsDto> results) {
}
