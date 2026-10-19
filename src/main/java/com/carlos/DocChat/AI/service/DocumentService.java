package com.carlos.DocChat.AI.service;

import com.carlos.DocChat.AI.ai.advisors.TimmingAdviser;
import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.dto.document.*;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.exceptions.service.DocumentProcessingException;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.model.DocumentEntity;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.repository.DocumentRepository;
import com.carlos.DocChat.AI.util.helpers.PdfValidationHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final S3Config s3Config;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private static final String REWRITE_INSTRUCTIONS = """
            Rewrite the latest user question as a standalone search query for the same document.
            Resolve references using the conversation history below. Preserve the user's intent.
            Do not answer the question, invent facts, or include explanations. Return only the search query.

            Conversation history:
            %s
            """;
    private static final String ANSWER_INSTRUCTIONS = """
            Answer the latest user question using only the retrieved PDF excerpts below.
            Conversation history may clarify what the user is referring to, but it is not
            evidence about the PDF. If the excerpts do not support an answer, say
            "Sorry, this information is not available in my context."
            Be concise and factual.

            Conversation history (reference context only):
            %s

            Retrieved PDF excerpts:
            ---
            %s
            ---
            """;

    public DocumentService(DocumentRepository documentRepository, S3Config s3Config, VectorStore vectorStore , ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.documentRepository = documentRepository;
        this.s3Config = s3Config;
        this.vectorStore = vectorStore;
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

            // Cada Document representa uma página; o splitter preserva seus metadados.
            documents = documents.stream().map(page -> {
                page.getMetadata().put("documentId", documentId.toString());
                page.getMetadata().put("title", Userdocument.getFilename());
                return page.mutate().text(normalizePdfText(page.getText())).build();
            }).toList();

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

    public RagAnswerDto searchSimilarChunks(UUID documentId, String query) {
        return answer(findReadyDocument(documentId), query, query, "");
    }

    public RagAnswerDto answerWithHistory(UUID documentId, String question, String history) {
        DocumentEntity document = findReadyDocument(documentId);
        String searchQuery = history.isEmpty() ? question : rewriteSearchQuery(question, history);
        return answer(document, question, searchQuery, history);
    }

    private DocumentEntity findReadyDocument(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (document.getStatus() != DocumentStatus.READY) {
            throw new DocumentNotReadyException(document.getStatus());
        }

        return document;
    }

    private String rewriteSearchQuery(String question, String history) {
        try {
            String rewritten = chatClient.prompt(new Prompt(
                    new SystemMessage(REWRITE_INSTRUCTIONS.formatted(history)),
                    new UserMessage(question))).call().content();
            if (rewritten == null || rewritten.isBlank() || rewritten.codePointCount(0, rewritten.length()) > 2000) {
                return question;
            }
            return rewritten.strip();
        } catch (RuntimeException exception) {
            return question;
        }
    }

    private RagAnswerDto answer(DocumentEntity document, String question, String searchQuery,
                                String history) {
        UUID documentId = document.getId();
        List<Document> results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(searchQuery)
                        .topK(5)
                        .similarityThreshold(0.4)
                        .filterExpression("documentId == '" + documentId + "'")
                        .build()
        );

        String context = results.stream()
                .map(d -> "[Document: " +
                        d.getMetadata().get("documentId") + "]\n" +
                        d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));


        String answer = chatClient.prompt(new Prompt(
                        new SystemMessage(ANSWER_INSTRUCTIONS.formatted(history, context)),
                        new UserMessage(question)))
                .advisors(new TimmingAdviser(0))
                .call()
                .content();

        List<CitationDTO> citations = results.stream()
                .map(chunk -> CitationDTO.from(chunk, document.getFilename(), documentId))
                .toList();

        return new RagAnswerDto(question, answer, citations);

    }

    static String normalizePdfText(String text) {
        // Mantém as quebras de linha (inclusive as que separam parágrafos).
        return text.replaceAll("[\\t\\p{Zs}]+", " ")
                .replaceAll("(?m)^ | $", "");
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
