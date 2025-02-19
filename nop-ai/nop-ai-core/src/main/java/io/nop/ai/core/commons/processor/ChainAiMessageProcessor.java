package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiResultMessage;

import java.util.concurrent.CompletionStage;

public class ChainAiMessageProcessor implements IAiResultMessageProcessor {
    private final IAiResultMessageProcessor processor;
    private final IAiResultMessageProcessor next;

    public ChainAiMessageProcessor(IAiResultMessageProcessor processor, IAiResultMessageProcessor next) {
        this.processor = processor;
        this.next = next;
    }

    @Override
    public CompletionStage<AiResultMessage> processAsync(AiResultMessage message) {
        return processor.processAsync(message).thenCompose(next::processAsync);
    }
}