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
import org.apache.pulsar.client.api.transaction.Transaction;
import org.apache.pulsar.client.api.transaction.TransactionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    private PulsarClientConfig config;
    private PulsarProducerConfig defaultProducerConfig;
    private PulsarConsumerConfig defaultConsumerConfig;
    private PulsarClient client;
    private Map<String, Schema<?>> topicSchemas = new ConcurrentHashMap<>();
    private Map<String, Producer<?>> producers = new ConcurrentHashMap<>();
    private Producer<?> defaultProducer;
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

        defaultProducer = buildProducer(Schema.STRING);

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

        if (defaultProducer != null) {
            try {
                defaultProducer.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.close-default-producer-failed", e);
            }
            defaultProducer = null;
        }

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

    Producer<?> getProducer(String topic) {
        Producer<?> producer = producers.get(topic);
        if (producer != null)
            return producer;

        Schema<?> schema = topicSchemas.get(topic);
        if (schema != null) {
            return producers.computeIfAbsent(topic, k -> buildProducer(schema));
        } else {
            return defaultProducer;
        }
    }

    Producer<?> buildProducer(Schema<?> schema) {
        try {
            ProducerBuilder<?> builder = client.newProducer(schema);
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

    Transaction newTransaction(long timeout) throws PulsarClientException {
        TransactionBuilder builder = client.newTransaction();
        if (timeout > 0)
            builder.withTransactionTimeout(timeout, TimeUnit.MILLISECONDS);

        return FutureHelper.syncGet(builder.build());
    }

    CompletableFuture<Void> sendAsync(Transaction txn,
                                       String topic, Object message,
                                       MessageSendOptions options) {
        Producer<?> producer = getProducer(topic);
        TypedMessageBuilder<?> builder = txn == null ?
                producer.newMessage() : producer.newMessage(txn);
        PulsarHelper.buildPulsarMessage(builder, message, options);

        return builder.sendAsync().thenAccept(ret -> {
        });
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        CompletableFuture<Void> future = sendAsync(null, topic, message, options);
        return future;
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (options == null)
            options = new MessageSubscribeOptions();

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

    IMessageSubscription doSubscribe(MessageSubscriptionConfig subConfig) {
        Schema<?> schema = topicSchemas.get(subConfig.getTopic());
        if (schema == null) {
            schema = Schema.STRING;
        }
        ConsumerBuilder<?> builder = client.newConsumer(schema);
        builder.topic(subConfig.getTopic());

        MessageSubscribeOptions options = subConfig.getOptions();
        if (options != null) {
            if (options.getSubscribeName() != null) {
                builder.subscriptionName(options.getSubscribeName());
            }
            if (options.getSubscriptionType() != null) {
                builder.subscriptionType(toPulsarSubscriptionType(options.getSubscriptionType()));
            }
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

        try {
            Consumer<?> consumer = builder.subscribe();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            PulsarConsumeTask task = new PulsarConsumeTask(this, executor,
                    (Consumer<Object>) consumer, subConfig);
            task.start();
            PulsarMessageSubscription subscription = new PulsarMessageSubscription(consumer, executor, task);
            subscriptions.add(subscription);
            return subscription;
        } catch (Exception e) {
            throw NopException.adapt(e);
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
