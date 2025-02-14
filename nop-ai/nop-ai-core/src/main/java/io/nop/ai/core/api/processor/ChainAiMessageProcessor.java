package io.nop.ai.core.api.processor;

import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;

import java.util.concurrent.CompletionStage;

public class ChainAiMessageProcessor implements IAiResultMessageProcessor {
    private final IAiResultMessageProcessor processor;
    private final IAiResultMessageProcessor next;

    public ChainAiMessageProcessor(IAiResultMessageProcessor processor, IAiResultMessageProcessor next) {
        this.processor = processor;
        this.next = next;
    }

    @Override
    public CompletionStage<AiResultMessage> processAsync(Prompt prompt, AiResultMessage message) {
        return processor.processAsync(prompt, message).thenCompose(m -> next.processAsync(prompt, m));
    }
}