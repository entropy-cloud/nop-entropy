/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiMessage;

import java.util.Collection;
import java.util.List;

/**
 * Legacy chat session interface of the {@code IAiChat*} pipeline.
 * <p>
 * <b>Deprecation semantics (P2-MA3-04 ruling):</b> still the active session
 * contract of the legacy chat path in nop-ai-core (base for
 * {@code AbstractAiChatSession} and all session implementations). Retained
 * {@code @Deprecated(forRemoval = true)} because the new AI API
 * ({@code IChatService} / {@code nopChatService} in nop-ai-api) supersedes it;
 * full migration is future major-version work. Do not remove while legacy
 * callers remain.
 */
@Deprecated(forRemoval = true)
public interface IAiChatSession extends AutoCloseable {
    String getSessionId();

    List<AiMessage> getActiveHistoryMessages();

    void disableMessages(Collection<String> messageIds);

    void addMessage(AiMessage message);

    void addMessages(Collection<AiMessage> messages);
}