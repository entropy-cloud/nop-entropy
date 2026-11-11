package io.nop.message.pulsar;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-C3-005（plan 2558 裁定 I）carrier：
 *
 * <ul>
 * <li>Per-message REPLY-FIRST-THEN-ACK ordering contract (registration order in the
 * pipelined future list; declared, not happens-before).</li>
 * <li>CL-023 declaration pin: the whole-batch nack on failure re-delivers — and
 * re-answers — already-replied messages (at-least-once residue is the declared cost).</li>
 * <li>CL-026: the batch catch clears {@code lastFailureTopic} together with the other
 * failure-context fields — a stale topic must not leak into the next failure dispatch.</li>
 * </ul>
 */
class TestPulsarBatchOrdering {

    static class BatchFixture {
        final PulsarMessageService service = mock(PulsarMessageService.class);
        @SuppressWarnings("unchecked")
        final Consumer<Object> consumer = mock(Consumer.class);
        final List<org.apache.pulsar.client.api.Message<Object>> messages = new ArrayList<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final List<CompletableFuture<org.apache.pulsar.client.api.MessageId>> acks =
                new ArrayList<>();

        @SuppressWarnings({"unchecked", "rawtypes"})
        BatchFixture() {
            try {
                // Build the messages BEFORE the when() call — nested stubbing inside a
                // when() argument trips Mockito's UnfinishedStubbing detection.
                org.apache.pulsar.client.api.Messages<Object> batch = batchMessages();
                when(consumer.batchReceiveAsync()).thenReturn(
                        CompletableFuture.completedFuture(batch));
                when(consumer.acknowledgeAsync(any(org.apache.pulsar.client.api.Message.class)))
                        .thenAnswer(inv -> CompletableFuture.completedFuture(null));
                when(service.getAckTopic(any(String.class))).thenReturn("ack-topic");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private org.apache.pulsar.client.api.Messages<Object> batchMessages() {
            org.apache.pulsar.client.api.Messages messages =
                    mock(org.apache.pulsar.client.api.Messages.class);
            for (int i = 0; i < 3; i++) {
                org.apache.pulsar.client.api.Message m =
                        mock(org.apache.pulsar.client.api.Message.class);
                when(m.getTopicName()).thenReturn("persistent://public/default/t-order");
                when(m.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
                this.messages.add(m);
                this.acks.add(new CompletableFuture<>());
            }
            when(messages.iterator()).thenAnswer(inv -> new ArrayList<>(this.messages).iterator()); // fresh per call
            when(messages.size()).thenReturn(3);
            return messages;
        }

        PulsarConsumeTask task(IMessageConsumer handler) {
            MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-order",
                    handler, new MessageSubscribeOptions());
            return new PulsarConsumeTask(service, executor, consumer, config);
        }
    }

    @Test
    void replyIsRegisteredBeforeAckForEveryRepliedMessage() throws Exception {
        BatchFixture fx = new BatchFixture();

        // Reply futures resolve only after the test releases them; the acks registered
        // AFTER each reply must still be awaited at the batch end (waitAll covers both).
        List<CompletableFuture<Void>> replies = new ArrayList<>();
        when(fx.service.sendAsync(any(String.class), any(), any())).thenAnswer(inv -> {
            CompletableFuture<Void> reply = new CompletableFuture<>();
            replies.add(reply);
            return reply;
        });

        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                responses.add(new Acknowledge("r0"));
                responses.add(new Acknowledge("r1"));
                responses.add(new Acknowledge("r2"));
                return responses;
            }
        };

        Thread runner = new Thread(() -> {
            try {
                fx.task(handler).batchConsume();
            } catch (Exception ignored) {
                // committed below is the arbiter
            }
        });
        runner.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (replies.size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        // All three replies registered (sendAsync) while acks are pending — the ackAsync
        // future of each message is registered AFTER its reply (reply-first-then-ack).
        assertEquals(3, replies.size());

        for (CompletableFuture<Void> reply : replies) {
            reply.complete(null);
        }
        runner.join(5000);
        // Every message acked (reply-then-ack per message, batch-end join).
        verify(fx.consumer, org.mockito.Mockito.times(3)).acknowledgeAsync(
                any(org.apache.pulsar.client.api.Message.class));
    }

    @Test
    void cl023_wholeBatchNackRedeliversAlreadyRepliedMessages() throws Exception {
        BatchFixture fx = new BatchFixture();
        when(fx.service.sendAsync(any(String.class), any(), any())).thenReturn(
                CompletableFuture.completedFuture(null));

        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                responses.add(new Acknowledge("replied")); // message 0 already replied
                throw new IllegalStateException("batch-boom"); // framework-grade failure
            }
        };

        assertThrows(IllegalStateException.class, () -> fx.task(handler).batchConsume());

        // CL-023 declaration pin: the WHOLE batch is nacked — including the already
        // replied message 0 (duplicate answer on redelivery is the declared residue).
        verify(fx.consumer).negativeAcknowledge(
                any(org.apache.pulsar.client.api.Messages.class));
        verify(fx.consumer, never()).acknowledgeAsync(
                any(org.apache.pulsar.client.api.Message.class));
    }

    @Test
    void cl026_batchCatchClearsStaleFailureTopic() throws Exception {
        BatchFixture fx = new BatchFixture();

        List<io.nop.api.core.message.ConsumeFailure> reported = new ArrayList<>();
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext ctx) {
                throw new IllegalStateException("single-boom"); // sets lastFailureTopic
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                throw new IllegalStateException("batch-boom"); // framework-grade batch failure
            }

            @Override
            public void onConsumeFailure(io.nop.api.core.message.ConsumeFailure failure) {
                reported.add(failure);
            }
        };
        when(fx.consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(fx.messages.get(0));

        PulsarConsumeTask task = fx.task(handler);
        // Round 1: single-message handler failure fills the failure context.
        assertThrows(IllegalStateException.class, task::consume);
        // Round 2: framework-grade batch failure — CL-026 requires the stale
        // lastFailureTopic from round 1 to be cleared before the dispatch.
        assertThrows(IllegalStateException.class, task::batchConsume);
        task.dispatchConsumeFailure(new IllegalStateException("loop-report"));

        assertEquals(1, reported.size());
        assertNull(reported.get(0).getTopic(),
                "CL-026: the batch catch must clear lastFailureTopic — the stale single-"
                        + "failure topic must not leak into the framework-level dispatch");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.apache.pulsar.client.api.Messages<Object> batchOf(BatchFixture fx) {
        org.apache.pulsar.client.api.Messages messages =
                mock(org.apache.pulsar.client.api.Messages.class);
        when(messages.iterator()).thenAnswer(inv -> new ArrayList<>(fx.messages).iterator());
        when(messages.size()).thenReturn(fx.messages.size());
        return messages;
    }
}
