package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;

/**
 * @deprecated 使用新API替代
 */
@Deprecated
public interface IAiChatLogger {
    void logRequest(AiChatExchange request);

    void logResponse(AiChatExchange response);

    void logChatExchange(AiChatExchange exchange);
}