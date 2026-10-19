package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.ConversationAnswerDto;
import com.carlos.DocChat.AI.dto.document.MessageResponse;
import com.carlos.DocChat.AI.exceptions.http.DocumentExceptionHandler;
import com.carlos.DocChat.AI.exceptions.service.ConversationNotFoundException;
import com.carlos.DocChat.AI.model.MessageRole;
import com.carlos.DocChat.AI.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {
    private final ConversationService service = mock(ConversationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ConversationController(service))
                .setControllerAdvice(new DocumentExceptionHandler()).build();
    }

    @Test
    void continuesConversationAndReturnsItsId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.continueConversation(id, "Próxima pergunta"))
                .thenReturn(new ConversationAnswerDto(id, "Próxima pergunta", "Resposta", List.of()));

        mvc.perform(post("/conversations/{id}/questions", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  Próxima pergunta  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(id.toString()))
                .andExpect(jsonPath("$.answer").value("Resposta"));
        verify(service).continueConversation(id, "Próxima pergunta");
    }

    @Test
    void listsMessagesInServiceOrder() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:00:00Z");
        when(service.messages(id)).thenReturn(List.of(
                new MessageResponse(UUID.randomUUID(), MessageRole.USER, "Pergunta", now, 1),
                new MessageResponse(UUID.randomUUID(), MessageRole.ASSISTANT, "Resposta", now, 2)));

        mvc.perform(get("/conversations/{id}/messages", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[1].content").value("Resposta"));
    }

    @Test
    void missingConversationReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.messages(id)).thenThrow(new ConversationNotFoundException(id));
        mvc.perform(get("/conversations/{id}/messages", id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidQuestionNeverReachesService() throws Exception {
        mvc.perform(post("/conversations/{id}/questions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
