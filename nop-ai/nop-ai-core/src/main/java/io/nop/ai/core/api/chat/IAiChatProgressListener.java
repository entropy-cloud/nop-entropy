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
 * @deprecated This internal AI core interface is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@Deprecated
public interface IAiChatProgressListener {
    void onReceiveMessage(AiChatExchange message);
}
