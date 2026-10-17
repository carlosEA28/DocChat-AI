package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentResponse;
import com.carlos.DocChat.AI.dto.document.QuestionRequest;
import com.carlos.DocChat.AI.dto.document.RagAnswerDto;
import com.carlos.DocChat.AI.exceptions.service.InvalidQuestionException;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file) {

        DocumentDto document = documentService.upload(file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadResponse(document.id(), document.status()));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Void> ingestDocument(@PathVariable UUID id) {
        documentService.ingestDocument(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{id}/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RagAnswerDto askQuestion(@PathVariable UUID id, @RequestBody QuestionRequest request) {
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

        return documentService.searchSimilarChunks(id, question);
    }

    public record UploadResponse(UUID id, DocumentStatus status) {
    }
}
