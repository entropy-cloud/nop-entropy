package io.nop.message.core.local;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(LocalMessageService.class);
    private final Map<String, List<Subscription>> consumers = new ConcurrentHashMap<>();

    public void clear() {
        consumers.clear();
    }

    class Subscription implements IMessageSubscription {
        private final String topic;
        private final IMessageConsumer consumer;
        private volatile boolean suspended = false;
        private volatile boolean cancelled = false;

        public Subscription(String topic, IMessageConsumer consumer) {
            this.topic = topic;
            this.consumer = consumer;
        }

        @Override
        public void cancel() {
            cancelled = true;
            removeConsumer(topic, consumer);
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
            suspended = true;
        }

        @Override
        public void resume() {
            suspended = false;
        }
    }

    class ConsumeContext implements IMessageConsumeContext {
        private final String topic;

        public ConsumeContext(String topic) {
            this.topic = topic;
        }

        @Override
        public void reply(Object message) {
            send(getReplyTopic(topic), message);
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return null;
        }

        @Override
        public void send(String topic, Object message, MessageSendOptions options) {
            LocalMessageService.this.send(topic, message, options);
        }

        @Override
        public void send(String topic, Object message) {
            LocalMessageService.this.send(topic, message);
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message) {
            return LocalMessageService.this.sendAsync(topic, message);
        }
    }

    private void removeConsumer(String topic, IMessageConsumer consumer) {
        List<Subscription> subscriptions = consumers.get(topic);
        if (subscriptions != null) {
            Subscription subscription = findSubscription(subscriptions, consumer);
            if (subscription != null) {
                subscriptions.remove(subscription);
            }
        }
    }

    private Subscription findSubscription(List<Subscription> subscriptions, IMessageConsumer consumer) {
        for (Subscription subscription : subscriptions) {
            if (subscription.consumer == consumer)
                return subscription;
        }
        return null;
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        Guard.notEmpty(topic, "topic");
        Guard.notNull(listener, "listener");

        List<Subscription> subscriptions = consumers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        synchronized (subscriptions) {
            Subscription subscription = findSubscription(subscriptions, listener);
            if (subscription == null) {
                subscription = new Subscription(topic, listener);
                subscriptions.add(subscription);
            }
            return subscription;
        }
    }

    @Override
    public void send(String topic, Object message, MessageSendOptions options) {
        List<Subscription> subscriptions = consumers.get(topic);
        if (subscriptions != null) {
            IMessageConsumeContext context = new ConsumeContext(topic);
            for (Subscription subscription : subscriptions) {
                if (subscription.suspended)
                    continue;
                IMessageConsumer consumer = subscription.consumer;
                consumer.onMessage(topic, message, context);
            }
        } else {
            LOG.debug("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return FutureHelper.futureRun(() -> send(topic, message, options));
    }
}
