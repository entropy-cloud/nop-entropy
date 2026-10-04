package io.nop.message.core.contract;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.TopicMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker-neutral contract test for {@link IMessageService} implementations (roadmap WI4).
 *
 * <p>The contract encodes the adjudicated semantics in
 * {@code ai-dev/design/nop-message/backend-semantics-design.md} (D5 return-value protocol)
 * and {@code ai-dev/design/nop-message/message-contract-design.md} (D1 per-backend capability
 * declaration): what an implementation declares via the protected hooks is what the contract
 * asserts — no silent gap between declaration and behavior.
 *
 * <p>Backends extending this skeleton must be green. The batch-consume dimension is gated by
 * {@link #supportsBatchConsume()}: implementations that do not declare batch support skip those
 * tests via an explicit JUnit assumption (a visible, logged skip — never a silent one).
 * When WI6 introduces {@code MessageCapability}, these hooks are expected to be replaced by
 * capability-set-driven gating.
 */
public abstract class AbstractMessageServiceContractTest {

    /**
     * ConsumeLater tests use a delay comfortably above scheduling noise so the
     * "no immediate redelivery" negative assertion is deterministic.
     */
    protected static final long CONSUME_LATER_DELAY_MS = 300L;
    private static final long REDELIVERY_WAIT_MS = CONSUME_LATER_DELAY_MS + 3000L;

    protected IMessageService service;

    /** Creates the fresh service instance under test. */
    protected abstract IMessageService createService();

    /**
     * Closes/releases the service. Maps to {@code clearConsumers()} for Local, {@code destroy()}
     * for broker backends. The destroy-idempotency contract asserts this is safe to call twice.
     */
    protected abstract void closeService();

    /** Whether the implementation declares batch consumption (drives onMessageBatch tests). */
    protected boolean supportsBatchConsume() {
        return false;
    }

    /** Whether suspend/resume is declared as effective (drives suspend-resume tests). */
    protected boolean supportsSuspendResume() {
        return true;
    }

    @BeforeEach
    void setUp() {
        service = createService();
    }

    @AfterEach
    void tearDown() {
        closeService();
    }

    // ============================================================
    // Dimension 1: send
    // ============================================================

    @Test
    void sendAsyncDeliversToSubscriber() throws Exception {
        List<Object> received = new CopyOnWriteArrayList<>();
        service.subscribe("t-send", recordingConsumer(received, null));

        service.sendAsync("t-send", "msg-1", null).toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals(1, received.size());
        assertEquals("msg-1", received.get(0));
    }

    @Test
    void syncSendDeliversToSubscriber() {
        List<Object> received = new CopyOnWriteArrayList<>();
        service.subscribe("t-send-sync", recordingConsumer(received, null));

        service.send("t-send-sync", "msg-2", null);

        assertEquals(1, received.size());
        assertEquals("msg-2", received.get(0));
    }

    @Test
    void sendMultiDeliversAllMessages() throws Exception {
        List<Object> received = new CopyOnWriteArrayList<>();
        service.subscribe("t-multi", recordingConsumer(received, null));

        List<TopicMessage> messages = new ArrayList<>();
        messages.add(new TopicMessage("t-multi", "m-1"));
        messages.add(new TopicMessage("t-multi", "m-2"));
        messages.add(new TopicMessage("t-multi", "m-3"));
        service.sendMultiAsync(messages, null).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(3, received.size());
        assertTrue(received.contains("m-1"));
        assertTrue(received.contains("m-2"));
        assertTrue(received.contains("m-3"));
    }

    // ============================================================
    // Dimension 2: five onMessage return-value semantics
    // ============================================================

    @Test
    void nullReturnMeansAcknowledgedNoReply() {
        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        String topic = "t-null";
        service.subscribe(topic, recordingConsumer(new CopyOnWriteArrayList<>(), null));
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        service.send(topic, "payload", null);

        assertTrue(ackTopicMessages.isEmpty(),
                "null return must not produce a reply on the ack topic");
    }

    @Test
    void completionStageReturnIsUnwrappedBeforeDispatch() throws Exception {
        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        String topic = "t-async";
        IMessageConsumer asyncConsumer = (t, m, ctx) -> {
            // Asynchronous handling: the reply is only dispatched after completion.
            return java.util.concurrent.CompletableFuture.completedFuture("async-reply");
        };
        service.subscribe(topic, asyncConsumer);
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        service.send(topic, "payload", null);

        assertEquals(1, ackTopicMessages.size(),
                "CompletionStage must be awaited and its result dispatched as the reply");
        assertEquals("async-reply", ackTopicMessages.get(0));
    }

    @Test
    void acknowledgeReturnSendsReplyMessageToAckTopic() {
        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        String topic = "t-ack";
        service.subscribe(topic, recordingConsumer(new CopyOnWriteArrayList<>(),
                () -> new Acknowledge("ack-payload")));
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        service.send(topic, "request", null);

        assertEquals(1, ackTopicMessages.size());
        assertEquals("ack-payload", ackTopicMessages.get(0));
    }

    @Test
    void otherNonNullReturnIsSentAsReplyToAckTopic() {
        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        String topic = "t-reply";
        service.subscribe(topic, recordingConsumer(new CopyOnWriteArrayList<>(),
                () -> "plain-reply"));
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        service.send(topic, "request", null);

        assertEquals(1, ackTopicMessages.size());
        assertEquals("plain-reply", ackTopicMessages.get(0));
    }

    @Test
    void consumeLaterDefersRedeliveryWithoutAckReply() throws Exception {
        String topic = "t-later";
        List<Object> received = new CopyOnWriteArrayList<>();
        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        // First call returns ConsumeLater, subsequent calls complete (no self-renewal).
        IMessageConsumer consumer = (t, m, ctx) -> {
            received.add(m);
            if (calls.incrementAndGet() == 1) {
                return new ConsumeLater(CONSUME_LATER_DELAY_MS);
            }
            return null;
        };
        service.subscribe(topic, consumer);
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        service.send(topic, "deferred-msg", null);

        // Synchronous dispatch is complete: no reply, and no immediate redelivery.
        assertTrue(ackTopicMessages.isEmpty(),
                "ConsumeLater must not be forwarded to the ack topic as a reply");
        assertEquals(1, received.size(),
                "no immediate redelivery within the delay window");
        assertFalse(service.getAckTopic(topic).equals(topic));

        // After the delay the message is redelivered (second call returns null).
        assertTrue(awaitCallCount(calls, 2, REDELIVERY_WAIT_MS),
                "message must be redelivered after the declared delay");
        assertEquals("deferred-msg", received.get(1),
                "redelivered payload must be identical");
        assertTrue(ackTopicMessages.isEmpty());
    }

    // ============================================================
    // Dimension 3: batch arity (gated by declaration)
    // ============================================================

    @Test
    void batchConsumeMatchesMessageArity() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(supportsBatchConsume(),
                "implementation does not declare batch consumption; batch arity contract "
                        + "applies only to backends that declare it (WI32 extends coverage)");

        List<Object> ackTopicMessages = new CopyOnWriteArrayList<>();
        String topic = "t-batch";
        AtomicInteger batchCalls = new AtomicInteger();
        List<TopicMessage> seenBatch = new CopyOnWriteArrayList<>();

        IMessageConsumer batchConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object m, IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<TopicMessage> msgList,
                                               IMessageConsumeContext ctx) {
                batchCalls.incrementAndGet();
                seenBatch.addAll(msgList);
                // One response per message: arity must match.
                List<Object> responses = new ArrayList<>(msgList.size());
                for (int i = 0; i < msgList.size(); i++) {
                    responses.add("batch-reply-" + i);
                }
                return responses;
            }
        };
        service.subscribe(topic, batchConsumer);
        service.subscribe(service.getAckTopic(topic), recordingConsumer(ackTopicMessages, null));

        List<TopicMessage> messages = new ArrayList<>();
        messages.add(new TopicMessage(topic, "b-1"));
        messages.add(new TopicMessage(topic, "b-2"));
        service.sendMultiAsync(messages, null).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1, batchCalls.get(), "batch-declaring backend must use onMessageBatch");
        assertEquals(2, seenBatch.size());
        assertEquals(2, ackTopicMessages.size(), "response arity must match message arity");
    }

    // ============================================================
    // Dimension 4: suspend / resume / cancel (+ idempotency)
    // ============================================================

    @Test
    void suspendStopsDeliveryAndResumeRestoresIt() {
        org.junit.jupiter.api.Assumptions.assumeTrue(supportsSuspendResume(),
                "implementation does not declare suspend/resume support");

        List<Object> received = new CopyOnWriteArrayList<>();
        String topic = "t-suspend";
        IMessageSubscription subscription =
                service.subscribe(topic, recordingConsumer(received, null));

        subscription.suspend();
        assertTrue(subscription.isSuspended());
        service.send(topic, "dropped-while-suspended", null);
        assertTrue(received.isEmpty(), "suspended subscription must not receive messages");

        subscription.resume();
        assertFalse(subscription.isSuspended());
        service.send(topic, "delivered-after-resume", null);
        assertEquals(1, received.size());
    }

    @Test
    void cancelStopsDeliveryAndIsIdempotent() {
        List<Object> received = new CopyOnWriteArrayList<>();
        String topic = "t-cancel";
        IMessageSubscription subscription =
                service.subscribe(topic, recordingConsumer(received, null));

        subscription.cancel();
        assertTrue(subscription.isCancelled());
        subscription.cancel(); // second cancel: no exception, no side effects

        service.send(topic, "dropped-after-cancel", null);
        assertTrue(received.isEmpty(), "cancelled subscription must not receive messages");
    }

    // ============================================================
    // Dimension 5: destroy idempotency
    // ============================================================

    @Test
    void closeServiceIsIdempotentAndServiceRemainsReusableIfDeclared() {
        closeService();
        closeService(); // second close: no exception, no side effects

        // Local semantics: after clearConsumers the service accepts new subscriptions again.
        List<Object> received = new CopyOnWriteArrayList<>();
        service.subscribe("t-after-close", recordingConsumer(received, null));
        service.send("t-after-close", "after-close", null);
        assertEquals(1, received.size());
    }

    // ============================================================
    // helpers
    // ============================================================

    protected IMessageConsumer recordingConsumer(List<Object> received,
                                                 java.util.function.Supplier<Object> reply) {
        return (topic, message, ctx) -> {
            received.add(message);
            return reply == null ? null : reply.get();
        };
    }

    private boolean awaitCallCount(AtomicInteger counter, int expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (counter.get() >= expected) {
                return true;
            }
            Thread.sleep(20L);
        }
        return counter.get() >= expected;
    }
}
