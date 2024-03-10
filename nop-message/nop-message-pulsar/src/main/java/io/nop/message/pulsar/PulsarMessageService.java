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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PulsarMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(PulsarMessageService.class);

    private PulsarClientConfig config;

    private PulsarProducerConfig defaultProducerConfig;

    private PulsarConsumerConfig defaultConsumerConfig;

    private List<MessageSubscriptionConfig> subscriptionConfigs;

    private PulsarClient client;

    private Map<String, Schema> topicSchemas;

    private Map<String, Producer> producers = new ConcurrentHashMap<>();
    private Producer defaultProducer;

    private Queue<PulsarMessageSubscription> subscriptions = new ConcurrentLinkedQueue<>();

    PulsarClient getClient() {
        return client;
    }

    Producer getProducer(String topic) {
        Producer producer = producers.get(topic);
        if (producer != null)
            return producer;

        Schema schema = topicSchemas.get(topic);
        if (schema != null) {
            return producers.computeIfAbsent(topic, key -> buildProducer(schema));
        } else {
            return defaultProducer;
        }
    }

    Producer<?> buildProducer(Schema<?> schema) {
        try {
            ProducerBuilder<?> builder = client.newProducer(schema);
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
            MessageSubscriptionConfig config = new MessageSubscriptionConfig(topic, listener, options);

            try {
                for (int i = 0; i < concurrency; i++) {
                    subs.add(doSubscribe(config));
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
        try {
            Consumer<?> consumer = builder.subscribe();
            new PulsarConsumeTask(this, newConsumeExecutor(), (Consumer<Object>) consumer, subConfig).start();
            return new PulsarMessageSubscription(consumer);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    Executor newConsumeExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    class PulsarMessageSubscription extends Cancellable implements IMessageSubscription {
        private final Consumer<?> consumer;
        private volatile boolean suspended;

        public PulsarMessageSubscription(Consumer<?> consumer) {
            this.consumer = consumer;
            this.appendOnCancel(this::doOnCancel);
        }

        private void doOnCancel(String reason) {
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.error("nop.message.pulsar.cancel-failed", e);
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