package io.nop.message.pulsar;

import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-C8-005（plan 2558 裁定 K，同键族 Pulsar 扩展）carrier：reconsumeLater budget bounce.
 *
 * <ul>
 * <li>Delay matrix {0, -5, 200}: the effective reconsumeLater delay is
 * {@code max(requested, consumeLaterMinDelayMs)}.</li>
 * <li>Over {@code consumeLaterMaxDefers} consecutive rounds: WARN + the message nacks
 * straight back to the broker (poll main path) instead of honoring another in-task
 * delay.</li>
 * </ul>
 */
class TestConsumeLaterBudget {

    @SuppressWarnings("unchecked")
    private org.apache.pulsar.client.api.Message<Object> message() {
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getTopicName()).thenReturn("persistent://public/default/t-defer");
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        return message;
    }

    private PulsarConsumeTask task(Consumer<Object> consumer, IMessageConsumer handler) {
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t-defer", handler,
                new MessageSubscribeOptions());
        return new PulsarConsumeTask(mock(PulsarMessageService.class),
                Executors.newSingleThreadExecutor(), consumer, config);
    }

    @Test
    void delayMatrixFloorsZeroAndNegativeDelays() throws Exception {
        long[] requested = {0L, -5L, 200L};
        long minDelay = 100L;
        for (long delay : requested) {
            Consumer<Object> consumer = mock(Consumer.class);
            org.apache.pulsar.client.api.Message<Object> m = message();
            when(consumer.receive(anyInt(), any())).thenReturn(m);

            PulsarConsumeTask task = task(consumer,
                    (topic, msg, ctx) -> new ConsumeLater(delay));
            task.setConsumeLaterBudgetForTest(minDelay, 0); // budget unlimited

            task.consume();

            // The effective delay honors the floor: reconsumeLater was called with
            // max(delay, minDelay).
            verify(consumer).reconsumeLater(org.mockito.ArgumentMatchers.eq(m),
                    org.mockito.ArgumentMatchers.eq(Math.max(delay, minDelay)),
                    org.mockito.ArgumentMatchers.any(java.util.concurrent.TimeUnit.class));
            verify(consumer, never()).negativeAcknowledge(m.getMessageId());
        }
    }

    @Test
    void consecutiveDefersOverBudgetBounceBackToBroker() throws Exception {
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> m = message();
        when(consumer.receive(anyInt(), any())).thenReturn(m);

        java.util.concurrent.atomic.AtomicInteger rounds = new java.util.concurrent.atomic.AtomicInteger();
        PulsarConsumeTask task = task(consumer, (topic, msg, ctx) -> new ConsumeLater(0L));
        task.setConsumeLaterBudgetForTest(300L, 2);

        // Rounds 1-2: within budget — reconsumeLater honors the floor.
        task.consume();
        task.consume();
        verify(consumer, org.mockito.Mockito.times(2)).reconsumeLater(
                org.mockito.ArgumentMatchers.eq(m),
                org.mockito.ArgumentMatchers.eq(300L), any(java.util.concurrent.TimeUnit.class));

        // Round 3: over budget — bounce: straight negativeAcknowledge (poll main path),
        // no further reconsumeLater delay.
        task.consume();
        verify(consumer).negativeAcknowledge(m.getMessageId());
        verify(consumer, org.mockito.Mockito.times(2)).reconsumeLater(
                org.mockito.ArgumentMatchers.eq(m), anyLong(),
                any(java.util.concurrent.TimeUnit.class));
    }

    @Test
    void budgetResetAfterFinalizingRound() throws Exception {
        Consumer<Object> consumer = mock(Consumer.class);
        org.apache.pulsar.client.api.Message<Object> m = message();
        when(consumer.receive(anyInt(), any())).thenReturn(m);
        when(consumer.acknowledgeAsync(any(org.apache.pulsar.client.api.Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Round 1 finalizes (null); rounds 2-3 defer. The finalizing round resets the
        // counter, so both defers stay within budget (maxDefers=2: without the reset,
        // the round-2 defer would already be #2 and round 3 would bounce).
        java.util.concurrent.atomic.AtomicInteger rounds = new java.util.concurrent.atomic.AtomicInteger();
        PulsarConsumeTask task = task(consumer, (topic, msg, ctx) ->
                rounds.incrementAndGet() == 1 ? null : new ConsumeLater(0L));
        task.setConsumeLaterBudgetForTest(100L, 2);

        task.consume(); // finalizing round — resets the counter
        task.consume(); // defer #1 (within budget after reset)
        task.consume(); // defer #2 — would bounce without the reset
        verify(consumer, org.mockito.Mockito.times(2)).reconsumeLater(
                org.mockito.ArgumentMatchers.eq(m),
                org.mockito.ArgumentMatchers.eq(100L), any(java.util.concurrent.TimeUnit.class));
        verify(consumer, never()).negativeAcknowledge(m.getMessageId());
    }
}
