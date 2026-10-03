package io.nop.ai.agent.engine;

import io.nop.ai.api.secure.SecureDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@SecureDefault
public class DefaultAgentEventPublisher implements IAgentEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEventPublisher.class);

    /**
     * Plan 2291 (WI10): default auto-removal threshold for
     * {@link SubscriberFailurePolicy#REMOVE_AFTER_THRESHOLD}.
     */
    public static final int DEFAULT_FAILURE_REMOVAL_THRESHOLD = 10;

    private final List<IAgentEventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    /** Plan 2291 (WI10): per-subscriber cumulative failure counters (identity-keyed). */
    private final Map<IAgentEventSubscriber, AtomicLong> failureCounts = new ConcurrentHashMap<>();

    /** Plan 2291 (WI10): subscriber failure handling policy (default LOG_ONLY). */
    private volatile SubscriberFailurePolicy failurePolicy = SubscriberFailurePolicy.LOG_ONLY;

    /** Plan 2291 (WI10): removal threshold for REMOVE_AFTER_THRESHOLD policy. */
    private volatile int failureRemovalThreshold = DEFAULT_FAILURE_REMOVAL_THRESHOLD;

    @Override
    public void publish(AgentEvent event) {
        for (IAgentEventSubscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (Exception e) {
                long failures = failureCounts
                        .computeIfAbsent(subscriber, k -> new AtomicLong())
                        .incrementAndGet();
                LOG.error("Subscriber {} threw exception while handling event {} (cumulative failures={})",
                        subscriber.getClass().getName(), event.getEventType(), failures, e);
                if (failurePolicy == SubscriberFailurePolicy.REMOVE_AFTER_THRESHOLD
                        && failures >= failureRemovalThreshold) {
                    // Plan 2291 (WI10): a persistently failing subscriber is
                    // auto-removed so it cannot degrade every fan-out; the
                    // failure counter is retained for introspection.
                    subscribers.remove(subscriber);
                    LOG.warn("Subscriber {} removed after {} cumulative failures (policy={})",
                            subscriber.getClass().getName(), failures, failurePolicy);
                }
            }
        }
    }

    @Override
    public void addSubscriber(IAgentEventSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void removeSubscriber(IAgentEventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Plan 2291 (WI10): cumulative failure count for a subscriber (0 when it
     * never failed).
     */
    public long getSubscriberFailureCount(IAgentEventSubscriber subscriber) {
        AtomicLong count = failureCounts.get(subscriber);
        return count != null ? count.get() : 0L;
    }

    public SubscriberFailurePolicy getSubscriberFailurePolicy() {
        return failurePolicy;
    }

    /** Plan 2291 (WI10): sets the failure policy (null → LOG_ONLY). */
    public void setSubscriberFailurePolicy(SubscriberFailurePolicy policy) {
        this.failurePolicy = policy != null ? policy : SubscriberFailurePolicy.LOG_ONLY;
    }

    public int getFailureRemovalThreshold() {
        return failureRemovalThreshold;
    }

    public void setFailureRemovalThreshold(int threshold) {
        this.failureRemovalThreshold = threshold;
    }
}
