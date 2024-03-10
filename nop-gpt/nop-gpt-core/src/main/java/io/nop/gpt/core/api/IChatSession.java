/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gpt.core.api;

import java.util.concurrent.CompletionStage;

public interface IChatSession extends AutoCloseable {
    String getSessionId();

    CompletionStage<String> sendChatAsync(String role, String question, ChatOptions options);
}