/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import io.nop.ai.core.api.messages.AiChatExchange;

/**
 * @deprecated Use {@link io.nop.ai.api.chat.IChatService#callStream(io.nop.ai.api.chat.ChatRequest, io.nop.api.core.util.ICancelToken)} instead (reactive streaming of {@link io.nop.ai.api.chat.stream.ChatStreamChunk}).
 */
@Deprecated(forRemoval = true)
public interface IAiChatProgressListener {
    void onReceiveMessage(AiChatExchange message);
}
