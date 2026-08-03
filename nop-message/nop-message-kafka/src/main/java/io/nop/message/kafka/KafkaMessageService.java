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
import java.util.List;
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
        if (config.getExtraProps() != null) {
            props.putAll(config.getExtraProps());
        }
        return props;
    }

    Properties buildConsumerProps(String groupId) {
        Properties props = new Properties();
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
        if (config.getExtraProps() != null) {
            props.putAll(config.getExtraProps());
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

        if (KafkaHelper.resolveDelay(options) > 0) {
            // Kafka has no native delayed delivery (unlike Pulsar deliverAfter). Warn so
            // the caller knows the delay is ignored — NOT a silent skip (plan guide #24).
            LOG.warn("nop.message.kafka.delay-ignored:topic={},delayMs={}", topic,
                    KafkaHelper.resolveDelay(options));
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
        if (options.getSubscriptionType() != null) {
            // Kafka has no direct equivalent of Pulsar's SubscriptionType (group-based
            // partition assignment covers Shared/Failover). Warn rather than silently
            // ignore (plan guide #24).
            LOG.warn("nop.message.kafka.subscription-type-ignored:type={}", options.getSubscriptionType());
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
                    "KafkaConsumerConfig.groupId / MessageSubscribeOptions.subscribeName is not configured");
        }

        KafkaConsumer<String, String> kConsumer = new KafkaConsumer<>(
                buildConsumerProps(groupId), new StringDeserializer(), new StringDeserializer());
        java.util.List<String> topics = java.util.Collections.singletonList(subConfig.getTopic());
        kConsumer.subscribe(topics);

        long pollTimeout = options != null && options.getBatchReceiveTimeout() > 0
                ? options.getBatchReceiveTimeout() : 1000L;

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consume-" + groupId);
            t.setDaemon(true);
            return t;
        });
        KafkaConsumeTask task = new KafkaConsumeTask(this, executor, kConsumer, subConfig,
                KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS, pollTimeout);
        task.start();

        KafkaMessageSubscription subscription = new KafkaMessageSubscription(kConsumer, executor, task);
        subscriptions.add(subscription);
        return subscription;
    }

    String resolveGroupId(MessageSubscribeOptions options) {
        if (defaultConsumerConfig != null && defaultConsumerConfig.getGroupId() != null
                && !defaultConsumerConfig.getGroupId().isEmpty()) {
            return defaultConsumerConfig.getGroupId();
        }
        if (options != null && options.getSubscribeName() != null) {
            return options.getSubscribeName();
        }
        return null;
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
