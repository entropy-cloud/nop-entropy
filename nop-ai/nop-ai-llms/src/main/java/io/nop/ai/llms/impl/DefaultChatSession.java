package io.nop.ai.llms.impl;

import io.nop.ai.core.api.chat.IChatService;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public class DefaultChatSession extends AbstractChatSession {
    private final IChatService chatService;

    public DefaultChatSession(IChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public CompletionStage<AiResultMessage> sendChatAsync(Prompt prompt, ICancelToken cancelToken) {
        return chatService.sendChatAsync(prompt, getChatOptions(), cancelToken);
    }
}
