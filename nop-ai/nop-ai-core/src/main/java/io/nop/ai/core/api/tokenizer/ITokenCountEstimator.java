package io.nop.ai.core.api.tokenizer;

import io.nop.ai.core.api.messages.AiMessage;

public interface ITokenCountEstimator {
    int estimate(String text);

    int estimateForMessage(AiMessage message);
}
