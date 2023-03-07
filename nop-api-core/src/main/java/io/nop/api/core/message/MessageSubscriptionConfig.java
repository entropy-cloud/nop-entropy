/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

public class MessageSubscriptionConfig {
    private String topic;
    private IMessageConsumer consumer;
    private MessageSubscribeOptions options;

    public MessageSubscriptionConfig() {

    }

    public MessageSubscriptionConfig(String topic, IMessageConsumer consumer,
                                     MessageSubscribeOptions options) {
        this.topic = topic;
        this.consumer = consumer;
        this.options = options;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public IMessageConsumer getConsumer() {
        return consumer;
    }

    public void setConsumer(IMessageConsumer consumer) {
        this.consumer = consumer;
    }

    public MessageSubscribeOptions getOptions() {
        return options;
    }

    public void setOptions(MessageSubscribeOptions options) {
        this.options = options;
    }
}