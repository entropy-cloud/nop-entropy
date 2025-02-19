/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IAiChatSession extends AutoCloseable {
    String getSessionId();

    List<AiMessage> getActiveHistoryMessages();

    void disableMessages(Collection<String> messageIds);

    void addMessage(AiMessage message);

    void addMessages(Collection<AiMessage> messages);

    Prompt newPrompt(boolean includeHistory);

    CompletionStage<AiResultMessage> sendChatAsync(Prompt prompt, ICancelToken cancelToken);

    default AiResultMessage sendChat(Prompt prompt, ICancelToken cancelToken) {
        return FutureHelper.syncGet(sendChatAsync(prompt, cancelToken));
    }
}