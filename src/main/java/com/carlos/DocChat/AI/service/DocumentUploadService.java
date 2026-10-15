package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.dto.document.CreateDocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.util.helpers.PdfValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;

    private final S3Config s3Config;

    public DocumentUploadService(DocumentRepository documentRepository, S3Config s3Config) {
        this.documentRepository = documentRepository;
        this.s3Config = s3Config;
    }

    public DocumentDto upload(MultipartFile file) {

        String key = "uploads/" + file.getOriginalFilename();

        // Valida o tamanho do pdf
        PdfValidationHelper.validateSize(file.getSize());

        // Valida o tipo do pdf
        PdfValidationHelper.validateType(file.getOriginalFilename(), file.getContentType());

        try {
            s3Config.uploadPdf(key, file.getBytes(), file.getContentType());

            DocumentEntity document = new DocumentEntity();
            document.setId(UUID.randomUUID());
            document.setFilename(file.getOriginalFilename());
            document.setContentType(file.getContentType());
            document.setSize(file.getSize());
            document.setS3Key(key);
            document.setStatus(DocumentStatus.PENDING);

            DocumentEntity saved = documentRepository.save(document);
            return toDto(saved);

        } catch (Exception e) {
            if (e instanceof DocumentStorageException) {
                throw (DocumentStorageException) e;
            }
            throw new DocumentStorageException("Error uploading document to S3", e);
        }
    }

    private DocumentDto toDto(DocumentEntity entity) {
        return new DocumentDto(
                entity.getId(),
                entity.getFilename(),
                entity.getContentType(),
                entity.getSize(),
                entity.getS3Key(),
                entity.getStatus()
        );
    }
}
