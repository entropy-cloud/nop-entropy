package io.nop.ai.core.persist;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;

public interface IAiChatResponseCache {

    AiChatExchange loadCachedResponse(Prompt prompt, AiChatOptions options);

    void saveCachedResponse(AiChatExchange exchange);
}