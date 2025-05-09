package io.nop.ai.core.persist;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

public interface IChatResponsePersister {
    default void save(IResource resource, AiChatResponse response) {
        ResourceHelper.writeText(resource, serialize(response));
    }

    default AiChatResponse load(IResource resource) {
        return deserialize(ResourceHelper.readText(resource));
    }

    String serialize(AiChatResponse response);

    AiChatResponse deserialize(String text);
}