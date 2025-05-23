package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatExchange;

import java.util.concurrent.CompletionStage;

public class ChainAiChatResponseProcessor implements IAiChatResponseProcessor {
    private final IAiChatResponseProcessor processor;
    private final IAiChatResponseProcessor next;

    public ChainAiChatResponseProcessor(IAiChatResponseProcessor processor, IAiChatResponseProcessor next) {
        this.processor = processor;
        this.next = next;
    }

    @Override
    public CompletionStage<AiChatExchange> processAsync(AiChatExchange message) {
        return processor.processAsync(message).thenCompose(next::processAsync);
    }
}