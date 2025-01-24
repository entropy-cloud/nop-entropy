package io.nop.message.core.local;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.message.core.MessageCoreConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(LocalMessageService.class);
    private final Map<String, List<Subscription>> consumers = new ConcurrentHashMap<>();

    public void clearConsumers() {
        consumers.clear();
    }

    public Map<String, List<Subscription>> getConsumers() {
        return consumers;
    }

    public class Subscription implements IMessageSubscription {
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

    public class ConsumeContext implements IMessageConsumeContext {
        private final String topic;

        public ConsumeContext(String topic) {
            this.topic = topic;
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return FutureHelper.futureCall(() -> {
                send(topic, message, options);
                return null;
            });
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

    public Set<String> getBroadcastTopics() {
        Set<String> ret = new TreeSet<>();
        for (String topic : consumers.keySet()) {
            if (topic.startsWith(MessageCoreConstants.TOPIC_PREFIX_BROADCAST)) {
                ret.add(topic);
            }
        }
        return ret;
    }

    public Set<String> getNonBroadcastTopics() {
        Set<String> ret = new TreeSet<>();
        for (String topic : consumers.keySet()) {
            if (!topic.startsWith(MessageCoreConstants.TOPIC_PREFIX_BROADCAST)) {
                ret.add(topic);
            }
        }
        return ret;
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
        this.invokeMessageListener(topic, message, options);
    }

    public ConsumeContext invokeMessageListener(String topic, Object message, MessageSendOptions options) {
        List<Subscription> subscriptions = consumers.get(topic);
        if (subscriptions != null) {
            ConsumeContext context = new ConsumeContext(topic);
            for (Subscription subscription : subscriptions) {
                if (subscription.suspended)
                    continue;
                IMessageConsumer consumer = subscription.consumer;
                Object ret = consumer.onMessage(topic, message, context);
                if (ret instanceof CompletionStage) {
                    ((CompletionStage) ret).whenComplete((r, e) -> {
                        if (e != null) {
                            LOG.error("nop.message.consumer-error:topic={},message={},error={}", topic, message, e);
                        } else {
                            handleMessageResult(ret, topic, message, context);
                        }
                    });
                } else {
                    handleMessageResult(ret, topic, message, context);
                }
            }
            return context;
        } else {
            LOG.debug("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
            return null;
        }
    }

    protected void handleMessageResult(Object ret, String topic, Object message, IMessageConsumeContext context) {
        if (ret instanceof Acknowledge) {
            send(getAckTopic(topic), ((Acknowledge) ret).getReplyMessage());
        } else if (ret != null) {
            send(getAckTopic(topic), ret);
        } else {
            LOG.debug("nop.message.ignore-message-when-no-reply:topic={},message={}", topic, message);
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return FutureHelper.futureRun(() -> send(topic, message, options));
    }
}
