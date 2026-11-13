/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.MessageErrors;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.MultiMessageSubscription;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.message.core.MessageMetrics;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.nop.message.pulsar.PulsarErrors.ERR_SERVICE_URL_NOT_CONFIGURED;

/**
 * Pulsar backend for {@link IMessageService}. Lifecycle runs on the platform
 * {@link LifeCycleSupport} state machine (WI19): the public {@link #init()} /
 * {@link #destroy()} pair required by the beans wiring delegates to {@code start()} /
 * {@code stop()}, so IoC callers, tests and lifecycle tooling share one idempotent state
 * machine.
 */
public class PulsarMessageService extends LifeCycleSupport implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(PulsarMessageService.class);

    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.BATCH_CONSUME,
            MessageCapability.DELAYED_SEND, MessageCapability.SEEK,
            MessageCapability.ACK_REPLY, MessageCapability.ENVELOPE_METADATA,
            MessageCapability.SEND_TIMEOUT);

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }

    private PulsarClientConfig config;
    private PulsarProducerConfig defaultProducerConfig;
    private PulsarConsumerConfig defaultConsumerConfig;
    // WI29: package-private for the test subclass access (TestPulsarService.doStart).
    PulsarClient client;

    // WI30 (MSG-F-23): metrics — null registry = no-op.
    volatile MessageMetrics metrics = MessageMetrics.noop();

    public void setMetricRegistry(io.micrometer.core.instrument.MeterRegistry registry) {
        this.metrics = MessageMetrics.create(registry, "pulsar");
    }
    private Map<String, Schema<?>> topicSchemas = new ConcurrentHashMap<>();
    private Map<String, Producer<?>> producers = new ConcurrentHashMap<>();
    // 4.1-C8-007（plan 2558 裁定 L）：in-flight producer creation placeholders（见 getProducer）。
    private final Map<String, CompletableFuture<Producer<?>>> producerCreations =
            new ConcurrentHashMap<>();
    // 4.1-MA-06（plan 2558 裁定 D）：producer 创建与 doStop 排空共享的服务级锁 —— 关闭
    // buildProducer 的 client!=null 检查与 client.newProducer 使用之间的 TOCTOU 窗口。
    private final Object producerLifecycleLock = new Object();
    private Queue<PulsarMessageSubscription> subscriptions = new ConcurrentLinkedQueue<>();

    public void setConfig(PulsarClientConfig config) {
        this.config = config;
    }

    public void setDefaultProducerConfig(PulsarProducerConfig defaultProducerConfig) {
        this.defaultProducerConfig = defaultProducerConfig;
    }

    public void setDefaultConsumerConfig(PulsarConsumerConfig defaultConsumerConfig) {
        this.defaultConsumerConfig = defaultConsumerConfig;
    }

    /**
     * WI33 (F-DLQ): package-private read-through of the service-level consumer config for
     * the consume task's failure endpoint (same pattern as the Kafka task reading
     * {@code resolveDeadLetterTopic} through its service reference). {@code null} = not
     * configured.
     */
    PulsarConsumerConfig defaultConsumerConfig() {
        return defaultConsumerConfig;
    }

    public void setTopicSchemas(Map<String, Schema<?>> topicSchemas) {
        if (topicSchemas != null) {
            this.topicSchemas.putAll(topicSchemas);
        }
    }

    /**
     * Beans-wiring entry point ({@code init-method="init"}), delegating to the
     * {@link LifeCycleSupport} state machine. Failure semantics: a missing serviceUrl
     * rejects with {@code ERR_SERVICE_URL_NOT_CONFIGURED}; a failed broker client creation
     * surfaces as {@code NopException.adapt(cause)} — the pre-WI19 checked
     * {@code throws PulsarClientException} no longer exists because
     * {@code LifeCycleSupport.doStart()} cannot declare checked exceptions.
     */
    public void init() {
        start();
    }

    @Override
    protected void doStart() {
        if (config == null || config.getServiceUrl() == null) {
            throw new NopException(ERR_SERVICE_URL_NOT_CONFIGURED);
        }
        // WI26 (F-55): the typed wrap lives at the CALL SITE — a createClient override
        // cannot bypass it (a wrap inside the method body would be replaced by the
        // overriding body).
        try {
            client = createClient();
        } catch (Exception e) {
            throw new NopException(PulsarErrors.ERR_PULSAR_CLIENT_CREATE_FAILED)
                    .description("Failed to create Pulsar client")
                    .cause(e);
        }

        LOG.info("nop.message.pulsar.initialized:serviceUrl={}", config.getServiceUrl());
    }

    /** Injection seam for tests (mirrors the Kafka createConsumer factory): lets a test
     * subclass supply a mock {@link PulsarClient} so lifecycle tests need no broker. */
    protected PulsarClient createClient() {
        try {
            return PulsarClient.builder()
                    .serviceUrl(config.getServiceUrl())
                    .build();
        } catch (PulsarClientException e) {
            throw NopException.adapt(e);
        }
    }

    /** Beans-wiring entry point ({@code destroy-method="destroy"}). */
    public void destroy() {
        stop();
    }

    @Override
    protected void doStop() {
        // F-27 (WI20 final order, aligned with Kafka): subscriptions are cancelled BEFORE
        // producers — in-flight sends during shutdown keep a live producer, and the
        // subscription cancel awaits its consume thread (close-after-termination).
        for (PulsarMessageSubscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.close-subscription-failed", e);
            }
        }
        subscriptions.clear();

        // 4.1-MA-06（plan 2558 裁定 D）：producer 排空 + client 关闭与 buildProducer 同锁 ——
        // 二次排空语义：锁内检查后清理，杜绝「检查时 client 非空、使用时已关闭」的 TOCTOU。
        synchronized (producerLifecycleLock) {
            for (Producer<?> producer : producers.values()) {
                try {
                    producer.close();
                } catch (Exception e) {
                    LOG.error("nop.message.pulsar.close-producer-failed", e);
                }
            }
            producers.clear();
            // 4.1-C8-007: in-flight creation placeholders are cancelled with the service;
            // a caller parked on one wakes exceptionally (never cached negative).
            for (CompletableFuture<Producer<?>> pending : producerCreations.values()) {
                pending.cancel(false);
            }
            producerCreations.clear();

            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    LOG.error("nop.message.pulsar.close-client-failed", e);
                }
                client = null;
            }
        }

        LOG.info("nop.message.pulsar.destroyed");
    }

    PulsarClient getClient() {
        return client;
    }

    // Test seam (WI15): aligns with the Kafka package-private producer injection convention.
    java.util.Map<String, Producer<?>> producersForTest() {
        return producers;
    }

    /**
     * 4.1-C8-007（plan 2558 裁定 L）：producer lookup with async creation + bounded wait.
     * The previous {@code computeIfAbsent(topic, k -> buildProducer(...))} ran the
     * blocking broker connect INSIDE the map's bin lock: a slow/blocked connect stalled
     * every other topic's lookup on the same striped lock (serial amplification), and
     * queued callers then retried the connect one-by-one.
     *
     * <p>New shape: the first caller installs a placeholder {@code CompletableFuture},
     * kicks {@code ProducerBuilder.createAsync()} OUTSIDE any lock, and every concurrent
     * caller (including the first) waits with a timeout from
     * {@code PulsarProducerConfig.sendTimeout} (floor 1s; unconfigured default 30s).
     * Failure semantics are unchanged: a failed creation is never cached negatively —
     * the placeholder is removed and the next caller retries; the failed future wakes
     * all queued waiters together instead of serially.
     *
     * <p>Non-shadowing declaration（C1-004 同站点异机制）: C1-004 guards the destroy window
     * (typed NOT_ACTIVE when the service is down — via {@code buildProducerAsync}'s
     * lifecycle-lock check); C8-007 guards the BLOCKED-CONNECT window (this timeout).
     * They compose: a destroy during a hung connect fails the placeholder through the
     * doStop cancel, the waiter wakes exceptionally — neither mechanism masks the other.
     */
    Producer<?> getProducer(String topic) {
        Producer<?> producer = producers.get(topic);
        if (producer != null)
            return producer;
        // MSG-NEW-03 (WI9): a topicless producer is invalid in Pulsar — unknown topics
        // get an on-demand producer with the default STRING schema instead of falling back
        // to a shared topicless defaultProducer.
        Schema<?> schema = topicSchemas.getOrDefault(topic, Schema.STRING);
        CompletableFuture<Producer<?>> creation = producerCreations.computeIfAbsent(topic, k -> {
            CompletableFuture<Producer<?>> promise = new CompletableFuture<>();
            startProducerCreation(topic, schema, promise);
            return promise;
        });
        // Never cache a negative: the placeholder removes itself on completion. Registered
        // AFTER computeIfAbsent returns — a synchronously-completing creation would
        // otherwise fire the removal BEFORE the map insertion (removal no-op) and leave
        // the failed future cached.
        creation.whenComplete((p, err) -> producerCreations.remove(topic, creation));
        long timeoutMs = defaultProducerConfig != null
                ? Math.max(defaultProducerConfig.getSendTimeout(), 1000L)
                : 30000L;
        try {
            return creation.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            creation.cancel(false);
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Timed out creating Pulsar producer after " + timeoutMs
                            + "ms:topic=" + topic)
                    .cause(e);
        } catch (java.util.concurrent.CancellationException e) {
            // The service stopped (or a concurrent waiter timed out) and cancelled the
            // placeholder — typed failure, never a raw CancellationException.
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Pulsar producer creation cancelled for topic " + topic)
                    .cause(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Interrupted while creating Pulsar producer:topic=" + topic)
                    .cause(e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NopException) {
                throw (NopException) cause;
            }
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Failed to create Pulsar producer:topic=" + topic)
                    .cause(cause);
        }
    }

    /** 4.1-C8-007: kicks the async connect; a synchronous failure fails the placeholder. */
    private void startProducerCreation(String topic, Schema<?> schema,
                                       CompletableFuture<Producer<?>> promise) {
        try {
            createProducerAsync(topic, schema).whenComplete((p, err) -> {
                if (err != null) {
                    promise.completeExceptionally(err);
                } else {
                    producers.put(topic, p);
                    promise.complete(p);
                }
            });
        } catch (Exception e) {
            promise.completeExceptionally(e);
        }
    }

    /**
     * 4.1-C8-007 creation seam (overridable like the legacy {@link #buildProducer}): the
     * async connect kicked OUTSIDE the placeholder map. Default routes through
     * {@link #buildProducerAsync} (MA-06 lifecycle-lock guard).
     */
    CompletableFuture<Producer<?>> createProducerAsync(String topic, Schema<?> schema) {
        return buildProducerAsync(topic, schema);
    }

    /**
     * 4.1-C8-007 / 4.1-MA-06: async producer creation. The not-active guard runs under
     * {@code producerLifecycleLock} — the same lock {@code doStop} holds while draining
     * producers and closing the client — so a concurrent destroy cannot interleave
     * between the guard and {@code newProducer} (TOCTOU window closed; the MA-06
     * buildProducer face). C1-004 non-shadowing declaration: see {@link #getProducer}.
     */
    CompletableFuture<Producer<?>> buildProducerAsync(String topic, Schema<?> schema) {
        synchronized (producerLifecycleLock) {
            if (client == null) {
                throw new NopException(PulsarErrors.ERR_PULSAR_SERVICE_NOT_ACTIVE)
                        .description("PulsarMessageService is not active; cannot create producer for topic "
                                + topic);
            }
            ProducerBuilder<?> builder = client.newProducer(schema).topic(topic);
            if (defaultProducerConfig != null) {
                builder.enableBatching(defaultProducerConfig.isBatchingEnabled());
                builder.batchingMaxMessages(defaultProducerConfig.getBatchMaxMessages());
                builder.sendTimeout(defaultProducerConfig.getSendTimeout(), TimeUnit.MILLISECONDS);
            }
            // Wildcard capture: createAsync() returns Producer<CAP>; erased view is safe —
            // the map stores untyped Producer<?> everywhere else too.
            @SuppressWarnings({"unchecked", "rawtypes"})
            CompletableFuture<Producer<?>> future = (CompletableFuture) builder.createAsync();
            return future;
        }
    }

    /**
     * Synchronous producer creation, delegating to {@link #buildProducerAsync} (shared
     * lifecycle-lock guard). Kept for the direct-callers/tests that expect the sync
     * typed-rejection face (F-30/WI20, WI26/F-55 wrap).
     */
    Producer<?> buildProducer(String topic, Schema<?> schema) {
        try {
            return buildProducerAsync(topic, schema)
                    .get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Failed to create Pulsar producer")
                    .cause(e.getCause());
        } catch (Exception e) {
            throw new NopException(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED)
                    .description("Failed to create Pulsar producer")
                    .cause(e);
        }
    }

    @Override
    public String getAckTopic(String topic) {
        return PulsarHelper.deriveAckTopic(topic);
    }

    /**
     * 4.1-C3-004（plan 2558 裁定 H）：send envelope — the whole segment INCLUDING
     * {@code getProducer} is try/caught (mirrors Kafka {@code sendAsync} :288-311): any
     * failure completes the returned future exceptionally with the
     * {@code ERR_PULSAR_SEND_FAILED} envelope; the original exception — including the
     * NOT_ACTIVE {@code NopException} — is preserved as the envelope cause
     * (失败不缓存：getProducer 失败占位已由 C8-007 自移除). NEVER a synchronous throw:
     * the {@code IMessageService.sendAsync} contract is a CompletionStage.
     *
     * <p>NOT_ACTIVE residual fork declaration（裁定 H）: Kafka surfaces not-active as a
     * synchronous typed throw (getProducer 外置), Pulsar as this failed-future envelope —
     * registered in {@code docs-for-ai/03-modules/nop-message.md}.
     */
    private CompletableFuture<Void> sendPulsarAsync(String topic, Object message, MessageSendOptions options) {
        try {
            return doSendPulsarAsync(topic, message, options);
        } catch (Exception e) {
            return failedSendFuture(topic, e);
        }
    }

    private static CompletableFuture<Void> failedSendFuture(String topic, Throwable cause) {
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new NopException(PulsarErrors.ERR_PULSAR_SEND_FAILED)
                .description("Pulsar producer send failed:topic=" + topic)
                .cause(cause));
        return failed;
    }

    private CompletableFuture<Void> doSendPulsarAsync(String topic, Object message,
                                                      MessageSendOptions options) {
        long sendStartNanos = System.nanoTime();
        metrics.incrementSent();
        if (options != null && options.getCancelToken() != null && options.getCancelToken().isCancelled()) {
            // WI15 (F-45): pre-send cancellation check; rejected future per the
            // MessageRpcClient.callAsync precedent (never a synchronous throw).
            java.util.concurrent.CompletableFuture<Void> rejected = new java.util.concurrent.CompletableFuture<>();
            rejected.completeExceptionally(new NopException(MessageErrors.ERR_SEND_CANCELLED)
                    .description("send cancelled before dispatch:topic=" + topic));
            return rejected;
        }
        Producer<?> producer = getProducer(topic);
        TypedMessageBuilder<?> builder = producer.newMessage();
        PulsarHelper.buildPulsarMessage(builder, message, options);

        CompletableFuture<Void> future = builder.sendAsync().thenAccept(ret -> {
        });
        // WI15 (F-45): per-message upper bound (the Pulsar builder-level sendTimeout is a
        // second, lower layer). Fire-and-forget: timeout leaves the send running.
        long sendTimeoutMs = resolveSendTimeoutMs(options);
        if (sendTimeoutMs > 0) {
            future.orTimeout(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        metrics.recordSendDuration(System.nanoTime() - sendStartNanos);
        return future;
    }

    private long resolveSendTimeoutMs(MessageSendOptions options) {
        if (options != null && options.getSendTimeout() > 0) {
            return options.getSendTimeout();
        }
        if (defaultProducerConfig != null) {
            return defaultProducerConfig.getSendTimeout();
        }
        return 0L;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "pulsar");
        return sendPulsarAsync(topic, message, options);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (options == null)
            options = new MessageSubscribeOptions();
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "pulsar");
        // Validated on the caller's thread before any client/builder is touched: the typed
        // rejection must reach the subscribe caller (MSG-F-03, aligned with the Kafka WI7 form).
        if (options.getSeekToMessage() != null) {
            throw new NopException(MessageErrors.ERR_SEEK_MESSAGE_NOT_SUPPORTED)
                    .description("seekToMessage is not supported by the Pulsar backend");
        }

        // WI27 (D4/F-32): resolve the effective Pulsar type once (mode > legacy type >
        // default), deprecate the legacy field, and make concurrency×type deterministic —
        // SINGLE_ACTIVE semantics clamp concurrency to 1 (the javadoc promise, previously
        // broken by a broker rejection), LOAD_BALANCED/ORDERED_BY_KEY honor N, and the
        // previously hard-failing unconfigured+N combination now runs Shared.
        if (options.getSubscriptionMode() != null && options.getSubscriptionType() != null) {
            MessageOptionsValidator.warnOnce("pulsar", "mode-type-conflict",
                    "subscriptionMode " + options.getSubscriptionMode()
                            + " takes precedence; the deprecated subscriptionType is ignored");
        }
        if (options.getSubscriptionType() != null) {
            MessageOptionsValidator.warnOnce("pulsar", "legacy-subscription-type",
                    "subscriptionType is deprecated; use subscriptionMode (D4 deprecation window)");
        }

        org.apache.pulsar.client.api.SubscriptionType effective = resolveEffectivePulsarType(options);
        boolean singleActive = effective == org.apache.pulsar.client.api.SubscriptionType.Exclusive
                || effective == org.apache.pulsar.client.api.SubscriptionType.Failover;
        boolean defaultShared = options.getSubscriptionMode() == null
                && options.getSubscriptionType() == null
                && options.getConcurrency() > 1;
        // WI29 (F-29): subscribeName is required — a missing value previously let Pulsar
        // generate a random subscription name, producing a new cursor on every restart.
        if (options.getSubscribeName() == null || options.getSubscribeName().isEmpty()) {
            throw new NopException(PulsarErrors.ERR_PULSAR_SUBSCRIBE_NAME_NOT_CONFIGURED)
                    .description("MessageSubscribeOptions.subscribeName is required by the Pulsar backend");
        }

        int concurrency = options.getConcurrency();
        if (concurrency <= 1) {
            return doSubscribe(new MessageSubscriptionConfig(topic, listener, options));
        }
        if (singleActive) {
            MessageOptionsValidator.warnOnce("pulsar", "concurrency-ignored-single-active",
                    "concurrency > 1 is ignored for SINGLE_ACTIVE semantics (effective type: "
                            + effective + "); one consumer is started");
            return doSubscribe(new MessageSubscriptionConfig(topic, listener, options));
        }
        if (defaultShared) {
            MessageOptionsValidator.warnOnce("pulsar", "concurrency-default-shared",
                    "no subscriptionType/subscriptionMode configured with concurrency > 1: "
                            + "the subscription runs as Shared (the previous behavior failed "
                            + "outright on the exclusive default)");
        }
        // WI29 (F-37): fill-then-construct — the old shape passed a mutable empty list to
        // the Multi constructor and populated it after, so the constructor's immutable
        // snapshot would be empty and cancel() would be a no-op.
        List<IMessageSubscription> subs = new ArrayList<>(concurrency);
        MessageSubscriptionConfig cfg = new MessageSubscriptionConfig(topic, listener, options);
        RuntimeException first = null;
        try {
            for (int i = 0; i < concurrency; i++) {
                subs.add(doSubscribe(cfg));
            }
        } catch (RuntimeException e) {
            first = e;
            for (IMessageSubscription sub : subs) {
                try {
                    sub.cancel();
                } catch (RuntimeException cancelError) {
                    first.addSuppressed(cancelError);
                }
            }
            throw first;
        }
        return new MultiMessageSubscription(subs);
    }

    /**
     * WI27 (D4/F-32): single source of the effective Pulsar subscription type — serves the
     * builder wiring AND the DLQ warn-once check (a mode-only configuration used to be
     * misdiagnosed there). Precedence: subscriptionMode > legacy subscriptionType >
     * default Shared when concurrency > 1, else Pulsar default (Exclusive).
     */
    static org.apache.pulsar.client.api.SubscriptionType resolveEffectivePulsarType(
            MessageSubscribeOptions options) {
        if (options.getSubscriptionMode() != null) {
            switch (options.getSubscriptionMode()) {
                case LOAD_BALANCED:
                    return org.apache.pulsar.client.api.SubscriptionType.Shared;
                case ORDERED_BY_KEY:
                    return org.apache.pulsar.client.api.SubscriptionType.Key_Shared;
                case SINGLE_ACTIVE:
                default:
                    return org.apache.pulsar.client.api.SubscriptionType.Exclusive;
            }
        }
        if (options.getSubscriptionType() != null) {
            return toPulsarSubscriptionType(options.getSubscriptionType());
        }
        if (options.getConcurrency() > 1) {
            return org.apache.pulsar.client.api.SubscriptionType.Shared;
        }
        return org.apache.pulsar.client.api.SubscriptionType.Exclusive;
    }

    /** Injection seam for tests (mirrors the Kafka createConsumer factory, WI7). */
    protected ConsumerBuilder<?> newConsumerBuilder(Schema<?> schema) {
        return client.newConsumer(schema);
    }

    /**
     * WI22: retry budget before the failure endpoint ({@code 0} = unlimited). Falls back to
     * the module default — never to 0 — so a directly constructed service keeps production
     * semantics.
     */
    int maxConsumeAttempts() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getMaxConsumeAttempts()
                : PulsarConsumeTask.DEFAULT_MAX_CONSUME_ATTEMPTS;
    }

    /** WI22: upper bound of the escalating error backoff. */
    long consumeErrorBackoffMaxMs() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeErrorBackoffMaxMs()
                : PulsarConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MAX_MS;
    }

    /**
     * 4.1-MA-01（plan 2558 裁定 A）：consume await timeout from
     * {@code PulsarConsumerConfig.consumeAwaitTimeoutMs} (module default when unconfigured).
     */
    long consumeAwaitTimeoutMs() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeAwaitTimeoutMs()
                : PulsarConsumeTask.DEFAULT_CONSUME_AWAIT_TIMEOUT_MS;
    }

    /** 4.1-C8-005（plan 2558 裁定 K，同键族）：reconsumeLater delay 下限（模块缺省兜底）。 */
    long consumeLaterMinDelayMs() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeLaterMinDelayMs()
                : PulsarConsumeTask.DEFAULT_CONSUME_LATER_MIN_DELAY_MS;
    }

    /** 4.1-C8-005（plan 2558 裁定 K，同键族）：连续 defer 预算（0 = 不限）。 */
    int consumeLaterMaxDefers() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeLaterMaxDefers()
                : PulsarConsumeTask.DEFAULT_CONSUME_LATER_MAX_DEFERS;
    }

    IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
        // 4.1-MA-06（plan 2558 裁定 D）：checkIsActive 前置交换 —— 守卫必须先于
        // newConsumerBuilder（builder 读取 this.client）；destroy-then-subscribe 现在在任何
        // client 触碰之前 typed 拒绝（F-30 subscribe path，窗口收窄到零）。
        checkIsActive();
        Schema<?> schema = topicSchemas.get(subConfig.getTopic());
        if (schema == null) {
            schema = Schema.STRING;
        }
        ConsumerBuilder<?> builder = newConsumerBuilder(schema);
        builder.topic(subConfig.getTopic());

        MessageSubscribeOptions options = subConfig.getOptions();
        if (options != null) {
            if (options.getSubscribeName() != null) {
                builder.subscriptionName(options.getSubscribeName());
            }
            // WI27 (D4): the resolver is the single source — mode > legacy type >
            // (default Shared when concurrency > 1, else Pulsar default Exclusive).
            builder.subscriptionType(resolveEffectivePulsarType(options));
            applyInitialPosition(builder, options.getSeekMode());
        }

        // WI25 (F-17): batchReceiveCount wires the client batch gate. The client-default
        // timeout/maxNumBytes are preserved (NOT a maxNumMessages-only policy): a gate-only
        // policy would make a low-traffic topic wait unboundedly for `count` messages
        // (the ≤100ms client-default completion bound is the pre-WI25 latency envelope).
        // Kafka's max.poll.records is a pure cap — the asymmetry is declared in design 3.10.
        if (options != null && options.getBatchReceiveCount() > 0) {
            builder.batchReceivePolicy(org.apache.pulsar.client.api.BatchReceivePolicy.builder()
                    .maxNumMessages(options.getBatchReceiveCount())
                    .maxNumBytes(org.apache.pulsar.client.api.BatchReceivePolicy.DEFAULT_POLICY.getMaxNumBytes())
                    .timeout(100, TimeUnit.MILLISECONDS)
                    .build());
        }

        if (defaultConsumerConfig != null) {
            if (defaultConsumerConfig.getAckTimeout() > 0) {
                builder.ackTimeout(defaultConsumerConfig.getAckTimeout(), TimeUnit.MILLISECONDS);
            }
            if (defaultConsumerConfig.getNegativeAckRedeliveryDelay() > 0) {
                builder.negativeAckRedeliveryDelay(defaultConsumerConfig.getNegativeAckRedeliveryDelay(),
                        TimeUnit.MILLISECONDS);
            }
            // WI21 (D6 layer two): native dead-letter policy. See
            // PulsarConsumerConfig.deadLetterMaxRedeliverCount for the diversion semantics —
            // diversion is ack-triggered and Shared-subscription-scoped, so an incompatible
            // subscription type gets a one-time warning instead of a silent no-op.
            if (defaultConsumerConfig.getDeadLetterMaxRedeliverCount() > 0) {
                org.apache.pulsar.client.api.DeadLetterPolicy.DeadLetterPolicyBuilder policy =
                        org.apache.pulsar.client.api.DeadLetterPolicy.builder()
                                .maxRedeliverCount(defaultConsumerConfig.getDeadLetterMaxRedeliverCount());
                if (defaultConsumerConfig.getDeadLetterTopic() != null) {
                    policy.deadLetterTopic(defaultConsumerConfig.getDeadLetterTopic());
                }
                builder.deadLetterPolicy(policy.build());
                // WI27: the resolver is the single source — a mode-only LOAD_BALANCED/
                // ORDERED_BY_KEY configuration is correctly diagnosed as Shared/Key_Shared
                // (no false "will not trigger" warning).
                org.apache.pulsar.client.api.SubscriptionType type = options != null
                        ? resolveEffectivePulsarType(options) : null;
                if (type != org.apache.pulsar.client.api.SubscriptionType.Shared
                        && type != org.apache.pulsar.client.api.SubscriptionType.Key_Shared) {
                    io.nop.api.core.message.MessageOptionsValidator.warnOnce("pulsar", "dlq-subscription-type",
                            "deadLetterMaxRedeliverCount is configured but the broker tracks redelivery "
                                    + "counts only for Shared/Key_Shared subscriptions (got: " + type
                                    + "); the dead-letter policy will not trigger");
                }
            }
        }

        ExecutorService executor = null;
        Consumer<?> consumer = null;
        try {
            // WI26 (F-55): narrow wrap — subscription creation failures carry the consumer-
            // create code; the outer catch keeps its resource-cleanup + adapt semantics
            // (post-create failures like the seek must not be mislabeled).
            try {
                consumer = builder.subscribe();
            } catch (PulsarClientException e) {
                throw new NopException(PulsarErrors.ERR_PULSAR_CONSUMER_CREATE_FAILED)
                        .description("Failed to create Pulsar consumer")
                        .cause(e);
            }

            // D3: ALWAYS_SEEK_TO_END and seekToTime resolve with a synchronous seek right
            // after subscribe (the client is connected at this point). A seek failure tears
            // down the freshly created consumer before the error reaches the caller.
            if (options != null) {
                if (options.getSeekMode() == io.nop.api.core.message.SeekMode.ALWAYS_SEEK_TO_END) {
                    consumer.seek(org.apache.pulsar.client.api.MessageId.latest);
                }
                if (options.getSeekToTime() > 0) {
                    consumer.seek(options.getSeekToTime());
                }
            }

            // F-24 (WI19): consume threads are daemon and named. identity = subscribeName
            // (fallback: topic — a missing subscribeName being accepted at all is F-29/WI29);
            // NamedThreadFactory's per-instance pool suffix keeps fan-out and multi-subscribe
            // threads distinguishable (no more pool-N-thread-1).
            String identity = options.getSubscribeName();
            executor = Executors.newSingleThreadExecutor(
                    new NamedThreadFactory("nop-msg-pulsar-consume-" + identity, true));
            // WI22: the backoff base comes from the consumer config (0-safe fallback above).
            long backoffMs = defaultConsumerConfig != null
                    ? defaultConsumerConfig.getConsumeErrorBackoffMs()
                    : PulsarConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS;
            PulsarConsumeTask task = new PulsarConsumeTask(this, executor,
                    (Consumer<Object>) consumer, subConfig, backoffMs);
            // 4.1-MA-01（plan 2558 裁定 A）：wire the bounded consume-await timeout.
            task.setConsumeAwaitTimeoutMs(consumeAwaitTimeoutMs());
            task.start();
            PulsarMessageSubscription subscription = new PulsarMessageSubscription(consumer, executor, task);
            subscriptions.add(subscription);
            return subscription;
        } catch (Exception e) {
            // The consumer (broker connection + buffered unacked messages) must not leak
            // when subscribe or the post-subscribe seek fails (WI8 closure audit Major-1).
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception closeError) {
                    e.addSuppressed(closeError);
                }
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            throw NopException.adapt(e);
        }
    }

    /**
     * D3: INIT_SEEK modes map to Pulsar's subscription initial position, which applies only
     * when the subscription cursor does not exist yet — precisely the SeekMode javadoc
     * ("first consumption from the beginning/end, afterwards resume from the last
     * position") and symmetric with the Kafka committed-offset gate (WI7).
     */
    private void applyInitialPosition(ConsumerBuilder<?> builder,
                                      io.nop.api.core.message.SeekMode mode) {
        if (mode == io.nop.api.core.message.SeekMode.INIT_SEEK_TO_BEGIN) {
            builder.subscriptionInitialPosition(org.apache.pulsar.client.api.SubscriptionInitialPosition.Earliest);
        } else if (mode == io.nop.api.core.message.SeekMode.INIT_SEEK_TO_END) {
            builder.subscriptionInitialPosition(org.apache.pulsar.client.api.SubscriptionInitialPosition.Latest);
        }
    }

    static org.apache.pulsar.client.api.SubscriptionType toPulsarSubscriptionType(
            io.nop.api.core.message.SubscriptionType type) {
        switch (type) {
            case Shared:
                return org.apache.pulsar.client.api.SubscriptionType.Shared;
            case Failover:
                return org.apache.pulsar.client.api.SubscriptionType.Failover;
            case Key_Shared:
                return org.apache.pulsar.client.api.SubscriptionType.Key_Shared;
            default:
                return org.apache.pulsar.client.api.SubscriptionType.Exclusive;
        }
    }

    class PulsarMessageSubscription extends Cancellable implements IMessageSubscription {
        private final Consumer<?> consumer;
        private final ExecutorService executor;
        private final PulsarConsumeTask task;
        private volatile boolean suspended;

        public PulsarMessageSubscription(Consumer<?> consumer, ExecutorService executor, PulsarConsumeTask task) {
            this.consumer = consumer;
            this.executor = executor;
            this.task = task;
            this.appendOnCancel(this::doOnCancel);
        }

        private void doOnCancel(String reason) {
            task.stop();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        LOG.warn("nop.message.pulsar.cancel-consume-thread-stuck");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            // F-26 (WI20): close strictly AFTER the consume thread terminated (or was
            // force-interrupted) — close can no longer race receive/batch processing. A
            // thread stuck in a blocking client call beyond the budget degrades to a
            // best-effort WARN'd close (residual window: backend-semantics-design.md 3.8.1).
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.cancel-close-consumer-failed", e);
            }
            subscriptions.remove(this);
        }

        @Override
        public void cancel() {
            super.cancel();
        }

        @Override
        public void suspend() {
            suspended = true;
            consumer.pause();
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public void resume() {
            consumer.resume();
            suspended = false;
        }
    }
}
