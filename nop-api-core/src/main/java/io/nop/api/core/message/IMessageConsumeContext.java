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
    /**
     * 消息消费者有可能向发送者返回多个消息
     *
     * @param message 返回消息
     */
    void reply(Object message);

    default ICancelToken getCancelToken() {
        return null;
    }

    /**
     * 如果Subscribe时指定了使用事务模式，则发送和接收处在一个事务当中
     */
    CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options);
}