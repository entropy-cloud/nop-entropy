package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IAiChatService {
    IAiChatSession newSession(AiChatOptions options);

    IAiChatSession getSession(String sessionId);

    CompletionStage<AiChatExchange> sendChatAsync(Prompt prompt, AiChatOptions options, ICancelToken cancelToken);
}