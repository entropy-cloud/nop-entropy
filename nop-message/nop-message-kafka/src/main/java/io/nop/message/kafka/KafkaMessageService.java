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
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.MultiMessageSubscription;
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
 * <p>Lifecycle methods are {@code init()} / {@code destroy()} (NOT close), matching
 * {@code PulsarMessageService}.
 */
public class KafkaMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(KafkaMessageService.class);

    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.BATCH_CONSUME,
            MessageCapability.SEEK, MessageCapability.ACK_REPLY, MessageCapability.ENVELOPE_METADATA);

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
     * Validates {@code bootstrapServers} non-empty and initializes the producer. Throws
     * {@link NopException} (not a bare RuntimeException) when misconfigured.
     */
    public void init() {
        if (config == null || config.getBootstrapServers() == null || config.getBootstrapServers().isEmpty()) {
            throw error(ERR_BOOTSTRAP_SERVERS_NOT_CONFIGURED,
                    "KafkaClientConfig.bootstrapServers is not configured");
        }
        producer = new KafkaProducer<>(buildProducerProps(), new StringSerializer(), new StringSerializer());
        initialized = true;
        LOG.info("nop.message.kafka.initialized:bootstrapServers={}", config.getBootstrapServers());
    }

    /**
     * Graceful shutdown: cancels every subscription (stops poll threads + closes
     * consumers), then closes the producer. Method name is {@code destroy} (NOT close),
     * matching {@code PulsarMessageService.destroy}.
     */
    public void destroy() {
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
        prod.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(KafkaHelper.adapt(exception));
            } else {
                future.complete(null);
            }
        });
        return future;
    }

    /**
     * Serializes an {@link ApiMessage}'s data to the STRING wire value. Mirrors
     * {@code PulsarHelper._buildPulsarMessage} passing {@code message.getData()} to a
     * STRING-schema producer: a String data is used directly, any other type is
     * JSON-encoded (so a non-String data does not crash the StringSerializer).
     */
    static String toWireValue(ApiMessage message) {
        Object data = message.getData();
        if (data == null) {
            return null;
        }
        if (data instanceof String) {
            return (String) data;
        }
        return KafkaHelper.encodeValue(data);
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
                buildConsumerProps(groupId), new StringDeserializer(), new StringDeserializer());
        java.util.List<String> topics = java.util.Collections.singletonList(subConfig.getTopic());
        KafkaConsumeTask task = newTask(kConsumer, subConfig);
        kConsumer.subscribe(topics, task.rebalanceListener());

        long pollTimeout = options != null && options.getBatchReceiveTimeout() > 0
                ? options.getBatchReceiveTimeout() : 1000L;

        warnIfAutoCommit();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consume-" + groupId);
            t.setDaemon(true);
            return t;
        });
        task.bindExecutor(executor, pollTimeout);
        task.start();

        KafkaMessageSubscription subscription = new KafkaMessageSubscription(kConsumer, executor, task);
        subscriptions.add(subscription);
        return subscription;
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

    /** Task factory: kept separate so doSubscribe can register the rebalance listener before start. */
    private KafkaConsumeTask newTask(Consumer<String, String> consumer, MessageSubscriptionConfig subConfig) {
        return new KafkaConsumeTask(this, consumer, subConfig,
                KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS);
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
        private volatile boolean suspended;
        private volatile boolean cancelled;

        public KafkaMessageSubscription(Consumer<String, String> consumer,
                                        ExecutorService executor, KafkaConsumeTask task) {
            this.consumer = consumer;
            this.executor = executor;
            this.task = task;
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            task.stop();
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.error("nop.message.kafka.cancel-close-consumer-failed", e);
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            subscriptions.remove(this);
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
            try {
                consumer.pause(consumer.assignment());
            } catch (Exception e) {
                LOG.error("nop.message.kafka.suspend-failed", e);
            }
        }

        @Override
        public void resume() {
            try {
                consumer.resume(consumer.assignment());
            } catch (Exception e) {
                LOG.error("nop.message.kafka.resume-failed", e);
            }
            suspended = false;
        }
    }
}
