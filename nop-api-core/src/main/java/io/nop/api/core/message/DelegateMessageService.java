/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

import io.nop.api.core.util.Guard;

public class DelegateMessageService extends DelegateMessageSender implements IMessageService {
    private final IMessageSubscriber subscriber;

    public DelegateMessageService(IMessageSender sender, IMessageSubscriber subscriber) {
        super(sender);
        this.subscriber = Guard.notNull(subscriber, "subscriber");
    }

    @Override
    public String getReplyTopic(String topic) {
        return subscriber.getReplyTopic(topic);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return subscriber.subscribe(topic, listener, options);
    }
}