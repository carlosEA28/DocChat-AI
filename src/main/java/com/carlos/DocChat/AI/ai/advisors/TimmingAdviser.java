package com.carlos.DocChat.AI.ai.advisors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

public class TimmingAdviser implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(TimmingAdviser.class);
    private final int order;

    public TimmingAdviser(int order) {
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {

        long start = System.currentTimeMillis();
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        logger.info("[Timmign Advisor] - Time taken for API call: " + (System.currentTimeMillis() - start) + " ms");

        return response;
    }

    @Override
    public String getName() {
        return "TimmingAdviser";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        long start = System.currentTimeMillis();

        return streamAdvisorChain.nextStream(chatClientRequest)
                .doOnComplete(() -> logger.info("[Timmign Advisor] - Time taken for API stream: " + (System.currentTimeMillis() - start) + " ms"));
    }
}
