package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-07（plan 2558 裁定 E）carrier：Kafka batch replies are pipelined via
 * {@code sendAsync} + a single batch-end {@code waitAll} (Pulsar form), not one
 * synchronous send per message.
 *
 * <p>Pipelining proof: the FIRST reply future is held incomplete by the test while the
 * handler returns the whole batch — under the pipelined contract the remaining replies
 * are still dispatched (all sendAsync invocations happen), and batchConsume only returns
 * after ALL futures complete (the waitAll barrier). The old per-message synchronous
 * shape returned the batch while reply futures were still unresolved.
 *
 * <p>Buffer-full probe: a failed reply future propagates out of the waitAll into the
 * batch catch (rewind + no commit) — the consumer thread is never stuck holding buffer
 * slots silently.
 */
class TestKafkaReplyPipeline {

    private final KafkaMessageService service = mock(KafkaMessageService.class);
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ConsumerRecords records(ConsumerRecord<String, String>... recs) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
        for (ConsumerRecord<String, String> r : recs) {
            map.computeIfAbsent(new TopicPartition(r.topic(), r.partition()), k -> new ArrayList<>())
                    .add(r);
        }
        return new ConsumerRecords(map);
    }

    private ConsumerRecord<String, String> record(long offset, String value) {
        return new ConsumerRecord<>("test-topic", 0, offset, null, value);
    }

    @Test
    void batchRepliesPipelinedAndJoinedAtBatchEnd() throws Exception {
        // Each reply future is held by the test; every send must be DISPATCHED before any
        // of them completes (pipelining), and batchConsume must not return before all
        // complete (waitAll at the batch end).
        CountDownLatch firstSendDispatched = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        when(service.sendAsync(any(String.class), any(), any())).thenAnswer(inv -> {
            pending.add(new CompletableFuture<>());
            sends.incrementAndGet();
            firstSendDispatched.countDown();
            return pending.get(pending.size() - 1);
        });
        when(service.getAckTopic(any(String.class))).thenReturn("test-topic-ack");

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
                for (int i = 0; i < msgList.size(); i++) {
                    responses.add(new Acknowledge("reply-" + i));
                }
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                new MessageSubscriptionConfig("test-topic", handler, options), 100L, 100L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(0, "a"), record(1, "b"), record(2, "c")));

        Thread runner = new Thread(() -> {
            try {
                task.batchConsume();
            } catch (Exception e) {
                // asserted via commit below
            }
        });
        runner.start();

        // All three replies dispatched while NONE has completed yet — pipelining.
        assertTrue(firstSendDispatched.await(5, TimeUnit.SECONDS));
        long deadline = System.currentTimeMillis() + 5000;
        while (sends.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(3, sends.get(), "all replies must be dispatched before any completes");
        verify(consumer, org.mockito.Mockito.never()).commitSync(
                ArgumentMatchers.<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>any());

        // Release all replies: the waitAll barrier lets the batch finalize and commit.
        for (CompletableFuture<Void> f : pending) {
            f.complete(null);
        }
        runner.join(5000);
        assertFalse(runner.isAlive(), "batchConsume must return after waitAll");
        verify(consumer).commitSync(ArgumentMatchers
                .<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>any());
    }

    @Test
    void failedReplyFuturePropagatesThroughWaitAllIntoBatchRewind() throws Exception {
        when(service.sendAsync(any(String.class), any(), any())).thenReturn(
                CompletableFuture.failedFuture(new NopException(KafkaErrors.ERR_KAFKA_SEND_FAILED)
                        .description("buffer full probe")));
        when(service.getAckTopic(any(String.class))).thenReturn("test-topic-ack");

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
                responses.add(new Acknowledge("reply"));
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                new MessageSubscriptionConfig("test-topic", handler, options), 100L, 100L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(3, "a")));

        // Buffer-full probe: the failed reply surfaces (no silent drop, no commit).
        assertThrows(Exception.class, task::batchConsume);
        verify(consumer).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer, org.mockito.Mockito.never()).commitSync(
                ArgumentMatchers.<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>any());
    }
}
