package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.config.aws.S3Config;
import com.carlos.DocChat.AI.service.DocumentService;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.vectorstore.pgvector.enabled=false",
        "spring.ai.google.genai.api-key=test-key",
        "app.aws.s3.bucket=test-bucket",
        "app.aws.s3.region=us-east-1",
        "app.aws.s3.profile=test"
})
@AutoConfigureMockMvc
class ControllerFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DocumentService documentService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean S3Config s3;
    @MockitoBean VectorStore vectorStore;
    @MockitoBean ChatClient.Builder chatBuilder;

    private byte[] pdf;
    private final List<UUID> createdDocuments = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : createdDocuments) {
            jdbc.update("delete from documents where id = ?", id);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        pdf = pdf();
        doAnswer(call -> pdf).when(s3).downloadPdf(anyString());
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        ReflectionTestUtils.setField(documentService, "chatClient", client);
        when(client.prompt(any(Prompt.class))).thenReturn(request);
        when(request.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("Java e PostgreSQL");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().text("O projeto usa Java e PostgreSQL.")
                        .metadata("page_number", 1).score(0.9).build()));
    }

    @Test
    void documentConversationAndDeletionFlow() throws Exception {
        UUID documentId = upload();
        mvc.perform(get("/documents/{id}", documentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/documents"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + documentId + "')]").isNotEmpty());
        mvc.perform(post("/documents/{id}/questions", documentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"O que usa?\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/documents/{id}/process", documentId)).andExpect(status().isOk());
        mvc.perform(get("/documents/{id}", documentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        verify(vectorStore).add(any());

        String answer = mvc.perform(post("/documents/{id}/questions", documentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"O que usa?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Java e PostgreSQL"))
                .andExpect(jsonPath("$.citations[0].page").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(JsonPath.read(answer, "$.conversationId"));
        mvc.perform(get("/documents/{id}/conversations", documentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(conversationId.toString()));

        mvc.perform(post("/conversations/{id}/questions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"E o banco?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(conversationId.toString()));
        mvc.perform(get("/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[3].role").value("ASSISTANT"));

        mvc.perform(delete("/documents/{id}", documentId)).andExpect(status().isNoContent());
        mvc.perform(get("/documents/{id}", documentId)).andExpect(status().isNotFound());
        mvc.perform(get("/conversations/{id}/messages", conversationId)).andExpect(status().isNotFound());
        verify(s3).deletePdf("uploads/" + documentId + ".pdf");
    }

    @Test
    void deletingConversationDeletesItsDocument() throws Exception {
        UUID documentId = upload();
        mvc.perform(post("/documents/{id}/process", documentId)).andExpect(status().isOk());
        String answer = mvc.perform(post("/documents/{id}/questions", documentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"O que usa?\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(JsonPath.read(answer, "$.conversationId"));

        mvc.perform(delete("/conversations/{id}", conversationId)).andExpect(status().isNoContent());
        mvc.perform(get("/documents/{id}", documentId)).andExpect(status().isNotFound());
    }

    @Test
    void invalidAndMissingResourceRequests() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/documents/{id}", missing)).andExpect(status().isNotFound());
        mvc.perform(post("/documents/{id}/process", missing)).andExpect(status().isNotFound());
        mvc.perform(get("/documents/{id}/conversations", missing)).andExpect(status().isNotFound());
        mvc.perform(get("/conversations/{id}/messages", missing)).andExpect(status().isNotFound());
        mvc.perform(delete("/conversations/{id}", missing)).andExpect(status().isNotFound());
        mvc.perform(get("/documents/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/documents/{id}/questions", missing)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/documents/upload").file(new MockMultipartFile(
                        "file", "invalid.txt", "text/plain", "invalid".getBytes())))
                .andExpect(status().isBadRequest());
    }

    private UUID upload() throws Exception {
        var file = new MockMultipartFile("file", "flow.pdf", "application/pdf", pdf);
        String body = mvc.perform(multipart("/documents/upload").file(file))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdDocuments.add(id);
        verify(s3).uploadPdf("uploads/" + id + ".pdf", pdf, "application/pdf");
        return id;
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("O projeto usa Java e PostgreSQL para responder perguntas sobre documentos.");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
