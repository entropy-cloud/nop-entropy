package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface IAiChatResponseProcessor {

    default AiChatExchange process(AiChatExchange message) {
        return FutureHelper.syncGet(processAsync(message));
    }

    CompletionStage<AiChatExchange> processAsync(AiChatExchange message);

    default IAiChatResponseProcessor next(IAiChatResponseProcessor processor) {
        return new ChainAiChatResponseProcessor(this, processor);
    }

    static IAiChatResponseProcessor buildPipeline(List<IAiChatResponseProcessor> processors) {
        if (processors.isEmpty())
            return null;
        if (processors.size() == 1)
            return processors.get(0);
        if (processors.size() == 2)
            return processors.get(0).next(processors.get(1));
        return processors.get(0).next(buildPipeline(processors.subList(1, processors.size())));
    }
}