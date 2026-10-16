package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentResponse;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.util.helpers.PdfValidationHelper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    private final S3Config s3Config;

    public DocumentService(DocumentRepository documentRepository, S3Config s3Config) {
        this.documentRepository = documentRepository;
        this.s3Config = s3Config;
    }

    public List<DocumentResponse> list() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DocumentResponse get(UUID id) {
        return documentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
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

    public List<Document> getDocumentText(UUID DocumentId) {
        DocumentEntity Userdocument = documentRepository.findById(DocumentId)
                .orElseThrow(() -> new DocumentNotFoundException(DocumentId));

        String s3Key = Userdocument.getS3Key();
        try {
            byte[] pdfBytes = s3Config.downloadPdf(s3Key);

            ByteArrayResource resource = new ByteArrayResource(pdfBytes);

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                    resource,
                    PdfDocumentReaderConfig.builder()
                            .withPageTopMargin(0)
                            .withPagesPerDocument(1)
                            .build()
            );

            List<Document> documents = pdfReader.get();

            documents.forEach(document -> {
                String text = document.getText();
                Map<String, Object> metadata = document.getMetadata();
                String id = document.getId();

                int pagina = (Integer) metadata.get(
                        PagePdfDocumentReader.METADATA_START_PAGE_NUMBER
                );

                System.out.println("Texto da página: " + text);
                System.out.println("Metadados: " + metadata);
                System.out.println("ID: " + id);
                System.out.println("Página: " + pagina);
            });

            return documents;


        } catch (Exception e) {
            throw new DocumentStorageException("Error downloading document from S3", e);
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

    private DocumentResponse toResponse(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getSize(),
                document.getCreatedAt(),
                document.getStatus(),
                document.getUpdatedAt()
        );
    }
}
