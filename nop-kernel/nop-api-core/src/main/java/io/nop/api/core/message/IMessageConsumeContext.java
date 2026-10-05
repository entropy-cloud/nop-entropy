/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IMessageConsumeContext extends IMessageSender {

    default ICancelToken getCancelToken() {
        return null;
    }

    /**
     * 消费会话内的发送（reply）。D11 (WI14) 起，消息消费遵循 at-least-once 基线：
     * commit/rollback 语义由后端的 ack/提交机制承担，本接口不再承诺事务边界。
     */
    CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options);
}