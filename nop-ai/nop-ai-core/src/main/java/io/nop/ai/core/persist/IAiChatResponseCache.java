package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.prompt.ChatExchange;

public interface IAiChatResponseCache {

    ChatExchange loadCachedResponse(ChatRequest request, ChatOptions options, String promptName);

    void saveCachedResponse(ChatExchange exchange, String promptName);
}
