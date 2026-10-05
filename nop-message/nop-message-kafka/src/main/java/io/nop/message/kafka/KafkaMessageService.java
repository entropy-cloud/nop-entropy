/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageErrors;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.MessageValueCodec;
import io.nop.api.core.message.MultiMessageSubscription;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.commons.service.LifeCycleSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.nop.message.kafka.KafkaErrors.ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED;
import static io.nop.message.kafka.KafkaErrors.ERR_GROUP_ID_NOT_CONFIGURED;

/**
 * Kafka backend for {@link IMessageService}, mirroring {@code PulsarMessageService}.
 *
 * <p>Provides at-least-once {@code sendAsync} via a {@link KafkaProducer} (STRING
 * serializer — wire format is a JSON string, matching the Pulsar STRING-schema backend
 * so {@code PulsarStringWireCodec}'s logic applies) and {@code subscribe} via a
 * {@link KafkaConsumer} poll loop driven by {@link KafkaConsumeTask}, which handles the
 * 5 documented {@code IMessageConsumer.onMessage} return-value semantics.
 *
 * <p>Lifecycle runs on the platform {@link LifeCycleSupport} state machine (WI19): the
 * public {@code init()} / {@code destroy()} pair required by the beans wiring delegates to
 * {@code start()} / {@code stop()}, matching {@code PulsarMessageService}.
 */
