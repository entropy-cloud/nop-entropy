package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

public interface IChatExchangePersister {
    default void save(IResource resource, ChatExchange exchange) {
        ResourceHelper.writeText(resource, serialize(exchange));
    }

    default ChatExchange load(IResource resource) {
        return deserialize(ResourceHelper.readText(resource));
    }

    String serialize(ChatExchange exchange);

    ChatExchange deserialize(String text);

    /**
     * Stable hash of the request payload used as the cache key. Mirrors the
     * legacy semantics: chat options do not participate in the hash, so the
     * same prompt under different models can be compared.
     */
    String calcRequestHash(ChatRequest request);
}
