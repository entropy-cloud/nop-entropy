/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.Prompt;

import java.util.concurrent.CompletionStage;

public interface IChatSession extends AutoCloseable {
    String getSessionId();

    CompletionStage<AiMessage> sendChatAsync(Prompt prompt, ChatOptions options);
}