package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public class DefaultAiChatSession extends AbstractAiChatSession {
    private final IAiChatService chatService;

    public DefaultAiChatSession(IAiChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public CompletionStage<AiChatExchange> sendChatAsync(Prompt prompt, ICancelToken cancelToken) {
        return chatService.sendChatAsync(prompt, getChatOptions(), cancelToken);
    }
}
