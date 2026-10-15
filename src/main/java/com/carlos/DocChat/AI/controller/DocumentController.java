package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.service.DocumentUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final DocumentUploadService uploadService;

    public DocumentController(DocumentUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file) {

        DocumentDto document = uploadService.upload(file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadResponse(document.id(), document.status()));
    }

    public record UploadResponse(UUID id, DocumentStatus status) {
    }
}
