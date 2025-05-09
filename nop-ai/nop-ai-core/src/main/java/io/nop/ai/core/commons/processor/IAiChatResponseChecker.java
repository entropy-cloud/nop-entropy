package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatExchange;

@FunctionalInterface
public interface IAiChatResponseChecker {
    boolean isAccepted(AiChatExchange chatResponse);
}
