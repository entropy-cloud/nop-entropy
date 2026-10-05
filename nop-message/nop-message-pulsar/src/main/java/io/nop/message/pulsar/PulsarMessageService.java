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
import io.nop.commons.lang.impl.Cancellable;
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

public class PulsarMessageService implements IMessageService {
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
    private PulsarClient client;
    private Map<String, Schema<?>> topicSchemas = new ConcurrentHashMap<>();
    private Map<String, Producer<?>> producers = new ConcurrentHashMap<>();
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

    public void setTopicSchemas(Map<String, Schema<?>> topicSchemas) {
        if (topicSchemas != null) {
            this.topicSchemas.putAll(topicSchemas);
        }
    }

    public void init() throws PulsarClientException {
        if (config == null || config.getServiceUrl() == null) {
            throw new NopException(ERR_SERVICE_URL_NOT_CONFIGURED);
        }
        client = PulsarClient.builder()
                .serviceUrl(config.getServiceUrl())
                .build();

        LOG.info("nop.message.pulsar.initialized:serviceUrl={}", config.getServiceUrl());
    }

    public void destroy() {
        for (Producer<?> producer : producers.values()) {
            try {
                producer.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.close-producer-failed", e);
            }
        }
        producers.clear();

        for (PulsarMessageSubscription subscription : subscriptions) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.close-subscription-failed", e);
            }
        }
        subscriptions.clear();

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.close-client-failed", e);
            }
            client = null;
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

    Producer<?> getProducer(String topic) {
        Producer<?> producer = producers.get(topic);
        if (producer != null)
            return producer;
        // MSG-NEW-03 (WI9): a topicless producer is invalid in Pulsar 2.8 — unknown topics
        // get an on-demand producer with the default STRING schema instead of falling back
        // to a shared topicless defaultProducer.
        Schema<?> schema = topicSchemas.getOrDefault(topic, Schema.STRING);
        return producers.computeIfAbsent(topic, k -> buildProducer(k, schema));
    }

    Producer<?> buildProducer(String topic, Schema<?> schema) {
        try {
            ProducerBuilder<?> builder = client.newProducer(schema).topic(topic);
            if (defaultProducerConfig != null) {
                builder.enableBatching(defaultProducerConfig.isBatchingEnabled());
                builder.batchingMaxMessages(defaultProducerConfig.getBatchMaxMessages());
                builder.sendTimeout(defaultProducerConfig.getSendTimeout(), TimeUnit.MILLISECONDS);
            }
            return builder.create();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String getAckTopic(String topic) {
        return PulsarHelper.deriveAckTopic(topic);
    }

    private CompletableFuture<Void> sendPulsarAsync(String topic, Object message, MessageSendOptions options) {
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

        int concurrency = options.getConcurrency();
        if (concurrency <= 1) {
            return doSubscribe(new MessageSubscriptionConfig(topic, listener, options));
        } else {
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
    }

    /** Injection seam for tests (mirrors the Kafka createConsumer factory, WI7). */
    protected ConsumerBuilder<?> newConsumerBuilder(Schema<?> schema) {
        return client.newConsumer(schema);
    }

    IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
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
            if (options.getSubscriptionType() != null) {
                builder.subscriptionType(toPulsarSubscriptionType(options.getSubscriptionType()));
            }
            applyInitialPosition(builder, options.getSeekMode());
        }

        if (defaultConsumerConfig != null) {
            if (defaultConsumerConfig.getAckTimeout() > 0) {
                builder.ackTimeout(defaultConsumerConfig.getAckTimeout(), TimeUnit.MILLISECONDS);
            }
            if (defaultConsumerConfig.getNegativeAckRedeliveryDelay() > 0) {
                builder.negativeAckRedeliveryDelay(defaultConsumerConfig.getNegativeAckRedeliveryDelay(),
                        TimeUnit.MILLISECONDS);
            }
        }

        ExecutorService executor = null;
        Consumer<?> consumer = null;
        try {
            consumer = builder.subscribe();

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

            executor = Executors.newSingleThreadExecutor();
            PulsarConsumeTask task = new PulsarConsumeTask(this, executor,
                    (Consumer<Object>) consumer, subConfig);
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
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.cancel-close-consumer-failed", e);
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
