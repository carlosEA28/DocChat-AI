package com.carlos.DocChat.AI.controller;

import com.carlos.DocChat.AI.dto.document.ConversationAnswerDto;
import com.carlos.DocChat.AI.dto.document.MessageResponse;
import com.carlos.DocChat.AI.dto.document.QuestionRequest;
import com.carlos.DocChat.AI.service.ConversationService;
import com.carlos.DocChat.AI.service.DocumentDeletionService;
import com.carlos.DocChat.AI.service.QuestionValidation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final DocumentDeletionService documentDeletionService;

    public ConversationController(ConversationService conversationService,
                                  DocumentDeletionService documentDeletionService) {
        this.conversationService = conversationService;
        this.documentDeletionService = documentDeletionService;
    }

    @PostMapping(value = "/{id}/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConversationAnswerDto askQuestion(@PathVariable UUID id, @RequestBody QuestionRequest request) {
        return conversationService.continueConversation(id, QuestionValidation.validate(request));
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@PathVariable UUID id) {
        return conversationService.messages(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentDeletionService.deleteByConversationId(id);
        return ResponseEntity.noContent().build();
    }
}