public class KafkaMessageService extends LifeCycleSupport implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(KafkaMessageService.class);

    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.BATCH_CONSUME,
            MessageCapability.SEEK, MessageCapability.ACK_REPLY, MessageCapability.ENVELOPE_METADATA,
            MessageCapability.SEND_TIMEOUT);

    /** Kafka has no native delayed delivery: warn once, then execute the declared ignore. */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(MessageCapability.DELAYED_SEND);

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }

    KafkaClientConfig config;
    private KafkaProducerConfig defaultProducerConfig;
    private KafkaConsumerConfig defaultConsumerConfig;

    KafkaProducer<String, String> producer;
    private Queue<KafkaMessageSubscription> subscriptions = new ConcurrentLinkedQueue<>();
    volatile boolean initialized = false;

    public void setConfig(KafkaClientConfig config) {
        this.config = config;
    }

    public void setDefaultProducerConfig(KafkaProducerConfig defaultProducerConfig) {
        this.defaultProducerConfig = defaultProducerConfig;
    }

    public void setDefaultConsumerConfig(KafkaConsumerConfig defaultConsumerConfig) {
        this.defaultConsumerConfig = defaultConsumerConfig;
    }

    /**
     * Beans-wiring entry point ({@code init-method="init"}), delegating to the
     * {@link LifeCycleSupport} state machine. A missing bootstrapServers rejects with
     * {@code ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED} exactly as before (NopException passes
     * through {@code LifeCycleSupport.start()}'s rethrow unchanged).
     */
    public void init() {
        start();
    }

    @Override
    protected void doStart() {
        if (config == null || config.getBootstrapServers() == null || config.getBootstrapServers().isEmpty()) {
            throw error(ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED,
                    "KafkaClientConfig.bootstrapServers is not configured");
        }
        producer = new KafkaProducer<>(buildProducerProps(), new StringSerializer(), new StringSerializer());
        initialized = true;
        LOG.info("nop.message.kafka.initialized:bootstrapServers={}", config.getBootstrapServers());
    }

    /** Beans-wiring entry point ({@code destroy-method="destroy"}). */
    public void destroy() {
        stop();
    }

    /**
     * Graceful shutdown: cancels every subscription (stops poll threads + closes
     * consumers), then closes the producer. Idempotent via the
     * {@link LifeCycleSupport} stop state machine.
     */
    @Override
    protected void doStop() {
        for (KafkaMessageSubscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                LOG.error("nop.message.kafka.close-subscription-failed", e);
            }
        }
        subscriptions.clear();

        if (producer != null) {
            try {
                long timeoutMs = defaultProducerConfig != null
                        ? defaultProducerConfig.getSendTimeoutMs() : 120000L;
                producer.close(java.time.Duration.ofMillis(timeoutMs));
            } catch (Exception e) {
                LOG.error("nop.message.kafka.close-producer-failed", e);
            }
            producer = null;
        }
        initialized = false;
        LOG.info("nop.message.kafka.destroyed");
    }

    Properties buildProducerProps() {
        Properties props = new Properties();
        // F-28 (WI13): extraProps are applied FIRST — keys written by the code below are
        // managed keys and cannot be overridden by extraProps; extraProps remain effective
        // for keys the code does not manage (e.g. SASL/SSL).
        if (config.getExtraProps() != null) {
            props.putAll(config.getExtraProps());
        }
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        if (config.getClientId() != null) {
            props.put(ProducerConfig.CLIENT_ID_CONFIG, config.getClientId());
        }
        if (defaultProducerConfig != null) {
            if (defaultProducerConfig.getAcks() != null) {
                props.put(ProducerConfig.ACKS_CONFIG, defaultProducerConfig.getAcks());
            }
            props.put(ProducerConfig.RETRIES_CONFIG, defaultProducerConfig.getRetries());
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, defaultProducerConfig.getBatchSize());
            props.put(ProducerConfig.LINGER_MS_CONFIG, defaultProducerConfig.getLingerMs());
            props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, defaultProducerConfig.getBufferMemory());
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, defaultProducerConfig.getMaxBlockMs());
        }
        return props;
    }

    Properties buildConsumerProps(String groupId) {
        return buildConsumerProps(groupId, null);
    }

    /**
     * WI25 (F-17): a per-subscribe {@code batchReceiveCount > 0} overrides the global
     * {@code maxPollRecords} — precedence chain: extraProps &lt; managed keys &lt;
     * per-subscribe option. {@code null} options keeps the global default.
     */
    Properties buildConsumerProps(String groupId, MessageSubscribeOptions options) {
        Properties props = new Properties();
        // F-28 (WI13): extraProps are applied FIRST — the resolved consumer-group identity
        // and all managed keys below cannot be overridden by extraProps.
        if (config.getExtraProps() != null) {
            props.putAll(config.getExtraProps());
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        if (config.getClientId() != null) {
            props.put(ConsumerConfig.CLIENT_ID_CONFIG, config.getClientId());
        }
        if (defaultConsumerConfig != null) {
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                    defaultConsumerConfig.isEnableAutoCommit());
            if (defaultConsumerConfig.getAutoOffsetReset() != null) {
                props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        defaultConsumerConfig.getAutoOffsetReset());
            }
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, defaultConsumerConfig.getMaxPollRecords());
            props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, defaultConsumerConfig.getFetchMinBytes());
            props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, defaultConsumerConfig.getFetchMaxWaitMs());
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, defaultConsumerConfig.getSessionTimeoutMs());
            props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
                    defaultConsumerConfig.getHeartbeatIntervalMs());
            props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                    defaultConsumerConfig.getMaxPollIntervalMs());
        }
        if (options != null && options.getBatchReceiveCount() > 0) {
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, options.getBatchReceiveCount());
        }
        return props;
    }

    KafkaProducer<String, String> getProducer() {
        if (!initialized || producer == null) {
            throw error(KafkaErrors.ERR_KAFKA_PRODUCER_CREATE_FAILED,
                    "KafkaMessageService is not initialized");
        }
        return producer;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "kafka", WARN_ONCE);
        if (options != null && options.getCancelToken() != null && options.getCancelToken().isCancelled()) {
            // WI15 (F-45): pre-send cancellation check; rejected future per the
            // MessageRpcClient.callAsync precedent (never a synchronous throw).
            java.util.concurrent.CompletableFuture<Void> rejected = new java.util.concurrent.CompletableFuture<>();
            rejected.completeExceptionally(new NopException(MessageErrors.ERR_SEND_CANCELLED)
                    .description("send cancelled before dispatch:topic=" + topic));
            return rejected;
        }
        KafkaProducer<String, String> prod = getProducer();

        String key;
        String value;
        Headers headers = null;
        if (message instanceof ApiMessage) {
            ApiMessage apiMessage = (ApiMessage) message;
            key = KafkaHelper.extractKey(apiMessage);
            value = toWireValue(apiMessage);
            headers = new org.apache.kafka.common.header.internals.RecordHeaders();
            KafkaHelper.copyHeaders(headers, apiMessage);
        } else {
            key = null;
            value = message == null ? null : message.toString();
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value, headers);
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            prod.send(record, (metadata, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(KafkaHelper.adapt(exception));
                } else {
                    future.complete(null);
                }
            });
        } catch (Exception e) {
            // F-39: synchronous send failures (producer closed, interrupted, unexpected
            // runtime errors) must not escape sendAsync — the contract is a CompletionStage.
            // Note: max.block.ms exhaustion is NOT this path (it routes through the callback).
            // Kafka's InterruptException extends KafkaException (not JDK InterruptedException).
            if (e instanceof InterruptedException
                    || e instanceof org.apache.kafka.common.errors.InterruptException) {
                Thread.currentThread().interrupt();
            }
            future.completeExceptionally(KafkaHelper.adapt(e));
        }
        // WI15 (F-45): upper bound on the returned future. Timeout leaves the underlying
        // send running (fire-and-forget) — the message may still be delivered. Bare
        // java.util.concurrent.TimeoutException is the contract (JDK orTimeout semantics).
        long sendTimeoutMs = resolveSendTimeoutMs(options);
        if (sendTimeoutMs > 0) {
            future.orTimeout(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return future;
    }

    private long resolveSendTimeoutMs(MessageSendOptions options) {
        if (options != null && options.getSendTimeout() > 0) {
            return options.getSendTimeout();
        }
        if (defaultProducerConfig != null) {
            return defaultProducerConfig.getSendTimeoutMs();
        }
        return 0L; // no configured bound
    }

    /**
     * Serializes an {@link ApiMessage}'s data to the STRING wire value. Mirrors
     * {@code PulsarHelper._buildPulsarMessage} passing {@code message.getData()} to a
     * STRING-schema producer: a String data is used directly, any other type is
     * JSON-encoded (so a non-String data does not crash the StringSerializer).
     */
    static String toWireValue(ApiMessage message) {
        return MessageValueCodec.encodeValue(message.getData());
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (options == null) {
            options = new MessageSubscribeOptions();
        }
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "kafka", WARN_ONCE);
        if (options.getSubscriptionType() != null) {
            // Kafka has no direct equivalent of Pulsar's SubscriptionType (group-based
            // partition assignment covers Shared/Failover). Warn once per JVM, never
            // per call (F-53), and never silently.
            MessageOptionsValidator.warnOnce("kafka", "subscriptionType",
                    "subscriptionType " + options.getSubscriptionType() + " has no Kafka equivalent and is ignored");
        }

        int concurrency = options.getConcurrency();
        if (concurrency <= 1) {
            return doSubscribe(new MessageSubscriptionConfig(topic, listener, options));
        }
        List<IMessageSubscription> subs = new ArrayList<>(concurrency);
        MultiMessageSubscription ret = new MultiMessageSubscription(subs);
        MessageSubscriptionConfig cfg = new MessageSubscriptionConfig(topic, listener, options);
        try {
            for (int i = 0; i < concurrency; i++) {
                subs.add(doSubscribe(cfg));
            }
        } catch (Exception e) {
            ret.cancel();
            throw NopException.adapt(e);
        }
        return ret;
    }

    IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
        MessageSubscribeOptions options = subConfig.getOptions();
        String groupId = resolveGroupId(options);
        if (groupId == null || groupId.isEmpty()) {
            throw error(ERR_GROUP_ID_NOT_CONFIGURED,
                    "MessageSubscribeOptions.subscribeName is required by the Kafka backend");
        }
        // Validated before any consumer is constructed: the typed rejection must reach the
        // subscribe caller on this thread, and creating a consumer first would leak its
        // network threads on the error path (MSG-F-02 seek facet, WI7).
        if (options != null && options.getSeekToMessage() != null) {
            throw error(KafkaErrors.ERR_KAFKA_SEEK_NOT_SUPPORTED,
                    "seekToMessage is not supported by the Kafka backend");
        }

        Consumer<String, String> kConsumer = createConsumer(
                buildConsumerProps(groupId, options), new StringDeserializer(), new StringDeserializer());
        java.util.List<String> topics = java.util.Collections.singletonList(subConfig.getTopic());
        KafkaConsumeTask task = newTask(kConsumer, subConfig);
        kConsumer.subscribe(topics, task.rebalanceListener());

        long pollTimeout = options != null && options.getBatchReceiveTimeout() > 0
                ? options.getBatchReceiveTimeout() : 1000L;

        warnIfAutoCommit();

        // F-24 (WI19): consume threads are daemon and named; NamedThreadFactory's
        // per-subscription pool suffix makes fan-out members of the same group
        // distinguishable (the previous inline factory named all of them identically).
        NamedThreadFactory threadFactory = consumeThreadFactory(groupId);
        ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        task.bindExecutor(executor, pollTimeout);
        task.start();

        KafkaMessageSubscription subscription = new KafkaMessageSubscription(kConsumer, executor, task);
        subscriptions.add(subscription);
        return subscription;
    }

    /**
     * F-24 (WI19): consume-thread factory for one subscription — daemon, name prefix
     * {@code nop-msg-kafka-consume-<groupId>}, with NamedThreadFactory's
     * {@code -<pool>-<thread>} suffix distinguishing fan-out members. Package-private for
     * the naming tests.
     */
    NamedThreadFactory consumeThreadFactory(String groupId) {
        return new NamedThreadFactory("nop-msg-kafka-consume-" + groupId, true);
    }

    /**
     * F-58 disposition: with enable.auto.commit=true, the broker-side auto-committer
     * commits positions on its own schedule and does not respect this task's converged
     * commit boundaries — warn once per JVM. The user's explicit setting is respected, not
     * rewritten. Note: covers the config-bean path only; an extraProps override of
     * enable.auto.commit is not detected here (and, since WI13, cannot override managed keys).
     */
    void warnIfAutoCommit() {
        if (defaultConsumerConfig != null && defaultConsumerConfig.isEnableAutoCommit()) {
            io.nop.api.core.message.MessageOptionsValidator.warnOnce("kafka", "auto-commit",
                    "enable.auto.commit=true bypasses the converged commit boundaries of the "
                            + "manual-commit consume loop");
        }
    }

    /**
     * Task factory: kept separate so doSubscribe can register the rebalance listener before
     * start. WI22: the backoff base comes from the consumer config (protected so tests can
     * observe the wiring); maxConsumeAttempts/backoffMax are read per-failure through the
     * package-private accessors below.
     */
    protected KafkaConsumeTask newTask(Consumer<String, String> consumer, MessageSubscriptionConfig subConfig) {
        long backoffMs = defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeErrorBackoffMs()
                : KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS;
        return new KafkaConsumeTask(this, consumer, subConfig, backoffMs);
    }

    /**
     * WI22: retry budget before the failure endpoint ({@code 0} = unlimited). Falls back to
     * the module default — never to 0 — so a directly constructed service keeps production
     * semantics.
     */
    int maxConsumeAttempts() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getMaxConsumeAttempts()
                : KafkaConsumeTask.DEFAULT_MAX_CONSUME_ATTEMPTS;
    }

    /** WI22: upper bound of the escalating error backoff. */
    long consumeErrorBackoffMaxMs() {
        return defaultConsumerConfig != null
                ? defaultConsumerConfig.getConsumeErrorBackoffMaxMs()
                : KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MAX_MS;
    }

    /** Injection seam for tests: lets a test subclass supply a mock/fake consumer. */
    protected Consumer<String, String> createConsumer(Properties props,
                                                      StringDeserializer keyDeserializer,
                                                      StringDeserializer valueDeserializer) {
        return new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
    }

    /**
     * D7 (WI13 / MSG-F-12): the subscribeName is the primary consumer-group identity. A
     * globally configured groupId is demoted to a namespace prefix — the effective identity
     * is {@code <groupId>.<subscribeName>} — so heterogeneous subscriptions can never share
     * one assignment domain. A missing subscribeName is a typed rejection even when a
     * global groupId exists (breaking by adjudication; previously the global silently
     * masked the subscribeName). extraProps cannot override the resolved identity (F-28).
     */
    String resolveGroupId(MessageSubscribeOptions options) {
        String subscribeName = options != null ? options.getSubscribeName() : null;
        if (subscribeName == null || subscribeName.isEmpty()) {
            throw error(ERR_GROUP_ID_NOT_CONFIGURED,
                    "MessageSubscribeOptions.subscribeName is required by the Kafka backend "
                            + "(a global groupId only acts as a namespace prefix)");
        }
        String global = defaultConsumerConfig != null ? defaultConsumerConfig.getGroupId() : null;
        if (global != null && !global.isEmpty()) {
            return global + "." + subscribeName;
        }
        return subscribeName;
    }

    /**
     * WI21 (D6 layer two): resolves the application-level dead-letter topic for a consume
     * topic. {@code null} = DLQ republish disabled (TerminateConsume drops with WARN).
     * Package-private: the consume task reads it through its service reference.
     */
    String resolveDeadLetterTopic(String topic) {
        if (defaultConsumerConfig == null) {
            return null;
        }
        String dlq = defaultConsumerConfig.getDeadLetterTopic();
        // Self-loop guard: republishing a topic into its own dead-letter topic would make a
        // TerminateConsume redeliver forever (the DLQ copy is itself a live message). The
        // config error is surfaced once; the terminate semantics degrade to drop-with-WARN.
        if (dlq != null && dlq.equals(topic)) {
            io.nop.api.core.message.MessageOptionsValidator.warnOnce("kafka", "dlq-self-loop",
                    "KafkaConsumerConfig.deadLetterTopic must differ from the consumed topic '" + topic
                            + "'; TerminateConsume messages would loop forever and are dropped instead");
            return null;
        }
        return dlq;
    }

    NopException error(io.nop.api.core.exceptions.ErrorCode code, String message) {
        return new NopException(code).description(message);
    }

    /**
     * {@link IMessageSubscription} backed by a {@link KafkaConsumer}. Mirrors
     * {@code PulsarMessageSubscription}: suspend maps to {@link KafkaConsumer#pause}, resume
     * to {@link KafkaConsumer#resume}, cancel stops the poll task and closes the consumer.
     */
    public class KafkaMessageSubscription implements IMessageSubscription {
        private final Consumer<String, String> consumer;
        private final ExecutorService executor;
        private final KafkaConsumeTask task;
        // WI20 (F-25): atomic guard — concurrent cancels must run the cleanup body once.
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean suspended;

        public KafkaMessageSubscription(Consumer<String, String> consumer,
                                        ExecutorService executor, KafkaConsumeTask task) {
            this.consumer = consumer;
            this.executor = executor;
            this.task = task;
        }

        /**
         * WI20 (F-26): the consume task is stopped first, the executor is then awaited to
         * termination (5s; on timeout forced via shutdownNow and awaited once more for 1s),
         * and only AFTER the poll thread has exited is the consumer closed — close can no
         * longer race poll/commit on the consume thread. If the thread is still stuck in a
         * blocking consumer call beyond that budget, the close degrades to a best-effort
         * WARN'd fallback (residual window declared in backend-semantics-design.md 3.8.1).
         */
        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            task.stop();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        LOG.warn("nop.message.kafka.cancel-consume-thread-stuck");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.error("nop.message.kafka.cancel-close-consumer-failed", e);
            }
            subscriptions.remove(this);
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        /**
         * WI20 (F-26): only records the desired state — the consume thread applies
         * {@code pause} at the top of the next poll round (and re-applies it to freshly
         * assigned partitions), so the call never touches the consumer cross-thread.
         */
        @Override
        public void suspend() {
            suspended = true;
            task.setSuspended(true);
        }

        @Override
        public void resume() {
            task.setSuspended(false);
            suspended = false;
        }
    }
}
