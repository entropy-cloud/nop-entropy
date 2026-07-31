package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * Legacy chat service interface of the {@code IAiChat*} pipeline.
 * <p>
 * <b>Deprecation semantics (P2-MA3-04 ruling):</b> this interface is still the
 * active backbone of the legacy chat path in nop-ai-core: implemented by
 * {@code DefaultAiChatService} and consumed by {@code AiCommand} and the task
 * engine ({@code ai:TaskStep}). The {@code @Deprecated(forRemoval = true)} marker
 * is retained because the new AI API ({@code IChatService} / bean {@code nopChatService}
 * in nop-ai-api, shaped {@code callAsync(ChatRequest, ICancelToken)}) supersedes it.
 * Full migration of the legacy pipeline to the new API is future major-version work;
 * <b>do not remove this interface while legacy callers remain</b>.
 */
@Deprecated(forRemoval = true)
public interface IAiChatService {
    IAiChatSession newSession(AiChatOptions options);

    IAiChatSession getSession(String sessionId);

    CompletionStage<AiChatExchange> sendChatAsync(Prompt prompt, AiChatOptions options, ICancelToken cancelToken);

    default AiChatExchange sendChat(Prompt prompt, AiChatOptions options, ICancelToken cancelToken) {
        return FutureHelper.syncGet(sendChatAsync(prompt, options, cancelToken));
    }
}