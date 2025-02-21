package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatResponse;

@FunctionalInterface
public interface IAiChatResponseChecker {
    boolean isAccepted(AiChatResponse chatResponse);
}
