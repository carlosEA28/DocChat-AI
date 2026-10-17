package com.carlos.DocChat.AI.dto.document;

public record CitationDTO(String title, String source, double score, String excerpt) {

    // Método estático para criar um CitationDTO a partir de um Document
    public static CitationDTO from(org.springframework.ai.document.Document document){
        // Extrai os metadados do documento
        String text = document.getText();


        // Cria um resumo do texto, limitando a 400 caracteres e adicionando "..." se for maior
        String excerpt = text.length() > 400 ? text.substring(0, 400) + "..." : text;

        // Cria e retorna um novo CitationDTO com os metadados e o resumo
        return new CitationDTO((String) document.getMetadata().getOrDefault("title", "Unknown"),
                (String) document.getMetadata().getOrDefault("source", "Unknown"),
                document.getScore(),
                excerpt);
    }
}
