package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiChatExchange;

/**
 * Predicate used to validate a legacy chat response, e.g. to decide whether it passes
 * a content or format check before being accepted.
 */
@FunctionalInterface
public interface IAiChatResponseChecker {
    /**
     * @param chatResponse the legacy chat response to check
     * @return true if the response is accepted
     */
    boolean isAccepted(AiChatExchange chatResponse);
}
