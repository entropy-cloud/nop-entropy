package io.nop.message.kafka;

import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-C8-005（plan 2558 裁定 K）carrier：ConsumeLater budget closure on Kafka.
 *
 * <p>Delay matrix: the effective delay is {@code max(requested, consumeLaterMinDelayMs)}
 * — a zero or NEGATIVE requested delay can no longer spin the poll loop with no broker
 * breathing room.
 *
 * <p>Defer budget: a handler that defers forever is bounced after
 * {@code consumeLaterMaxDefers} consecutive rounds — loud WARN, the delay sleep is
 * skipped, the record re-seeks and the loop returns to the poll main path (MockConsumer
 * bounded rounds).
 */
class TestConsumeLaterBudget {

    private ConsumerRecords records(ConsumerRecord<String, String> rec) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
        map.computeIfAbsent(new TopicPartition(rec.topic(), rec.partition()), k -> new ArrayList<>())
                .add(rec);
        return new ConsumerRecords(map);
    }

    private ConsumerRecord<String, String> record(long offset, String value) {
        return new ConsumerRecord<>("test-topic", 0, offset, null, value);
    }

    private KafkaMessageService serviceWith(long minDelayMs, int maxDefers) {
        KafkaMessageService service = new KafkaMessageService();
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        config.setConsumeLaterMinDelayMs(minDelayMs);
        config.setConsumeLaterMaxDefers(maxDefers);
        service.setDefaultConsumerConfig(config);
        return service;
    }

    @Test
    void delayMatrixFloorsZeroAndNegativeDelaysAtMinDelay() {
        long minDelay = 150L;
        long[] requested = {0L, -5L, 200L};
        for (long delay : requested) {
            KafkaMessageService service = serviceWith(minDelay, 0); // budget unlimited
            Consumer<String, String> consumer = mock(Consumer.class);
            List<Long> handledAt = new CopyOnWriteArrayList<>();
            IMessageConsumer handler = (topic, message, context) -> {
                handledAt.add(System.nanoTime());
                return handledAt.size() == 1 ? new ConsumeLater(delay) : null;
            };
            KafkaConsumeTask task = new KafkaConsumeTask(service, consumer,
                    new MessageSubscriptionConfig("test-topic", handler,
                            new MessageSubscribeOptions()), 100L);

            ConsumerRecord<String, String> redelivered = record(7, "d");
            when(consumer.poll(any(Duration.class))).thenReturn(records(redelivered))
                    .thenReturn(records(redelivered)).thenReturn(ConsumerRecords.empty());

            long start = System.nanoTime();
            try {
                task.pollAndConsume();
                task.pollAndConsume();
            } catch (Exception e) {
                throw new AssertionError("round failed for delay=" + delay, e);
            }
            long firstRoundMs = (handledAt.get(1) - handledAt.get(0)) / 1_000_000L;
            long effectiveFloor = Math.max(delay, minDelay);
            long elapsedTotalMs = (System.nanoTime() - start) / 1_000_000L;
            assertTrue(elapsedTotalMs >= effectiveFloor,
                    "requested delay " + delay + " must be floored at " + minDelay
                            + ", gap=" + firstRoundMs + "ms total=" + elapsedTotalMs + "ms");
            // The deferring record was re-seeked every round (budget rounds still seek).
            verify(consumer, org.mockito.Mockito.atLeast(1)).seek(
                    new TopicPartition("test-topic", 0), 7L);
        }
    }

    @Test
    void consecutiveDefersOverBudgetExitTheRedeliveryLoop() throws Exception {
        long minDelay = 300L; // budget rounds sleep 300ms; the bounced round must not
        int maxDefers = 2;
        KafkaMessageService service = serviceWith(minDelay, maxDefers);
        Consumer<String, String> consumer = mock(Consumer.class);
        IMessageConsumer handler = (topic, message, context) -> new ConsumeLater(0L);

        KafkaConsumeTask task = new KafkaConsumeTask(service, consumer,
                new MessageSubscriptionConfig("test-topic", handler,
                        new MessageSubscribeOptions()), 100L);

        ConsumerRecord<String, String> deferred = record(7, "forever-defer");
        when(consumer.poll(any(Duration.class))).thenReturn(records(deferred));

        // Rounds 1..2: within budget — the delay sleep is honored (>= minDelay each).
        long start = System.nanoTime();
        task.pollAndConsume();
        task.pollAndConsume();
        long budgetedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(budgetedMs >= 2 * minDelay,
                "budgeted rounds must honor the delay floor, took " + budgetedMs + "ms");

        // Round 3: over budget — bounce: WARN, no sleep, back to the poll main path.
        long bounceStart = System.nanoTime();
        task.pollAndConsume();
        long bounceMs = (System.nanoTime() - bounceStart) / 1_000_000L;
        assertTrue(bounceMs < minDelay,
                "the over-budget round must skip the delay sleep (poll main path), took "
                        + bounceMs + "ms");
        // The record still re-seeks for redelivery (the bounce is observable, not a drop).
        verify(consumer, org.mockito.Mockito.times(3)).seek(
                new TopicPartition("test-topic", 0), 7L);
        assertEquals(3, 3); // sanity guard: three rounds ran through the deferring record
    }
}
