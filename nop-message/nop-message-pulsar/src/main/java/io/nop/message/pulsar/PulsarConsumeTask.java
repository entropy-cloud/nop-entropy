/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.exceptions.NopException;
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

    static final long DEFAULT_CONSUME_ERROR_BACKOFF_MS = 1000;
    static final long DEFAULT_CONSUME_ERROR_BACKOFF_MAX_MS = 30000L;
    static final int DEFAULT_MAX_CONSUME_ATTEMPTS = 10;

    /** 4.1-MA-01（plan 2558 裁定 A）：默认有界等待，javadoc 参考值见 {@code PulsarConsumerConfig}. */
    static final long DEFAULT_CONSUME_AWAIT_TIMEOUT_MS = 30000L;
    /** 4.1-C8-005（plan 2558 裁定 K，同键族）：reconsumeLater delay 下限缺省。 */
    static final long DEFAULT_CONSUME_LATER_MIN_DELAY_MS = 1000L;
    /** 4.1-C8-005（plan 2558 裁定 K，同键族）：连续 defer 预算缺省。 */
    static final int DEFAULT_CONSUME_LATER_MAX_DEFERS = 10;

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
    // 4.1-MA-01（plan 2558 裁定 A）：handler 返回 future 的有界等待上限。
    private long consumeAwaitTimeoutMs = DEFAULT_CONSUME_AWAIT_TIMEOUT_MS;
    // 4.1-C8-005（plan 2558 裁定 K，同键族）：consecutive ConsumeLater rounds（consume-thread confined）。
    private int consecutiveDefers;

    private volatile boolean active = false;
    // WI16: message-level failure context, filled by the inner catches before rethrow.
    private String lastFailureTopic;
    private String lastFailureLocation;
    private Object lastFailureMessage;
    private final java.util.Map<String, Integer> failureAttempts = new java.util.HashMap<>();

    /**
     * Pending {@link Consumer#batchReceiveAsync()} future, retained across receive-timeout
     * rounds (WI19). Pulsar (2.8 through 4.0, re-verified) has no batchReceive(timeout)
 * overload; re-issuing a new
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

    private void runTask() {
        // WI22 (F-21): escalating error backoff — n counts CONSECUTIVE error rounds
        // (0-based); resets only on a round that dispatched at least one message (an empty
        // receive timeout is neutral, otherwise broker redelivery gaps would keep resetting
        // the escalation).
        int consecutiveErrors = 0;
        do {
            try {
                boolean dispatched;
                if (config.getOptions().allowBatchConsume()) {
                    dispatched = batchConsume();
                } else {
                    dispatched = consume();
                }
                if (dispatched) {
                    consecutiveErrors = 0;
                }
            } catch (Throwable e) {
                // C3-009 (plan 2496 Phase 4): the loop barrier must cover Throwable, not
                // just Exception. Pre-fix an Error (StackOverflowError/OOM/LinkageError
                // from the user handler) escaped the loop, the consume thread died and
                // the subscription became a permanent zombie (active stayed true).
                // The Error branch keeps the Exception-branch failure reporting, then
                // exits honestly.
                consecutiveErrors++;
                LOG.error("nop.err.message.pulsar.consume-fail", e);
                dispatchConsumeFailure(e);
                if (e instanceof Exception) {
                    if (active) {
                        try {
                            Thread.sleep(computeBackoffMs(consumeErrorBackoffMs,
                                    service.consumeErrorBackoffMaxMs(), consecutiveErrors - 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } else {
                    // Error path: not backoff-recoverable — exit with active=false so
                    // isActive() stops misreporting the dead subscription as alive; the
                    // failure report above has already reached onConsumeFailure.
                    active = false;
                    break;
                }
            }
        } while (active);
    }

    /**
     * WI22 (F-21): escalating backoff — {@code baseMs * 2^n}, capped at {@code maxMs};
     * {@code n} is 0-based so the first error sleeps exactly the base. Mirrors the Kafka
     * task (the two backends share no module, hence the deliberate copy).
     */
    static long computeBackoffMs(long baseMs, long maxMs, long n) {
        long backoff = baseMs;
        for (long i = 0; i < n && backoff < maxMs; i++) {
            backoff = Math.min(backoff * 2, maxMs);
        }
        return backoff;
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
     * 4.1-MA-01（plan 2558 裁定 A）：wiring seam — the service resolves
     * {@code PulsarConsumerConfig.consumeAwaitTimeoutMs} at subscribe time.
     */
    void setConsumeAwaitTimeoutMs(long consumeAwaitTimeoutMs) {
        this.consumeAwaitTimeoutMs = consumeAwaitTimeoutMs;
    }

    // 4.1-C8-005 test seam: overrides the service-resolved budget (a mocked service
    // otherwise resolves 0/0).
    private long consumeLaterMinDelayOverride = -1L;
    private int consumeLaterMaxDefersOverride = -1;

    /** Test/wiring seam (4.1-C8-005，plan 2558 裁定 K 同键族): pin the defer budget. */
    void setConsumeLaterBudgetForTest(long minDelayMs, int maxDefers) {
        this.consumeLaterMinDelayOverride = minDelayMs;
        this.consumeLaterMaxDefersOverride = maxDefers;
    }

    private long consumeLaterMinDelayMs() {
        return consumeLaterMinDelayOverride >= 0
                ? consumeLaterMinDelayOverride
                : service.consumeLaterMinDelayMs();
    }

    private int consumeLaterMaxDefers() {
        return consumeLaterMaxDefersOverride >= 0
                ? consumeLaterMaxDefersOverride
                : service.consumeLaterMaxDefers();
    }

    /**
     * 4.1-MA-01（plan 2558 裁定 A）：bounded wait on a handler-returned future (Kafka
     * mirror of {@code KafkaConsumeTask.awaitConsumeResponse}). Plain (non-stage)
     * responses pass through; a {@code CompletionStage} that cannot convert to a
     * {@code CompletableFuture} is a typed error
     * ({@code ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC}), never a silent unbounded wait.
     *
     * @throws ConsumeAwaitTimeoutException on timeout — the caller takes the dedicated
     *         timeout branch (nack + WARN, attempt=0 semantics: never participates in
     *         the maxConsumeAttempts/poison budget).
     */
    private Object awaitConsumeResponse(Object response) throws ConsumeAwaitTimeoutException {
        if (!(response instanceof CompletionStage)) {
            return response;
        }
        CompletableFuture<Object> cf;
        try {
            cf = ((CompletionStage<Object>) response).toCompletableFuture();
        } catch (UnsupportedOperationException e) {
            throw new NopException(PulsarErrors.ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC)
                    .description("consume handler returned a CompletionStage that is not "
                            + "a CompletableFuture; refusing an unbounded wait")
                    .cause(e);
        }
        try {
            return cf.get(consumeAwaitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            cf.cancel(false);
            throw new ConsumeAwaitTimeoutException(consumeAwaitTimeoutMs, te);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopException(PulsarErrors.ERR_PULSAR_CONSUME_AWAIT_NOT_ASYNC)
                    .description("interrupted while waiting for the consume handler future")
                    .cause(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /** 4.1-MA-01（plan 2558 裁定 A）：dedicated timeout signal — NOT a poison-budget failure. */
    private static final class ConsumeAwaitTimeoutException extends Exception {
        ConsumeAwaitTimeoutException(long timeoutMs, Throwable cause) {
            super("consume handler future did not complete within " + timeoutMs + "ms", cause);
        }
    }

    /**
     * Bounded batch receive (MSG-F-54, WI19). Pulsar (2.8 through 4.0) has no batchReceive(timeout)
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

    /**
     * Batch consume path. WI22 boundary: no application-level failure endpoint here — a
     * failed batch is negatively acknowledged as a whole with no per-message attribution,
     * so the retry cap cannot be applied per record; the native
     * {@code deadLetterMaxRedeliverCount} policy covers Shared/Key_Shared subscriptions
     * (backend-semantics-design.md 3.3.2).
     *
     * <p>4.1-C3-005（plan 2558 裁定 I，CL-023 排序契约声明）：the per-message ordering
     * contract is REPLY-FIRST-THEN-ACK — message i's reply sendAsync future is registered
     * BEFORE its ackAsync future in the pipelined list, and both are awaited at the batch
     * end. This is a REGISTRATION-order contract, not a strict happens-before guarantee:
     * the two async operations may run concurrently on the client. A handler that must
     * observe the reply durably before the ack removes redelivery duplication should not
     * batch this subscription.
     *
     * <p>CL-023（重复应答声明）：the batch catch negatively acknowledges the WHOLE batch,
     * including messages whose replies were already dispatched. At-least-once residue:
     * those messages re-deliver and the handler answers AGAIN (duplicate replies are the
     * declared cost of the whole-batch nack boundary — same residue class as the Kafka
     * F-08 pre-break-point replies).
     */
    boolean batchConsume() throws Exception {
        Messages<Object> messages = receiveBatchBounded();
        if (messages == null)
            return false;

        List<TopicMessage> list = new ArrayList<>(messages.size());
        for (Message<Object> message : messages) {
            ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);
            list.add(new TopicMessage(message.getTopicName(), apiMessage));
        }

        IMessageConsumer consumer = config.getConsumer();

        ConsumeContext context = newConsumeContext();
        boolean dispatched = false;
        try {
            List<Object> responses = consumer.onMessageBatch(list, context);
            Iterator<Message<Object>> it = messages.iterator();
            List<CompletionStage<?>> futures = new ArrayList<>(responses.size());

            // WI25 (F-16): PAIRWISE iteration — response i adjudicates message i. The old
            // shape iterated responses and pulled it.next() unconditionally: more responses
            // than messages threw NoSuchElementException (whole batch nacked INCLUDING
            // already-replied records); more messages than responses left the tail
            // permanently unacked.
            int n = Math.min(responses.size(), messages.size());
            for (int i = 0; i < n; i++) {
                Object response;
                try {
                    response = awaitConsumeResponse(responses.get(i));
                } catch (ConsumeAwaitTimeoutException te) {
                    // 4.1-MA-01（plan 2558 裁定 A）：dedicated timeout branch — nack the
                    // timed-out message and the undelivered tail, WARN, attempt=0 (never
                    // the poison budget). Already-replied earlier messages keep their
                    // acks; only the boundary onward redelivers (CL-023 residue halved).
                    LOG.warn("nop.message.pulsar.consume-await-timeout:index={},timeoutMs={}",
                            i, consumeAwaitTimeoutMs);
                    while (true) {
                        Message<Object> boundary = it.next();
                        pulsarConsumer.negativeAcknowledge(boundary.getMessageId());
                        if (!it.hasNext()) {
                            break;
                        }
                    }
                    return dispatched;
                }
                dispatched = true;
                Message<Object> message = it.next();
                if (response instanceof PartialCommit) {
                    // D5(b-prime): ack records [0..k-1] only; the record carrying the
                    // signal (index k) and everything after it is negatively acknowledged
                    // (redelivered) — the boundary record itself must not be dropped. This
                    // is an EXPLICIT contract early-exit: it owns its own boundary+tail
                    // nacks and is exempt from the post-loop arity handling below.
                    FutureHelper.syncGet(FutureHelper.waitAll(futures));
                    pulsarConsumer.negativeAcknowledge(message.getMessageId());
                    while (it.hasNext()) {
                        Message<Object> remaining = it.next();
                        pulsarConsumer.negativeAcknowledge(remaining.getMessageId());
                    }
                    // Skip the normal commit: the boundary is expressed by the acks above.
                    return dispatched;
                }
                if (response instanceof TerminateConsume) {
                    // D5(b-prime): acknowledged, no redelivery, no reply.
                    LOG.warn("nop.message.pulsar.terminate-consume:messageId={},reason={}",
                            message.getMessageId(), ((TerminateConsume) response).getReason());
                    futures.add(ackAsync(context, message));
                } else if (response != null) {
                    if (response instanceof ConsumeLater) {
                        ConsumeLater later = (ConsumeLater) response;
                        // 4.1-C8-005（plan 2558 裁定 K，同键族）：delay floor + budget bounce —
                        // over budget the message nacks straight back to the broker (poll
                        // main path) instead of honoring another in-task delay.
                        consecutiveDefers++;
                        int maxDefers = consumeLaterMaxDefers();
                        if (maxDefers > 0 && consecutiveDefers > maxDefers) {
                            LOG.warn("nop.message.pulsar.consume-later-budget-exhausted:defers={},max={}",
                                    consecutiveDefers, maxDefers);
                            pulsarConsumer.negativeAcknowledge(message.getMessageId());
                        } else {
                            futures.add(pulsarConsumer.reconsumeLaterAsync(message,
                                    Math.max(later.getDelay(), consumeLaterMinDelayMs()),
                                    TimeUnit.MILLISECONDS));
                        }
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
            // 4.1-C8-005: only a FINALIZING round resets the consecutive-defer budget —
            // a deferred round must keep the count so the budget can actually trigger.
            if (responses.stream().noneMatch(r -> r instanceof ConsumeLater)) {
                consecutiveDefers = 0;
            }
            // WI25 (F-16): deterministic arity-mismatch handling — the early-exit above is
            // exempt; a handler-contract breach is surfaced with a per-occurrence WARN and
            // at-least-once-safe fallbacks (extras dropped, unhandled tail nacked).
            if (responses.size() > n) {
                LOG.warn("nop.message.pulsar.batch-arity-extra-responses:responses={},messages={}",
                        responses.size(), messages.size());
            }
            if (it.hasNext()) {
                LOG.warn("nop.message.pulsar.batch-arity-unhandled-tail:messages={},responses={}",
                        messages.size(), responses.size());
                while (it.hasNext()) {
                    Message<Object> remaining = it.next();
                    pulsarConsumer.negativeAcknowledge(remaining.getMessageId());
                }
            }
            return dispatched;
        } catch (Exception e) {
            LOG.error("nop.err.message.pulsar.batch-consume-fail", e);
            // 4.1-C3-005（plan 2558 裁定 I，CL-026）：clear ALL failure-context fields — a
            // stale topic from a previous record must not leak into the next dispatch.
            lastFailureTopic = null;
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

    boolean consume() throws Exception {
        // MSG-F-54 (WI19): bounded receive — null on timeout — so the loop re-checks
        // active at least once per DEFAULT_RECEIVE_TIMEOUT_MS instead of blocking until
        // the next message or a close-induced exception.
        Message<Object> message = pulsarConsumer.receive((int) receiveTimeoutMs, TimeUnit.MILLISECONDS);
        if (message == null)
            return false;

        IMessageConsumer consumer = config.getConsumer();

        ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);
        ConsumeContext context = newConsumeContext();
        try {
            Object response = consumer.onMessage(message.getTopicName(), apiMessage, context);
            try {
                response = awaitConsumeResponse(response);
            } catch (ConsumeAwaitTimeoutException te) {
                // 4.1-MA-01（plan 2558 裁定 A）：dedicated timeout branch — nack + WARN,
                // attempt=0 semantics (never the poison budget); the broker redelivers.
                LOG.warn("nop.message.pulsar.consume-await-timeout:messageId={},timeoutMs={}",
                        message.getMessageId(), consumeAwaitTimeoutMs);
                pulsarConsumer.negativeAcknowledge(message.getMessageId());
                return true;
            }
            if (service.metrics != null) service.metrics.incrementConsumed();

            if (response instanceof ConsumeLater) {
                ConsumeLater later = (ConsumeLater) response;
                // 4.1-C8-005（plan 2558 裁定 K，同键族）：delay floor + budget bounce.
                consecutiveDefers++;
                int maxDefers = consumeLaterMaxDefers();
                if (maxDefers > 0 && consecutiveDefers > maxDefers) {
                    LOG.warn("nop.message.pulsar.consume-later-budget-exhausted:messageId={},defers={},max={}",
                            message.getMessageId(), consecutiveDefers, maxDefers);
                    pulsarConsumer.negativeAcknowledge(message.getMessageId());
                } else {
                    pulsarConsumer.reconsumeLater(message,
                            Math.max(later.getDelay(), consumeLaterMinDelayMs()),
                            TimeUnit.MILLISECONDS);
                }
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
            // 4.1-C8-005: only a FINALIZING round resets the consecutive-defer budget —
            // a deferred round must keep the count so the budget can actually trigger.
            if (!(response instanceof ConsumeLater)) {
                consecutiveDefers = 0;
            }
            context.commit();
            return true;
        } catch (Exception e) {
            String location = String.valueOf(message.getMessageId());
            // WI22: effective attempt = local consecutive counter vs the broker redelivery
            // count (cross-restart where Pulsar tracks it — Shared/Key_Shared subscriptions).
            int attempt = Math.max(bumpFailureAttempt(location), message.getRedeliveryCount());
            if (isPoisoned(attempt)) {
                runFailureEndpoint(context, message, location, attempt, e);
                return true; // endpoint consumed the message; the loop continues
            }
            if (service.metrics != null) service.metrics.incrementFailed();
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
     * WI22: the failure endpoint for an exhausted message. WI33 (F-DLQ) correction of the
     * WI21 adjudication — the client's dead-letter diversion (verified on 2.8.0 and
     * re-verified on 4.0.14 by the WI33/plan-36 real-broker E2E) does NOT fire on a plain
     * ack: it intercepts the NEGATIVE-ack redelivery request for a message that was
     * received at {@code redeliveryCount >= maxRedeliverCount} (verified in the
     * {@code getRedeliveryMessageIdData} pipeline and by the WI33 raw-client probe). When
     * the native policy is configured and the threshold is met, the endpoint therefore
     * hands the message to that mechanism (the client publishes the DLQ copy and acks the
     * original itself — at-least-once preserved); otherwise the message is acked
     * permanently and dropped with a loud WARN. The ConsumeFailure callback dispatches in
     * both branches. The endpoint never throws.
     */
    private void runFailureEndpoint(ConsumeContext context, Message<Object> message,
                                    String location, int attempt, Exception e) {
        PulsarConsumerConfig consumerConfig = service.defaultConsumerConfig();
        int dlqMaxRedelivers = consumerConfig != null
                ? consumerConfig.getDeadLetterMaxRedeliverCount() : 0;
        if (dlqMaxRedelivers > 0 && message.getRedeliveryCount() >= dlqMaxRedelivers) {
            pulsarConsumer.negativeAcknowledge(message.getMessageId());
            LOG.warn("nop.message.pulsar.consume-attempt-exhausted:messageId={},attempts={} "
                            + "- failure endpoint, native dead-letter diversion takes over"
                            + " (nack at redeliveryCount={}, maxRedeliverCount={})",
                    location, attempt, message.getRedeliveryCount(), dlqMaxRedelivers);
        } else {
            try {
                FutureHelper.syncGet(ackAsync(context, message));
            } catch (Exception ackError) {
                LOG.warn("nop.err.message.pulsar.endpoint-ack-fail:messageId={}", location, ackError);
            }
            LOG.warn("nop.message.pulsar.consume-attempt-exhausted:messageId={},attempts={} "
                    + "- failure endpoint, leaving the redelivery loop", location, attempt);
        }
        try {
            config.getConsumer().onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                    message.getTopicName(), location, message.getValue(), attempt, e));
        } catch (Throwable callbackError) {
            // C3-009 ⑥: the failure-endpoint callback guard must cover Throwable — an
            // Error thrown by the user callback must not escape and kill the consume
            // thread (with the endpoint already consumed the message).
            LOG.warn("nop.err.message.pulsar.failure-callback-throw", callbackError);
        }
        failureAttempts.remove(location);
        lastFailureTopic = null;
        lastFailureLocation = null;
        lastFailureMessage = null;
    }

    private int bumpFailureAttempt(String location) {
        return failureAttempts.merge(location, 1, Integer::sum);
    }

    private int peekFailureAttempt(String location) {
        Integer attempt = failureAttempts.get(location);
        return attempt == null ? 0 : attempt;
    }

    /** WI22: the failure endpoint fires on failure number cap+1 ({@code 0} = unlimited). */
    private boolean isPoisoned(int attempt) {
        int cap = service.maxConsumeAttempts();
        return cap > 0 && attempt > cap;
    }

    /**
     * WI16: dispatches {@link IMessageConsumer#onConsumeFailure} with the context captured
     * by fail()/batch (framework-level failures get topic=null, attempt=0). Guarded — a
     * throwing user callback must not kill the consume thread.
     * C3-009 (plan 2496 Phase 4): the guard and the parameter width cover Throwable, so
     * an Error escaping the loop barrier still reaches the user's failure callback.
     */
    // Package-private test seam (CL-026 pin reads the failure context through it).
    void dispatchConsumeFailure(Throwable e) {
        // WI21: clear ALL context fields together — a stale topic from a previous record
        // failure must not leak into a framework-level failure report.
        String topic = lastFailureTopic;
        String location = lastFailureLocation;
        Object message = lastFailureMessage;
        lastFailureTopic = null;
        lastFailureLocation = null;
        lastFailureMessage = null;
        // WI22: the attempt was already bumped at the failure point; this dispatch reads it.
        int attempt = peekFailureAttempt(location);
        try {
            config.getConsumer().onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                    topic, location, message, attempt, asReportedException(e)));
        } catch (Throwable callbackError) {
            // C3-009 ⑥: the callback guard must cover Throwable — an Error thrown by the
            // user callback must not escape and kill the consume thread.
            LOG.warn("nop.err.message.pulsar.failure-callback-throw", callbackError);
        }
    }

    /**
     * C3-009 (plan 2496 Phase 4): {@link io.nop.api.core.message.ConsumeFailure} carries
     * an {@link Exception} (nop-api-core contract, zero-touch in this batch) — non-Exception
     * Throwables (Error escapes) are wrapped so the failure still reaches the callback with
     * its original cause attached.
     */
    private static Exception asReportedException(Throwable e) {
        if (e instanceof Exception) {
            return (Exception) e;
        }
        return new IllegalStateException(
                "non-Exception Throwable escaped the consume loop: " + e.getClass().getName(), e);
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
