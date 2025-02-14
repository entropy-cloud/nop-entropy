package io.nop.ai.core.api.processor;

import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IAiResultMessageProcessor {

    default AiResultMessage process(Prompt prompt, AiResultMessage message) {
        return FutureHelper.syncGet(processAsync(prompt, message));
    }

    CompletionStage<AiResultMessage> processAsync(Prompt prompt, AiResultMessage message);

    default IAiResultMessageProcessor next(IAiResultMessageProcessor processor) {
        return new ChainAiMessageProcessor(this, processor);
    }

    static IAiResultMessageProcessor buildPipeline(List<IAiResultMessageProcessor> processors) {
        if (processors.isEmpty())
            return null;
        if (processors.size() == 1)
            return processors.get(0);
        if (processors.size() == 2)
            return processors.get(0).next(processors.get(1));
        return processors.get(0).next(buildPipeline(processors.subList(1, processors.size())));
    }
}