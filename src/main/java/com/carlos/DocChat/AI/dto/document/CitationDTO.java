package com.carlos.DocChat.AI.dto.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;

import java.util.UUID;

public record CitationDTO(String title, String source, Integer page, double score, String excerpt,
                          String evidenceStatus) {

    public static CitationDTO from(Document chunk, String filename, UUID documentId) {
        Object pageValue = chunk.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        Integer page = pageValue instanceof Number number ? number.intValue() : null;
        Object titleValue = chunk.getMetadata().get("title");
        String title = titleValue instanceof String value && !value.isBlank() ? value : filename;

        // A busca fornece candidatos; a resposta do LLM não identifica quais utilizou.
        return new CitationDTO(title, documentId.toString(), page, chunk.getScore(), chunk.getText(),
                "RETRIEVED_UNVERIFIED");
    }
}
