package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * @deprecated This internal AI core interface is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@Deprecated
public interface IAiChatService {
    IAiChatSession newSession(AiChatOptions options);

    IAiChatSession getSession(String sessionId);

    CompletionStage<AiChatExchange> sendChatAsync(Prompt prompt, AiChatOptions options, ICancelToken cancelToken);

    default AiChatExchange sendChat(Prompt prompt, AiChatOptions options, ICancelToken cancelToken) {
        return FutureHelper.syncGet(sendChatAsync(prompt, options, cancelToken));
    }
}