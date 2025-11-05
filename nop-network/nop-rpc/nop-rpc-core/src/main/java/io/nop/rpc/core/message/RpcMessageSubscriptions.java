/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.message;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.lang.impl.Cancellable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.rpc.core.RpcErrors.ARG_TOPIC;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_NOT_ALLOW_MULTIPLE_SUBSCRIPTION;

public class RpcMessageSubscriptions {
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public class Subscription extends Cancellable implements IMessageSubscription {
        private volatile boolean suspended;
        private final String topic;
        private final IMessageConsumer consumer;
        private final MessageSubscribeOptions options;

        public Subscription(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
            this.topic = topic;
            this.consumer = consumer;
            this.options = options;
            this.appendOnCancel(this::doCancel);
        }

        public MessageSubscribeOptions getOptions() {
            return options;
        }

        public String getTopic() {
            return topic;
        }

        public IMessageConsumer getConsumer() {
            return consumer;
        }

        void doCancel(String reason) {
            subscriptions.remove(topic, this);
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public void suspend() {
            suspended = true;
        }

        @Override
        public void resume() {
            suspended = false;
        }

        @SuppressWarnings("PMD.UselessOverridingMethod")
        @Override
        public void cancel() {
            super.cancel();
        }
    }

    public Subscription getSubscription(String topic) {
        return subscriptions.get(topic);
    }

    public Subscription register(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
        Subscription subscription = new Subscription(topic, consumer, options);
        if (subscriptions.putIfAbsent(topic, subscription) != null)
            throw new NopException(ERR_RPC_NOT_ALLOW_MULTIPLE_SUBSCRIPTION).param(ARG_TOPIC, topic);
        return subscription;
    }
}