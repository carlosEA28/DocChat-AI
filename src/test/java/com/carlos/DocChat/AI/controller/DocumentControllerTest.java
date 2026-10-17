package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.DocumentDto;
import com.carlos.DocChat.AI.dto.document.DocumentResponse;
import com.carlos.DocChat.AI.dto.document.CitationDTO;
import com.carlos.DocChat.AI.dto.document.RagAnswerDto;
import com.carlos.DocChat.AI.exceptions.http.DocumentExceptionHandler;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.model.DocumentStatus;
import com.carlos.DocChat.AI.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {
    private final DocumentService documentService = mock(DocumentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentService))
                .setControllerAdvice(new DocumentExceptionHandler())
                .build();
    }

    @Test
    void listsDocumentsWithoutStorageKey() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.list()).thenReturn(List.of(response(id)));

        mvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].filename").value("arquivo.pdf"))
                .andExpect(jsonPath("$[0].size").value(123))
                .andExpect(jsonPath("$[0].uploadedAt").value("2026-10-15T10:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].s3Key").doesNotExist());
    }

    @Test
    void getsDocumentAndReturnsNotFoundForUnknownId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        when(documentService.get(id)).thenReturn(response(id));
        when(documentService.get(unknownId)).thenThrow(new DocumentNotFoundException(unknownId));

        mvc.perform(get("/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-15T11:00:00Z"));
        mvc.perform(get("/documents/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void uploadStillReturnsCreatedDocumentIdAndStatus() throws Exception {
        UUID id = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "arquivo.pdf", "application/pdf", new byte[]{1});
        when(documentService.upload(file)).thenReturn(
                new DocumentDto(id, "arquivo.pdf", "application/pdf", 1, "uploads/arquivo.pdf", DocumentStatus.PENDING));

        mvc.perform(multipart("/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void asksQuestionAndReturnsMatchingChunks() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.searchSimilarChunks(id, "Quais tecnologias?"))
                .thenReturn(new RagAnswerDto("Quais tecnologias?", "Java e PostgreSQL",
                        List.of(new CitationDTO("arquivo.pdf", id.toString(), 1, 0.82,
                                "Trecho relevante", "RETRIEVED_UNVERIFIED"))));

        mvc.perform(post("/documents/{id}/questions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  Quais tecnologias?  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Quais tecnologias?"))
                .andExpect(jsonPath("$.answer").value("Java e PostgreSQL"))
                .andExpect(jsonPath("$.citations[0].excerpt").value("Trecho relevante"))
                .andExpect(jsonPath("$.citations[0].page").value(1))
                .andExpect(jsonPath("$.citations[0].source").value(id.toString()))
                .andExpect(jsonPath("$.citations[0].evidenceStatus").value("RETRIEVED_UNVERIFIED"))
                .andExpect(jsonPath("$.citations[0].score").value(0.82));
        verify(documentService).searchSimilarChunks(id, "Quais tecnologias?");
    }

    @Test
    void returnsEmptyCitationsWhenSearchFindsNothing() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.searchSimilarChunks(id, "Pergunta"))
                .thenReturn(new RagAnswerDto("Pergunta", "Desculpe, não sei a resposta", List.of()));

        mvc.perform(post("/documents/{id}/questions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Pergunta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void rejectsInvalidQuestionsBeforeSearch() throws Exception {
        UUID id = UUID.randomUUID();
        for (String body : List.of("{}", "{\"question\":null}", "{\"question\":\"\"}",
                "{\"question\":\"   \"}", "{\"question\":\"" + "a".repeat(2001) + "\"}")) {
            mvc.perform(post("/documents/{id}/questions", id)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
        mvc.perform(post("/documents/{id}/questions", id).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        verify(documentService, never()).searchSimilarChunks(any(UUID.class), anyString());
    }

    @Test
    void rejectsInvalidDocumentId() throws Exception {
        mvc.perform(post("/documents/not-a-uuid/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Pergunta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        verify(documentService, never()).searchSimilarChunks(any(UUID.class), anyString());
    }

    @Test
    void reportsMissingAndUnreadyDocuments() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        when(documentService.searchSimilarChunks(missing, "Pergunta"))
                .thenThrow(new DocumentNotFoundException(missing));
        when(documentService.searchSimilarChunks(pending, "Pergunta"))
                .thenThrow(new DocumentNotReadyException(DocumentStatus.PENDING));

        for (UUID id : List.of(missing, pending)) {
            mvc.perform(post("/documents/{id}/questions", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"Pergunta\"}"))
                    .andExpect(status().is(id.equals(missing) ? 404 : 409))
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    private DocumentResponse response(UUID id) {
        return new DocumentResponse(id, "arquivo.pdf", 123,
                OffsetDateTime.parse("2026-10-15T10:00:00Z"), DocumentStatus.READY,
                OffsetDateTime.parse("2026-10-15T11:00:00Z"));
    }
}
