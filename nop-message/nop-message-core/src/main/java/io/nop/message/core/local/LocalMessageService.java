package io.nop.message.core.local;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    public void invokeMessageListener(String topic, Object message, MessageSendOptions options) {
        List<Subscription> subscriptions = consumers.get(topic);
        if (subscriptions != null) {
            ConsumeContext context = new ConsumeContext(topic);
            for (Subscription subscription : subscriptions) {
                if (subscription.suspended)
                    continue;
                invokeSubscription(subscription, message);
            }
        } else {
            LOG.debug("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
        }
    }

    private void invokeSubscription(Subscription subscription, Object message) {
        ConsumeContext context = new ConsumeContext(subscription.topic);
        try {
            Object ret = FutureHelper.getResult(
                    subscription.consumer.onMessage(subscription.topic, message, context));
            if (ret instanceof ConsumeLater) {
                scheduleRedelivery(subscription, message, ((ConsumeLater) ret).getDelay());
            } else {
                handleMessageResult(ret, subscription.topic, message, context);
            }
        } catch (Exception e) {
            LOG.error("nop.message.consumer-failed:topic={},message={}", subscription.topic, message, e);
        }
    }

    /**
     * Schedules a delayed redelivery of the message to the subscription that returned
     * {@link ConsumeLater} (F-14 minimal fix). Adjudicated semantics (WI4 plan):
     *
     * <p><strong>Per-subscription scope</strong> — only the subscriber that deferred is
     * re-invoked; a topic-wide redelivery would re-send to already-acknowledged
     * subscribers. WI23 must keep this semantics when aligning F-15.
     *
     * <p><strong>Fire-time state re-check</strong> — when the scheduled task fires, the
     * subscription's cancelled/suspended flags and its presence in the consumers map are
     * re-checked; a cancelled subscription or a cleared service abandons the pending
     * redelivery. Abandoning on cancellation is contract behavior, not a swallowed error.
     */
    private void scheduleRedelivery(Subscription subscription, Object message, long delayMs) {
        long delay = Math.max(delayMs, 0L);
        redeliveryScheduler().schedule(() -> {
            if (subscription.cancelled || subscription.suspended)
                return;
            List<Subscription> subscriptions = consumers.get(subscription.topic);
            if (subscriptions == null || findSubscription(subscriptions, subscription.consumer) == null)
                return;
            invokeSubscription(subscription, message);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private volatile ScheduledExecutorService redeliveryExecutor;

    private ScheduledExecutorService redeliveryScheduler() {
        ScheduledExecutorService executor = redeliveryExecutor;
        if (executor == null) {
            synchronized (this) {
                if (redeliveryExecutor == null) {
                    redeliveryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "nop-local-message-redelivery");
                        t.setDaemon(true);
                        return t;
                    });
                }
                executor = redeliveryExecutor;
            }
        }
        return executor;
    }

    /**
     * Dispatches a processed result. {@link ConsumeLater} must never reach this method:
     * it is intercepted upstream by {@link #invokeSubscription} and turned into a delayed
     * per-subscription redelivery (it is not a reply — F-14).
     */
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
