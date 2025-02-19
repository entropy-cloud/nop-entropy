package io.nop.ai.core.commons.processor;

import io.nop.ai.core.api.messages.AiResultMessage;

@FunctionalInterface
public interface IAiResultMessageChecker {
    boolean isAccepted(AiResultMessage message);
}
