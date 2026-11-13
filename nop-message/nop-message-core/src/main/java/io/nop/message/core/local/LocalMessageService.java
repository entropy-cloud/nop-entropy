package io.nop.message.core.local;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.message.core.MessageCoreConstants;
import io.nop.message.core.MessageMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.message.core.MessageCoreErrors.ERR_LOCAL_CONSUME_AWAIT_NOT_FUTURE;
import static io.nop.message.core.MessageCoreErrors.ERR_LOCAL_CONSUME_AWAIT_TIMEOUT;
import static io.nop.message.core.MessageCoreErrors.ERR_LOCAL_REDELIVERY_PENDING_OVERFLOW;
import static io.nop.message.core.MessageCoreErrors.ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED;

public class LocalMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(LocalMessageService.class);

    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.ACK_REPLY);

    /**
     * In-JVM broadcast bus: options that require unimplemented transports are warn-once
     * (D1 disposition), never silently ignored.
     */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(
            MessageCapability.DELAYED_SEND, MessageCapability.SEND_TIMEOUT,
            MessageCapability.BATCH_CONSUME, MessageCapability.SEEK);

    /**
     * 4.1-MA-01（plan 2558 裁定 A·Local）: default bounded consume wait. Local has no
     * broker-side maxPollIntervalMs / ackTimeout; the default aligns with the Kafka
     * maxPollIntervalMs guidance (5 minutes) per the consumeAwaitTimeout contract
     * (merge-adjudication §五-6). {@code <= 0} restores the legacy unbounded wait
     * (javadoc-declared escape hatch, never a silent default).
     */
    public static final long DEFAULT_CONSUME_AWAIT_TIMEOUT_MS = 300_000L;

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: default per-subscription cap for pending
     * {@link io.nop.api.core.message.ConsumeLater} redeliveries, aligned with the
     * consumeLaterMaxDefers budget guidance (10) from the same batch.
     */
    public static final int DEFAULT_MAX_PENDING_REDELIVERIES = 10;

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: minimum interval between consecutive {@code delay=0}
     * redeliveries of the same subscription, aligned with the consumeLaterMinDelayMs
     * guidance (1000ms). Bounds the {@code ConsumeLater(0)} self-spin loop.
     */
    public static final long DEFAULT_REDELIVER_MIN_INTERVAL_MS = 1000L;

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: bounded capacity of the due-delivery work queue
     * that decouples the redelivery scheduler thread from handler execution.
     */
    public static final int DELIVERY_WORK_QUEUE_CAPACITY = 1000;

    /**
     * 4.1-C3-006（plan 2558 裁定 J）: delay applied when a due redelivery finds its
     * subscription suspended at fire time — the message is re-scheduled (suspension
     * accumulates a resume-flush backlog), never permanently dropped.
     */
    public static final long DEFAULT_SUSPENDED_REDELIVER_DELAY_MS = 1000L;

    // 4.1-MA-01（plan 2558 裁定 A·Local）: bounded consume wait, configured on the Local
    // module surface (bean property); <= 0 disables the bound (legacy unbounded wait).
    volatile long consumeAwaitTimeoutMs = DEFAULT_CONSUME_AWAIT_TIMEOUT_MS;

    // 4.1-MA-04（plan 2558 裁定 C）: per-subscription pending redelivery cap.
    volatile int maxPendingRedeliveriesPerSubscription = DEFAULT_MAX_PENDING_REDELIVERIES;

    // 4.1-MA-04（plan 2558 裁定 C）: delay=0 minimum-interval throttle.
    volatile long redeliverMinIntervalMs = DEFAULT_REDELIVER_MIN_INTERVAL_MS;

    // 4.1-C3-006（plan 2558 裁定 J）: re-schedule delay for fire-time suspended subscriptions.
    volatile long suspendedRedeliverDelayMs = DEFAULT_SUSPENDED_REDELIVER_DELAY_MS;

    // 4.1-MA-04（plan 2558 裁定 C）: aggregate pending-redelivery depth (gauge source).
    final AtomicInteger pendingRedeliveryDepth = new AtomicInteger();

    public int getPendingRedeliveryDepth() {
        return pendingRedeliveryDepth.get();
    }

    // 4.1-MA-04（plan 2558 裁定 C）: bounded work queue decoupling the scheduler thread
    // from handler execution; a hung handler no longer blocks due-delivery scheduling.
    volatile ExecutorService deliveryExecutor;

    volatile MessageMetrics metrics = MessageMetrics.noop();

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }
    private final Map<String, List<Subscription>> consumers = new ConcurrentHashMap<>();

    public void clearConsumers() {
        consumers.clear();
    }

    public Map<String, List<Subscription>> getConsumers() {
        return consumers;
    }

    // ---------------- 4.1-MA-01（plan 2558 裁定 A·Local）config surface ----------------

    /**
     * Bounded consume wait for handler-returned {@link CompletionStage}s
     * (consumeAwaitTimeout contract, merge-adjudication §五-6). {@code <= 0} disables
     * the bound (legacy unbounded wait).
     */
    public void setConsumeAwaitTimeoutMs(long consumeAwaitTimeoutMs) {
        this.consumeAwaitTimeoutMs = consumeAwaitTimeoutMs;
    }

    public long getConsumeAwaitTimeoutMs() {
        return consumeAwaitTimeoutMs;
    }

    // ---------------- 4.1-MA-04（plan 2558 裁定 C）config surface ----------------

    /** Per-subscription cap for pending ConsumeLater redeliveries ({@code <= 0} = unbounded). */
    public void setMaxPendingRedeliveriesPerSubscription(int cap) {
        this.maxPendingRedeliveriesPerSubscription = cap;
    }

    public int getMaxPendingRedeliveriesPerSubscription() {
        return maxPendingRedeliveriesPerSubscription;
    }

    /** Minimum interval between consecutive {@code delay<=0} redeliveries of a subscription. */
    public void setRedeliverMinIntervalMs(long redeliverMinIntervalMs) {
        this.redeliverMinIntervalMs = redeliverMinIntervalMs;
    }

    public long getRedeliverMinIntervalMs() {
        return redeliverMinIntervalMs;
    }

    /** Re-schedule delay when a due redelivery finds its subscription suspended (裁定 J). */
    public void setSuspendedRedeliverDelayMs(long suspendedRedeliverDelayMs) {
        this.suspendedRedeliverDelayMs = suspendedRedeliverDelayMs;
    }

    public long getSuspendedRedeliverDelayMs() {
        return suspendedRedeliverDelayMs;
    }

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: optional metrics injection, aligned with the
     * kafka/pulsar {@code setMetricRegistry(MeterRegistry)} form. Registers the
     * pending-redelivery depth gauge
     * ({@code nop.message.local.redelivery.pending}); a null registry keeps the no-op
     * metrics (zero overhead when not enabled).
     */
    public void setMetricRegistry(io.micrometer.core.instrument.MeterRegistry registry) {
        this.metrics = MessageMetrics.create(registry, "local");
        if (registry != null) {
            io.micrometer.core.instrument.Gauge
                    .builder("nop.message.local.redelivery.pending", pendingRedeliveryDepth::get)
                    .description("Local ConsumeLater redeliveries pending (scheduled or in delivery)")
                    .register(registry);
        }
    }

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: destroy hook for the lazily-created redelivery
     * scheduler and due-delivery worker (beans wiring:
     * {@code destroy-method="destroy"} on {@code nopLocalMessageService} — hand-maintained
     * resource, not a generated file). Idempotent; pending redeliveries after destroy are
     * abandoned with the typed {@code ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED} WARN.
     */
    volatile boolean destroyed;

    public void destroy() {
        destroyed = true;
        ScheduledExecutorService scheduler = this.redeliveryExecutor;
        if (scheduler != null)
            scheduler.shutdownNow();
        ExecutorService worker = this.deliveryExecutor;
        if (worker != null)
            worker.shutdownNow();
    }

    /**
     * 4.1-MA-01（plan 2558 裁定 A·Local）: liveness exposure of the Local consume pipeline
     * (redelivery scheduler + due-delivery worker). A never-destroyed service reports
     * active even while handlers are hung — the flag reflects shutdown state only, never
     * handler progress (isActive misreport fix: callers must not infer liveness from
     * delivery counters).
     */
    public boolean isConsumePipelineActive() {
        if (destroyed)
            return false;
        ScheduledExecutorService scheduler = this.redeliveryExecutor;
        if (scheduler != null && (scheduler.isShutdown() || scheduler.isTerminated()))
            return false;
        ExecutorService worker = this.deliveryExecutor;
        return worker == null || (!worker.isShutdown() && !worker.isTerminated());
    }

    public class Subscription implements IMessageSubscription {
        private final String topic;
        private final IMessageConsumer consumer;
        private volatile boolean suspended = false;
        private volatile boolean cancelled = false;

        // 4.1-MA-04（plan 2558 裁定 C）: per-subscription pending redelivery count.
        final AtomicInteger pendingRedeliveries = new AtomicInteger();

        // 4.1-MA-04（plan 2558 裁定 C）: last delivery start, source of the delay=0
        // minimum-interval throttle.
        volatile long lastDeliverStartNanos;

        public Subscription(String topic, IMessageConsumer consumer) {
            this.topic = topic;
            this.consumer = consumer;
        }

        boolean tryAcquirePending(int cap) {
            for (;;) {
                int current = pendingRedeliveries.get();
                if (current >= cap)
                    return false;
                if (pendingRedeliveries.compareAndSet(current, current + 1))
                    return true;
            }
        }

        void releasePending() {
            pendingRedeliveries.decrementAndGet();
            pendingRedeliveryDepth.decrementAndGet();
        }

        public int getPendingRedeliveries() {
            return pendingRedeliveries.get();
        }

        @Override
        public void cancel() {
            cancelled = true;
            // WI23 (MSG-F-15): remove by SUBSCRIPTION identity — the old consumer-identity
            // lookup removed the FIRST matching entry, so with two subscriptions of the
            // same listener ([A, B]) cancelling B deleted A and left the cancelled B in
            // place (still receiving, holder A silently unsubscribed).
            consumers.get(topic).remove(this);
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

    /**
     * Creates an INDEPENDENT subscription per call (WI23 / MSG-F-15): subscribing the same
     * listener twice yields two subscriptions that each receive every message, and
     * cancelling one never affects the other — mirroring the per-subscribe semantics of the
     * broker backends. The pre-WI23 identity-dedup shape made the second holder share the
     * first one's Subscription object, so either cancel silently unsubscribed both.
     */
    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        Guard.notEmpty(topic, "topic");
        Guard.notNull(listener, "listener");
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "local", WARN_ONCE);
        // WI23 (F-56 residual, D1 matrix Local row): subscribeName / concurrency /
        // subscriptionType pass the validator (they only require SUBSCRIBE), but the
        // in-JVM broadcast has no such concepts — declared disposition is warn-once, never
        // a silent ignore.
        if (options != null) {
            if (options.getSubscribeName() != null) {
                MessageOptionsValidator.warnOnce("local", "subscribeName",
                        "subscribeName has no meaning for the in-JVM broadcast bus and is ignored");
            }
            if (options.getConcurrency() > 1) {
                MessageOptionsValidator.warnOnce("local", "concurrency",
                        "concurrency has no meaning for the in-JVM broadcast bus and is ignored");
            }
            if (options.getSubscriptionType() != null) {
                MessageOptionsValidator.warnOnce("local", "subscriptionType",
                        "subscriptionType " + options.getSubscriptionType()
                                + " has no meaning for the in-JVM broadcast bus and is ignored");
            }
        }

        List<Subscription> subscriptions = consumers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        Subscription subscription = new Subscription(topic, listener);
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public void send(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "local", WARN_ONCE);
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
        // 4.1-MA-04（plan 2558 裁定 C）: mark delivery start — source of the delay=0
        // minimum-interval redelivery throttle.
        subscription.lastDeliverStartNanos = System.nanoTime();
        try {
            Object ret = awaitConsumeResult(subscription,
                    subscription.consumer.onMessage(subscription.topic, message, context));
            if (ret instanceof ConsumeLater) {
                scheduleRedelivery(subscription, message, ((ConsumeLater) ret).getDelay());
            } else if (ret instanceof TerminateConsume) {
                // D5(b-prime): acknowledged, no redelivery, no reply. Local has no DLQ
                // substrate — the message is dropped with this proof-by-WARN (WI23,
                // aligned with the broker backends' terminate logging).
                LOG.warn("nop.message.local.terminate-consume:topic={},reason={}",
                        subscription.topic, ((TerminateConsume) ret).getReason());
            } else if (ret instanceof PartialCommit) {
                // Local has no batch consume path and no commit concept; single-message
                // PartialCommit is treated as completion (declared in the WI6 plan).
            } else {
                handleMessageResult(ret, subscription.topic, message, context);
            }
        } catch (Throwable e) {
            // 4.1-MA-01（plan 2558 裁定 A·Local）: the consume-await timeout takes a DEDICATED
            // branch — dispatchConsumeFailure 留证丢弃 + WARN, attempt=0 semantics (§五-6).
            // It is NOT a handler failure feeding any poison/replay budget: a hung handler
            // re-timed-out forever is an explicitly accepted, observable surface, and the
            // failure budget stays reserved for real handler failures.
            if (isConsumeAwaitTimeout(e)) {
                LOG.warn("nop.message.local.consume-await-timeout:code={},topic={},timeoutMs={}",
                        ERR_LOCAL_CONSUME_AWAIT_TIMEOUT.getErrorCode(), subscription.topic,
                        consumeAwaitTimeoutMs, e);
                dispatchConsumeFailure(subscription, message, e);
                return;
            }
            // C3-009 ③ (plan 2496 Phase 4): the barrier must cover Throwable. Pre-fix an
            // Error escaped to the synchronous send chain (sender thread) or, on the
            // redelivery scheduler thread, killed the delayed task silently (the deferred
            // message was lost without a WARN). Catching keeps both threads alive and
            // keeps the failure observable.
            LOG.error("nop.message.consumer-failed:topic={},message={}", subscription.topic, message, e);
            // C8-002 (plan 2503 Phase 2, D6 three-layer architecture): Local is a no-DLQ-
            // substrate backend, so the FIRST layer (SPI failure callback) must still fire —
            // pre-fix it never did, the three-layer chain (callback →
            // MessageConsumeFailureBridge) was dead on Local, and the failure was
            // at-most-once with zero visibility beyond this log. Guarded dispatch
            // (KafkaConsumeTask:dispatchConsumeFailure shape, WI16): a throwing user
            // callback must not kill the sender / redelivery thread.
            dispatchConsumeFailure(subscription, message, e);
        }
    }

    /**
     * C8-002 (plan 2503 Phase 2): first-layer failure callback for the Local backend,
     * aligned with KafkaConsumeTask:dispatchConsumeFailure (WI16). attempt is fixed 0 —
     * Local has no broker redelivery loop (design §3.9), so no cross-delivery budget is
     * tracked; the ConsumeLater deferred redelivery is an explicit contract signal, not a
     * failure-budget replay.
     *
     * <p><strong>Redelivery adjudication (explicit, plan 2503 Phase 2 — never silently
     * undecided)</strong>: a Local delivery terminated by an exception is NOT automatically
     * redelivered. {@code ConsumeLater} remains the only redelivery path (per-subscription
     * timer); an exception-terminated delivery is dropped with the
     * {@code nop.message.consumer-failed} WARN + this first-layer callback as its two
     * visibility channels — the design §3.3 rule for no-DLQ-substrate backends ("first-layer
     * callback only, drop with proof"). Replay is the deployment's business via the
     * third-layer {@code MessageConsumeFailureBridge} wired inside
     * {@code onConsumeFailure}.
     */
    private void dispatchConsumeFailure(Subscription subscription, Object message, Throwable e) {
        try {
            subscription.consumer.onConsumeFailure(new ConsumeFailure(
                    subscription.topic, null, message, 0, asReportedException(e)));
        } catch (Throwable callbackError) {
            // C3-009 ⑥ guard width: a throwing user callback must not escape into the
            // sender / redelivery thread
            LOG.warn("nop.message.local.failure-callback-throw:topic={}",
                    subscription.topic, callbackError);
        }
    }

    private static Exception asReportedException(Throwable e) {
        if (e instanceof Exception) {
            return (Exception) e;
        }
        return new IllegalStateException("non-exception throwable: " + e, e);
    }

    /**
     * 4.1-MA-01（plan 2558 裁定 A·Local）: bounded consume wait. A handler-returned
     * {@link CompletionStage} must be a {@link CompletableFuture} (any other shape is a
     * typed {@link #ERR_LOCAL_CONSUME_AWAIT_NOT_FUTURE} rejection, never a silent
     * fallback); the wait is bounded by {@link #consumeAwaitTimeoutMs}. {@code <= 0}
     * restores the legacy unbounded wait. Non-future results pass through unchanged.
     */
    private Object awaitConsumeResult(Subscription subscription, Object result) {
        if (!(result instanceof CompletionStage))
            return result;
        CompletableFuture<Object> future;
        try {
            future = ((CompletionStage<Object>) result).toCompletableFuture();
        } catch (UnsupportedOperationException e) {
            throw new NopException(ERR_LOCAL_CONSUME_AWAIT_NOT_FUTURE)
                    .param("topic", subscription.topic);
        }
        long timeout = consumeAwaitTimeoutMs;
        if (timeout <= 0) {
            return FutureHelper.syncGet(future);
        }
        return FutureHelper.waitResult(future, timeout);
    }

    private static boolean isConsumeAwaitTimeout(Throwable e) {
        return FutureHelper.isTimeoutException(e) || FutureHelper.isTimeoutException(e.getCause());
    }

    /**
     * Schedules a delayed redelivery of the message to the subscription that returned
     * {@link ConsumeLater} (F-14 minimal fix). Adjudicated semantics (WI4 plan):
     *
     * <p><strong>Per-subscription scope</strong> — only the subscriber that deferred is
     * re-invoked; a topic-wide redelivery would re-send to already-acknowledged
     * subscribers. WI23 must keep this semantics when aligning F-15.
     *
     * <p><strong>Fire-time state re-check — abandon matrix (4.1-C3-006, plan 2558 裁定 J;
     * declared here, never silently undecided)</strong>: when the scheduled task fires,
     * <ul>
     * <li>{@code cancelled == true} → the pending redelivery is DISCARDED (contract
     * behavior, the only abandon path);</li>
     * <li>subscription absent from the consumers map (clearConsumers / cancel) →
     * DISCARDED;</li>
     * <li>{@code suspended == true} at FIRE time → the message is RE-SCHEDULED
     * (suspend accumulates a resume-flush backlog bounded by the pending cap) —
     * suspension never permanently drops an already-deferred message. (Send-time
     * suspension still skips delivery at dispatch, per the WI4 contract.)</li>
     * </ul>
     *
     * <p><strong>Bounded envelope (4.1-MA-04, plan 2558 裁定 C)</strong>: pending
     * redeliveries are capped per subscription; a {@code delay<=0} ConsumeLater is
     * throttled to at most one delivery per {@link #redeliverMinIntervalMs}; the due
     * delivery is handed to a bounded work queue so a hung handler cannot block the
     * scheduler thread.
     */
    private void scheduleRedelivery(Subscription subscription, Object message, long delayMs) {
        long delay = Math.max(delayMs, 0L);
        // 4.1-MA-04（plan 2558 裁定 C）: delay=0 minimum-interval throttle — bounds the
        // ConsumeLater(0) self-spin loop to one delivery per min interval.
        if (delay == 0) {
            long minInterval = redeliverMinIntervalMs;
            if (minInterval > 0) {
                long sinceLast = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - subscription.lastDeliverStartNanos);
                if (sinceLast < minInterval)
                    delay = minInterval - sinceLast;
            }
        }
        // 4.1-MA-04（plan 2558 裁定 C）: bounded pending queue (per-subscription cap,
        // aligned with the consumeLaterMaxDefers budget). Overflow drops with a typed
        // WARN — the cap is observable via the pending-depth gauge.
        int cap = maxPendingRedeliveriesPerSubscription;
        if (cap > 0 && !subscription.tryAcquirePending(cap)) {
            LOG.warn("nop.message.local.redelivery-pending-overflow:code={},topic={},pending={},cap={}",
                    ERR_LOCAL_REDELIVERY_PENDING_OVERFLOW.getErrorCode(),
                    subscription.topic, subscription.getPendingRedeliveries(), cap);
            return;
        }
        pendingRedeliveryDepth.incrementAndGet();
        try {
            redeliveryScheduler().schedule(() -> enqueueDelivery(subscription, message),
                    delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // WI26 (F-40): scheduler shutdown during teardown — the deferred redelivery is
            // abandoned with a typed WARN (proving the previously-empty MessageCoreErrors).
            subscription.releasePending();
            LOG.warn("nop.message.local.redelivery-schedule-failed:code={},topic={},delay={}",
                    ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED.getErrorCode(),
                    subscription.topic, delay, e);
        }
    }

    /**
     * 4.1-MA-04（plan 2558 裁定 C）: the due-delivery handler moves from the scheduler
     * thread to a bounded work queue (single worker) — a slow/hung handler (now bounded
     * by consumeAwaitTimeoutMs) can no longer head-of-line-block the scheduling of other
     * due redeliveries. Queue overflow / shutdown abandons the delivery with a typed
     * WARN (drop-with-proof).
     */
    private void enqueueDelivery(Subscription subscription, Object message) {
        try {
            deliveryWorker().execute(() -> runDueDelivery(subscription, message));
        } catch (RejectedExecutionException e) {
            subscription.releasePending();
            LOG.warn("nop.message.local.redelivery-schedule-failed:code={},topic={}",
                    ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED.getErrorCode(),
                    subscription.topic, e);
        }
    }

    private void runDueDelivery(Subscription subscription, Object message) {
        // Pending-slot accounting: the slot is released as soon as the delivery actually
        // begins (or is abandoned) — a ConsumeLater returned inside the handler re-acquires
        // a fresh slot, so depth accounting never double-holds during hand-off.
        boolean released = false;
        try {
            // abandon matrix (javadoc on scheduleRedelivery): only cancelled / detached
            // subscriptions discard; suspended re-schedules (裁定 J).
            if (subscription.cancelled) {
                subscription.releasePending();
                return;
            }
            if (subscription.suspended) {
                LOG.debug("nop.message.local.redelivery-suspended-reschedule:topic={}",
                        subscription.topic);
                subscription.releasePending();
                released = true;
                scheduleRedelivery(subscription, message, suspendedRedeliverDelayMs);
                return;
            }
            List<Subscription> subscriptions = consumers.get(subscription.topic);
            // WI23: instance identity — a sibling subscription of the same listener must
            // not satisfy THIS subscription's presence check.
            if (subscriptions == null || !subscriptions.contains(subscription)) {
                subscription.releasePending();
                return;
            }
            subscription.releasePending();
            released = true;
            invokeSubscription(subscription, message);
        } catch (Throwable t) {
            // C3-009 width guard: the delivery worker must never die silently.
            LOG.error("nop.message.local.delivery-worker-error:topic={}",
                    subscription.topic, t);
            if (!released)
                subscription.releasePending();
        }
    }

    // WI26: test seam so tests can inject a shut-down scheduler (public: consumers of the
    // class live in io.nop.message.core, one package up).
    volatile ScheduledExecutorService redeliveryExecutor;

    public void setRedeliveryExecutorForTest(ScheduledExecutorService executor) {
        this.redeliveryExecutor = executor;
    }

    // 4.1-MA-04 test seam: inject the due-delivery worker (e.g. a same-thread executor
    // for deterministic carrier tests).
    public void setDeliveryExecutorForTest(ExecutorService executor) {
        this.deliveryExecutor = executor;
    }

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
     * 4.1-MA-04（plan 2558 裁定 C）: lazily-created single-worker executor with a bounded
     * work queue ({@link #DELIVERY_WORK_QUEUE_CAPACITY}); overflow aborts with
     * {@link RejectedExecutionException}, which {@link #enqueueDelivery} turns into the
     * typed drop-with-proof WARN. Never a globalTimer (WI 2.1 C4-001 adjudication).
     */
    private ExecutorService deliveryWorker() {
        ExecutorService executor = deliveryExecutor;
        if (executor == null) {
            synchronized (this) {
                if (deliveryExecutor == null) {
                    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(DELIVERY_WORK_QUEUE_CAPACITY),
                            r -> {
                                Thread t = new Thread(r, "nop-local-message-delivery");
                                t.setDaemon(true);
                                return t;
                            },
                            new ThreadPoolExecutor.AbortPolicy());
                    worker.allowCoreThreadTimeOut(true);
                    deliveryExecutor = worker;
                }
                executor = deliveryExecutor;
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
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "local", WARN_ONCE);
        return FutureHelper.futureRun(() -> send(topic, message, options));
    }
}
