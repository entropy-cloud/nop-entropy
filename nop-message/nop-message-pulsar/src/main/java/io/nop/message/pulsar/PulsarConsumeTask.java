/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.util.FutureHelper;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PulsarConsumeTask {
    static final Logger LOG = LoggerFactory.getLogger(PulsarConsumeTask.class);

    private static final long DEFAULT_CONSUME_ERROR_BACKOFF_MS = 1000;

    /**
     * Bounded wait per receive round (MSG-F-54, WI19): the run loop re-checks
     * {@code active} at least once per timeout even when no message arrives, so
     * {@link #stop()} terminates the task in bounded time WITHOUT depending on
     * {@code consumer.close()} to unblock it (previously the loop blocked in the no-timeout
     * {@code receive()} until close threw AlreadyClosedException). Mirrors the Kafka poll
     * timeout default. Deliberately a constant, not an SPI option field: an option no
     * backend reads would violate the no-dead-options constraint.
     */
    static final long DEFAULT_RECEIVE_TIMEOUT_MS = 1000L;

    private final ExecutorService executor;
    private final Consumer<Object> pulsarConsumer;
    private final PulsarMessageService service;
    private final MessageSubscriptionConfig config;
    private final long consumeErrorBackoffMs;
    private long receiveTimeoutMs = DEFAULT_RECEIVE_TIMEOUT_MS;

    private volatile boolean active = false;
    // WI16: message-level failure context, filled by the inner catches before rethrow.
    private String lastFailureTopic;
    private String lastFailureLocation;
    private Object lastFailureMessage;
    private final java.util.Map<String, Integer> failureAttempts = new java.util.HashMap<>();

    /**
     * Pending {@link Consumer#batchReceiveAsync()} future, retained across receive-timeout
     * rounds (WI19). Pulsar 2.8 has no batchReceive(timeout) overload; re-issuing a new
     * batchReceiveAsync after every timeout would accumulate abandoned pending requests
     * inside the client whose messages could never be acked, so the SAME future is awaited
     * again. Confined to the single task thread (like the other loop state); {@code active}
     * is the only cross-thread flag.
     */
    private CompletableFuture<Messages<Object>> pendingBatch;

    public PulsarConsumeTask(PulsarMessageService service, ExecutorService executor,
                             Consumer<Object> pulsarConsumer, MessageSubscriptionConfig config) {
        this(service, executor, pulsarConsumer, config, DEFAULT_CONSUME_ERROR_BACKOFF_MS);
    }

    public PulsarConsumeTask(PulsarMessageService service, ExecutorService executor,
                             Consumer<Object> pulsarConsumer, MessageSubscriptionConfig config,
                             long consumeErrorBackoffMs) {
        this.service = service;
        this.executor = executor;
        this.pulsarConsumer = pulsarConsumer;
        this.config = config;
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
    }

    public void start() {
        active = true;
        executor.execute(this::runTask);
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
                LOG.error("nop.err.message.pulsar.consume-fail", e);
                dispatchConsumeFailure(e);
                if (active) {
                    try {
                        Thread.sleep(consumeErrorBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } while (active);
    }

    public void stop() {
        active = false;
    }

    /**
     * Bounded stop (MSG-F-54, WI19): the run loop waits on receive / batchReceiveAsync with
     * a finite timeout ({@link #DEFAULT_RECEIVE_TIMEOUT_MS}), so it observes
     * {@code active == false} within one timeout round even when no message ever arrives.
     * It never wakes or closes the consumer — closing remains the subscription-cancel
     * path's job (its ordering is WI20) — and loop exit never depends on close-induced
     * exceptions.
     */
    public boolean isActive() {
        return active;
    }

    /** Test seam (mirrors the Kafka pollTimeoutMs wiring): shrinks the bounded-wait round. */
    void setReceiveTimeoutMs(long receiveTimeoutMs) {
        this.receiveTimeoutMs = receiveTimeoutMs;
    }

    /**
     * Bounded batch receive (MSG-F-54, WI19). Pulsar 2.8 has no batchReceive(timeout)
     * overload, so the task awaits {@link Consumer#batchReceiveAsync()} with a finite
     * timeout while RETAINING the pending future:
     * <ul>
     * <li>Timeout with {@code active} still set → the SAME future is awaited again (never
     * re-issued — an abandoned pending request would accumulate in the client and strand
     * its messages unacked).</li>
     * <li>Timeout with stop requested → returns null; the run loop re-checks
     * {@code active} and exits bounded. Any message that later completes the abandoned
     * future stays unacked and is redelivered by Pulsar when the consumer closes — the only
     * production caller of stop() is the cancel path, which closes the consumer right
     * after.</li>
     * <li>Exceptional completion (consumer closed, broker disconnected) → the pending
     * future is cleared and the cause rethrown so the run loop's backoff path takes over;
     * retaining a dead future would turn every round into an immediate error loop.</li>
     * <li>Interrupt (forced shutdown) → the interrupt flag is restored and null returned;
     * the loop's active check decides exit (standard interruption protocol, not a silent
     * skip).</li>
     * </ul>
     */
    private Messages<Object> receiveBatchBounded() throws Exception {
        if (pendingBatch == null) {
            pendingBatch = pulsarConsumer.batchReceiveAsync();
        }
        try {
            Messages<Object> messages = pendingBatch.get(receiveTimeoutMs, TimeUnit.MILLISECONDS);
            pendingBatch = null;
            return messages;
        } catch (TimeoutException e) {
            // Keep pendingBatch; empty round — the loop re-checks active (bounded stop).
            return null;
        } catch (ExecutionException e) {
            pendingBatch = null;
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException("batchReceiveAsync failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    void batchConsume() throws Exception {
        Messages<Object> messages = receiveBatchBounded();
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
                if (response instanceof PartialCommit) {
                    // D5(b-prime): ack records [0..k-1] only; the record carrying the
                    // signal (index k) and everything after it is negatively acknowledged
                    // (redelivered) — the boundary record itself must not be dropped.
                    FutureHelper.syncGet(FutureHelper.waitAll(futures));
                    pulsarConsumer.negativeAcknowledge(message.getMessageId());
                    while (it.hasNext()) {
                        Message<Object> remaining = it.next();
                        pulsarConsumer.negativeAcknowledge(remaining.getMessageId());
                    }
                    // Skip the normal commit: the boundary is expressed by the acks above.
                    return;
                }
                if (response instanceof TerminateConsume) {
                    // D5(b-prime): acknowledged, no redelivery, no reply.
                    LOG.warn("nop.message.pulsar.terminate-consume:messageId={},reason={}",
                            message.getMessageId(), ((TerminateConsume) response).getReason());
                    futures.add(ackAsync(context, message));
                } else if (response != null) {
                    if (response instanceof ConsumeLater) {
                        ConsumeLater later = (ConsumeLater) response;
                        CompletionStage<?> future = pulsarConsumer.reconsumeLaterAsync(message, later.getDelay(), TimeUnit.MILLISECONDS);
                        futures.add(future);
                    } else {
                        futures.add(context.sendAsync(getAckTopic(message.getTopicName()), response));
                        futures.add(ackAsync(context, message));
                    }
                } else {
                    futures.add(ackAsync(context, message));
                }
            }
            FutureHelper.syncGet(FutureHelper.waitAll(futures));
            context.commit();
        } catch (Exception e) {
            LOG.error("nop.err.message.pulsar.batch-consume-fail", e);
            lastFailureLocation = null;
            lastFailureMessage = null;
            pulsarConsumer.negativeAcknowledge(messages);
            try {
                context.rollback();
            } catch (Exception rollbackError) {
                // A rollback failure must not mask the original consumer failure (WI10).
                e.addSuppressed(rollbackError);
            }
            // MSG-F-05: propagate so the run-loop's backoff and onException failure
            // callback are reachable for message-level failures (aligned with Kafka).
            throw e;
        }
    }

    void consume() throws Exception {
        // MSG-F-54 (WI19): bounded receive — null on timeout — so the loop re-checks
        // active at least once per DEFAULT_RECEIVE_TIMEOUT_MS instead of blocking until
        // the next message or a close-induced exception.
        Message<Object> message = pulsarConsumer.receive((int) receiveTimeoutMs, TimeUnit.MILLISECONDS);
        if (message == null)
            return;

        IMessageConsumer consumer = config.getConsumer();

        ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);
        ConsumeContext context = newConsumeContext();
        try {
            Object response = consumer.onMessage(message.getTopicName(), apiMessage, context);
            response = FutureHelper.getResult(response);

            if (response instanceof ConsumeLater) {
                ConsumeLater later = (ConsumeLater) response;
                pulsarConsumer.reconsumeLater(message, later.getDelay(), TimeUnit.MILLISECONDS);
            } else if (response instanceof TerminateConsume) {
                // D5(b-prime): acknowledged, no redelivery, no reply.
                LOG.warn("nop.message.pulsar.terminate-consume:messageId={},reason={}",
                        message.getMessageId(), ((TerminateConsume) response).getReason());
                FutureHelper.syncGet(ackAsync(context, message));
            } else if (response instanceof PartialCommit) {
                // Single-message dispatch has no batch boundary; acknowledged as completion
                // (declared in the WI6 plan).
                FutureHelper.syncGet(ackAsync(context, message));
            } else if (response instanceof Acknowledge) {
                context.send(getAckTopic(message.getTopicName()), ((Acknowledge) response).getReplyMessage());
                FutureHelper.syncGet(ackAsync(context, message));
            } else {
                if (response != null) {
                    context.send(getAckTopic(message.getTopicName()), response);
                }
                FutureHelper.syncGet(ackAsync(context, message));
            }
            // WI16: success resets the consecutive-failure counter for this location.
            failureAttempts.remove(String.valueOf(message.getMessageId()));
            context.commit();
        } catch (Exception e) {
            fail(context, message, e);
            try {
                context.rollback();
            } catch (Exception rollbackError) {
                // A rollback failure must not mask the original consumer failure (WI10).
                e.addSuppressed(rollbackError);
            }
            // MSG-F-05: propagate so the run-loop's backoff and onException failure
            // callback are reachable for message-level failures (aligned with Kafka).
            throw e;
        }
    }

    /**
     * WI16: dispatches {@link IMessageConsumer#onConsumeFailure} with the context captured
     * by fail()/batch (framework-level failures get topic=null, attempt=0). Guarded — a
     * throwing user callback must not kill the consume thread.
     */
    private void dispatchConsumeFailure(Exception e) {
        String location = lastFailureLocation;
        Object message = lastFailureMessage;
        lastFailureLocation = null;
        lastFailureMessage = null;
        int attempt = 0;
        if (location != null) {
            attempt = failureAttempts.merge(location, 1, Integer::sum);
        }
        try {
            config.getConsumer().onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                    lastFailureTopic, location, message, attempt, e));
        } catch (Exception callbackError) {
            LOG.warn("nop.err.message.pulsar.failure-callback-throw", callbackError);
        }
    }

    String getAckTopic(String topic) {
        return service.getAckTopic(topic);
    }

    ConsumeContext newConsumeContext() {
        // D11 (WI14): transaction semantics removed — at-least-once baseline; F-52's
        // per-message broker transaction no longer exists.
        return new ConsumeContext();
    }

    CompletionStage<Void> ackAsync(ConsumeContext context, Message<Object> message) {
        return pulsarConsumer.acknowledgeAsync(message);
    }

    void fail(ConsumeContext context, Message<Object> message, Throwable exception) throws Exception {
        LOG.error("nop.err.message.pulsar.consume-fail:messageId={}", message.getMessageId(), exception);
        pulsarConsumer.negativeAcknowledge(message.getMessageId());
        // WI16: carry the message context up to the run loop's failure dispatch.
        lastFailureTopic = message.getTopicName();
        lastFailureLocation = String.valueOf(message.getMessageId());
        lastFailureMessage = message.getValue();
    }

    /**
     * D11 (WI14): consumption follows the at-least-once baseline. {@code commit}/
     * {@code rollback} are explicit no-ops — the ack/commit mechanics of each backend carry
     * the delivery semantics (see backend-semantics-design.md 3.7); the deprecated
     * transaction options are ignored with a one-time warn (see
     * {@code MessageOptionsValidator}).
     */
    protected class ConsumeContext implements IMessageConsumeContext {

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return service.sendAsync(topic, message, options);
        }

        /**
         * At-least-once baseline (D11): no transaction boundary; batch paths perform their
         * backend-native commits explicitly.
         */
        public void commit() {
            // no-op by design (at-least-once baseline)
        }

        /** At-least-once baseline (D11): see {@link #commit()}. */
        public void rollback() {
            // no-op by design (at-least-once baseline)
        }
    }
}
