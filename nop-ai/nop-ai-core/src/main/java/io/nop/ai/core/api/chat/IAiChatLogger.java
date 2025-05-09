package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;

public interface IAiChatLogger {
    void logRequest(AiChatExchange request);

    void logResponse(AiChatExchange response);
}