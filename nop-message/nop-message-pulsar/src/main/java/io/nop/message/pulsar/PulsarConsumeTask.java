/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.util.FutureHelper;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class PulsarConsumeTask {
    static final Logger LOG = LoggerFactory.getLogger(PulsarConsumeTask.class);

    private final Executor executor;
    private final Consumer<Object> pulsarConsumer;
    private final PulsarMessageService service;
    private final MessageSubscriptionConfig config;

    private volatile boolean active = false;

    public PulsarConsumeTask(PulsarMessageService service, Executor executor,
                             Consumer<Object> pulsarConsumer, MessageSubscriptionConfig config) {
        this.service = service;
        this.executor = executor;
        this.pulsarConsumer = pulsarConsumer;
        this.config = config;
    }

    public void start() {
        executor.execute(() -> {
            seekToPosition();
            this.runTask();
        });
    }

    private void seekToPosition() {

    }

    public void schedule() {
        executor.execute(this::runTask);
    }

    private void runTask() {
        do {
            try {
                if (config.getOptions().allowBatchConsume()) {
                    batchConsume();
                } else {
                    consume();
                }
            } catch (Exception e) {
                LOG.error("nop.err.pulsar.consume-fail", e);
                config.getConsumer().onException(e);
            }
        } while (active);
    }

    public void stop() {
        active = false;
    }

    void batchConsume() throws Exception {
        Messages<Object> messages = pulsarConsumer.batchReceive();
        if (messages == null)
            return;

        List<TopicMessage> list = new ArrayList<>(messages.size());
        for (Message<Object> message : messages) {
            ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);
            list.add(new TopicMessage(message.getTopicName(), apiMessage));
        }

        IMessageConsumer consumer = config.getConsumer();

        ConsumeContext context = newConsumeContext();
        try {
            List<Object> responses = consumer.onMessageBatch(list, context);
            Iterator<Message<Object>> it = messages.iterator();
            List<CompletionStage<?>> futures = new ArrayList<>(responses.size());

            for (Object response : responses) {
                response = FutureHelper.getResult(response);
                Message<Object> message = it.next();
                if (response != null) {
                    if (response instanceof ConsumeLater) {
                        ConsumeLater later = (ConsumeLater) response;
                        CompletionStage<?> future = pulsarConsumer.reconsumeLaterAsync(message, later.getDelay(), TimeUnit.MILLISECONDS);
                        futures.add(future);
                    } else {
                        futures.add(context.sendAsync(getReplyTopic(message.getTopicName()), response));
                        futures.add(ackAsync(context, message));
                    }
                } else {
                    futures.add(ackAsync(context, message));
                }
            }
            FutureHelper.syncGet(FutureHelper.waitAll(futures));
            context.commit();
        } catch (Exception e) {
            LOG.error("nop.err.pulsar.batch-consume-fail", e);
            pulsarConsumer.negativeAcknowledge(messages);
            context.rollback();
        }
    }

    void consume() throws Exception {
        Message<Object> message = pulsarConsumer.receive();
        if (message == null)
            return;

        IMessageConsumer consumer = config.getConsumer();

        ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);
        ConsumeContext context = newConsumeContext();
        try {
            Object response = consumer.onMessage(message.getTopicName(), apiMessage, context);
            response = FutureHelper.getResult(response);
            if (response != null) {
                if (response instanceof ConsumeLater) {
                    ConsumeLater later = (ConsumeLater) response;
                    pulsarConsumer.reconsumeLater(message, later.getDelay(), TimeUnit.MILLISECONDS);
                } else {
                    context.send(getReplyTopic(message.getTopicName()), response);
                    FutureHelper.syncGet(ackAsync(context, message));
                }
            } else {
                FutureHelper.syncGet(ackAsync(context, message));
            }
            context.commit();
        } catch (Exception e) {
            fail(context, message, e);
            context.rollback();
        }
    }

    String getReplyTopic(String topic) {
        return service.getReplyTopic(topic);
    }

    ConsumeContext newConsumeContext() throws PulsarClientException {
        Transaction txn = null;
        if (config.getOptions().isTransactional()) {
            txn = service.newTransaction(config.getOptions().getTransactionTimeout());
        }
        return new ConsumeContext(txn);
    }

    CompletionStage<Void> ackAsync(ConsumeContext context, Message<Object> message) throws Exception {
        Transaction transaction = context.getTransaction();
        if (transaction != null) {
            return pulsarConsumer.acknowledgeAsync(message.getMessageId(), transaction);
        } else {
            return pulsarConsumer.acknowledgeAsync(message);
        }
    }

    void fail(ConsumeContext context, Message<Object> message, Throwable exception) throws Exception {
        LOG.error("nop.err.pulsar.consume-fail:messageId={}", message.getMessageId(), exception);
        pulsarConsumer.negativeAcknowledge(message.getMessageId());
    }

    class ConsumeContext implements IMessageConsumeContext {
        private Transaction transaction;

        public ConsumeContext(Transaction transaction) {
            this.transaction = transaction;
        }

        public Transaction getTransaction() {
            return transaction;
        }

        @Override
        public void reply(Object message) {

        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return service.sendAsync(transaction, topic, message, options);
        }

        public void commit() {
            if (transaction != null)
                FutureHelper.syncGet(transaction.commit());
        }

        public void rollback() {
            if (transaction != null)
                FutureHelper.syncGet(transaction.abort());
        }
    }
}