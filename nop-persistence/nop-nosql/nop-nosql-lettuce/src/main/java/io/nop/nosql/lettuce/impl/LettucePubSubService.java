/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.service.LifeCycleSupport;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 Redis Pub/Sub 的消息服务实现。
 * <p>
 * 能力边界：Redis Pub/Sub 是无持久化、无消费组语义的 fire-and-forget 投递模式。
 * {@link #subscribe(String, IMessageConsumer, MessageSubscribeOptions)} 的 options
 * （subscribeName/transactional/subscriptionType 等）与
 * {@link #sendAsync(String, Object, MessageSendOptions)} 的 options
 * （delay/sendTimeout 等）在此实现中不生效，传入即被忽略。
 */
public class LettucePubSubService extends LifeCycleSupport implements IMessageService {

    private final LettuceRedisConnectionProvider connectionProvider;

    private volatile StatefulRedisPubSubConnection<String, Object> pubSubConnection;

    private final ConcurrentMap<String, SubscriptionEntry> subscriptions = new ConcurrentHashMap<>();

    public LettucePubSubService(LettuceRedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return connectionProvider.getAsyncCommands().publish(topic, message)
                .thenApply(ignored -> null);
    }

    @Override
    public synchronized IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                                       MessageSubscribeOptions options) {
        StatefulRedisPubSubConnection<String, Object> conn = getOrCreateConnection();
        SubscriptionEntry entry = subscriptions.get(topic);
        if (entry == null) {
            entry = new SubscriptionEntry(topic);
            subscriptions.put(topic, entry);
            entry.addListener(listener);
            try {
                conn.sync().subscribe(topic);
            } catch (RuntimeException e) {
                // roll back the stale entry, otherwise later subscribes would only add a listener
                // without ever sending SUBSCRIBE to Redis again
                entry.removeListener(listener);
                subscriptions.remove(topic, entry);
                throw e;
            }
        } else {
            entry.addListener(listener);
        }
        return new PubSubSubscription(topic, entry, listener);
    }

    private StatefulRedisPubSubConnection<String, Object> getOrCreateConnection() {
        if (pubSubConnection == null) {
            synchronized (this) {
                if (pubSubConnection == null) {
                    pubSubConnection = connectionProvider.createPubSubConnection();
                }
            }
        }
        return pubSubConnection;
    }

    @Override
    protected void doStart() {
    }

    @Override
    protected void doStop() {
        StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
        if (conn != null) {
            conn.close();
        }
    }

    private class SubscriptionEntry extends RedisPubSubAdapter<String, Object> {
        private final String topic;
        private final ConcurrentMap<IMessageConsumer, Boolean> listeners = new ConcurrentHashMap<>();

        SubscriptionEntry(String topic) {
            this.topic = topic;
        }

        void addListener(IMessageConsumer consumer) {
            listeners.put(consumer, Boolean.TRUE);
            if (listeners.size() == 1) {
                getOrCreateConnection().addListener(this);
            }
        }

        void removeListener(IMessageConsumer consumer) {
            listeners.remove(consumer);
            if (listeners.isEmpty()) {
                getOrCreateConnection().removeListener(this);
            }
        }

        boolean isEmpty() {
            return listeners.isEmpty();
        }

        @Override
        public void message(String channel, Object message) {
            // one shared connection carries all subscriptions; only deliver to this entry's own topic
            if (!topic.equals(channel))
                return;

            IMessageConsumeContext context = new PubSubConsumeContext();
            for (IMessageConsumer consumer : listeners.keySet()) {
                try {
                    consumer.onMessage(channel, message, context);
                } catch (Exception e) {
                    consumer.onException(e instanceof Exception ? (Exception) e : new RuntimeException(e));
                }
            }
        }
    }

    private class PubSubConsumeContext implements IMessageConsumeContext {
        @Override
        public ICancelToken getCancelToken() {
            return null;
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return LettucePubSubService.this.sendAsync(topic, message, options);
        }
    }

    private class PubSubSubscription implements IMessageSubscription {
        private final String topic;
        private final SubscriptionEntry entry;
        private final IMessageConsumer listener;
        private volatile boolean suspended;
        private volatile boolean cancelled;

        PubSubSubscription(String topic, SubscriptionEntry entry, IMessageConsumer listener) {
            this.topic = topic;
            this.entry = entry;
            this.listener = listener;
        }

        @Override
        public void cancel() {
            cancelled = true;
            entry.removeListener(listener);
            if (entry.isEmpty()) {
                StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
                if (conn != null) {
                    conn.sync().unsubscribe(topic);
                }
                subscriptions.remove(topic, entry);
            }
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void suspend() {
            if (!suspended) {
                suspended = true;
                entry.removeListener(listener);
            }
        }

        @Override
        public void resume() {
            if (suspended && !cancelled) {
                suspended = false;
                entry.addListener(listener);
            }
        }
    }
}
