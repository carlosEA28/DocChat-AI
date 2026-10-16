package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentResponse;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentProcessingException;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.util.helpers.PdfValidationHelper;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public DocumentService(DocumentRepository documentRepository, S3Config s3Config, VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.documentRepository = documentRepository;
        this.s3Config = s3Config;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
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

    public List<Document> ingestDocument(UUID documentId) {
        DocumentEntity Userdocument = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        DocumentStatus previousStatus = Userdocument.getStatus();

        String s3Key = Userdocument.getS3Key();

        if (Userdocument.getStatus() == DocumentStatus.READY) {
            throw new DocumentProcessingException(
                    "Document already ingested and ready for search",
                    null
            );
        }

        if (Userdocument.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingException(
                    "Document is already being processed", null
            );
        }
        try {

            // Atualiza o status do documento para PROCESSING
            Userdocument.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(Userdocument);

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

            // Associa os chunks ao documento do seu banco
            documents.forEach(document -> {
                document.getMetadata().put("documentId", documentId.toString());
            });

            // Configura o splitter
            DocumentTransformer splitter = TokenTextSplitter.builder()
                    .withChunkSize(500)
                    .withMinChunkSizeChars(200)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(1000)
                    .withKeepSeparator(true)
                    .build();

            // Divide todas as páginas em chunks
            List<Document> chunks = splitter.apply(documents);

            if(chunks.isEmpty()){
                throw new DocumentStorageException("No chunks were generated from the document", null);
            }

            if (previousStatus == DocumentStatus.FAILED) {
                vectorStore.delete("documentId == '" + documentId + "'");
            }

            // Adiciona os chunks ao pgvectorstore para indexação e busca semântica e atualiza o status do documento para READY
            vectorStore.add(chunks);
            Userdocument.setStatus(DocumentStatus.READY);
            documentRepository.save(Userdocument);

            return chunks;



        } catch (Exception e) {
            Userdocument.setStatus(DocumentStatus.FAILED);
            documentRepository.save(Userdocument);

            throw new DocumentProcessingException(
                    "Failed to ingest document: " + documentId, e
            );
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
