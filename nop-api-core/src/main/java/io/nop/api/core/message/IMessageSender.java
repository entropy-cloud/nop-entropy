/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import io.nop.api.core.util.FutureHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IMessageSender {

    CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options);

    /**
     * 发送消息到指定topic
     *
     * @param message 待发送的消息
     */
    default void send(String topic, Object message, MessageSendOptions options) {
        FutureHelper.syncGet(sendAsync(topic, message, options));
    }


    /**
     * 阻塞等待发送成功，发送失败则抛出异常
     *
     * @param topic   目标topic
     * @param message 待发送的消息，不允许为空
     */
    default void send(String topic, Object message) {
        send(topic, message, null);
    }

    default CompletionStage<Void> sendAsync(String topic, Object message) {
        return sendAsync(topic, message, null);
    }

    default void sendMulti(Collection<TopicMessage> messages, MessageSendOptions options) {
        FutureHelper.syncGet(sendMultiAsync(messages, options));
    }

    default CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        List<CompletionStage> futures = new ArrayList<>(messages.size());
        for (TopicMessage message : messages) {
            futures.add(sendAsync(message.getTopic(), message.getMessage(), options));
        }
        return FutureHelper.waitAll(futures);
    }
}