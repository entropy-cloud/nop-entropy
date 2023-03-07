/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

import io.nop.api.core.util.Guard;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public class DelegateMessageSender implements IMessageSender {
    private final IMessageSender sender;

    public DelegateMessageSender(IMessageSender sender) {
        this.sender = Guard.notNull(sender, "sender");
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return sender.sendAsync(topic, message, options);
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        return sender.sendMultiAsync(messages, options);
    }

    @Override
    public void send(String topic, Object message, MessageSendOptions options) {
        sender.send(topic, message, options);
    }
}
