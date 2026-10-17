package com.carlos.DocChat.AI.dto.document;

import java.util.List;

public record RagAnswerDto(String question, String answer, List<CitationDTO> citations) {
}
