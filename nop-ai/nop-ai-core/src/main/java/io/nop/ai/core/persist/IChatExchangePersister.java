package io.nop.ai.core.persist;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

public interface IChatExchangePersister {
    default void save(IResource resource, AiChatExchange exchange) {
        ResourceHelper.writeText(resource, serialize(exchange));
    }

    default AiChatExchange load(IResource resource) {
        return deserialize(ResourceHelper.readText(resource));
    }

    String serialize(AiChatExchange exchange);

    AiChatExchange deserialize(String text);
}