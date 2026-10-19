package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentResponse;
import com.carlos.DocChat.AI.dto.document.ConversationAnswerDto;
import com.carlos.DocChat.AI.dto.document.ConversationResponse;
import com.carlos.DocChat.AI.dto.document.QuestionRequest;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.service.ConversationService;
import com.carlos.DocChat.AI.service.DocumentDeletionService;
import com.carlos.DocChat.AI.service.DocumentService;
import com.carlos.DocChat.AI.service.QuestionValidation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final ConversationService conversationService;
    private final DocumentDeletionService documentDeletionService;

    public DocumentController(DocumentService documentService, ConversationService conversationService,
                              DocumentDeletionService documentDeletionService) {
        this.documentService = documentService;
        this.conversationService = conversationService;
        this.documentDeletionService = documentDeletionService;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentDeletionService.deleteByDocumentId(id);
        return ResponseEntity.noContent().build();
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
    public ConversationAnswerDto askQuestion(@PathVariable UUID id, @RequestBody QuestionRequest request) {
        return conversationService.start(id, QuestionValidation.validate(request));
    }

    @GetMapping("/{id}/conversations")
    public List<ConversationResponse> conversations(@PathVariable UUID id) {
        return conversationService.list(id);
    }

    public record UploadResponse(UUID id, DocumentStatus status) {
    }
}
