/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
    public String getAckTopic(String topic) {
        return subscriber.getAckTopic(topic);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return subscriber.subscribe(topic, listener, options);
    }
}