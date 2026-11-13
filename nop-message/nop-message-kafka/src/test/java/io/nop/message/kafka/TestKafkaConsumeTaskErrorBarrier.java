package io.nop.message.kafka;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.ConsumeFailure;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C3-009 barrier ① regression (plan 2496 Phase 4): a deterministic Error escaping the
 * user handler must not leave the Kafka subscription as a permanent zombie. Pre-fix
 * {@code runTask} caught only {@code Exception} — the Error escaped the loop, the consume
 * thread died, {@code active} stayed true and {@code isActive()} misreported the dead
 * subscription as healthy, with no {@code onConsumeFailure} report. Post-fix the Error
 * branch reports the failure through the same path as Exceptions and exits with
 * {@code active=false} so the health check no longer lies.
 */
class TestKafkaConsumeTaskErrorBarrier {

    static class RecordingConsumer implements IMessageConsumer {
        final List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();

        @Override
        public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext context) {
            throw new LinkageError("injected handler Error");
        }

        @Override
        public void onConsumeFailure(ConsumeFailure failure) {
            failures.add(failure);
        }
    }

    private static ConsumerRecord<String, String> record(long offset, String value) {
        return new ConsumerRecord<>("test-topic", 0, offset, null, value);
    }

    @Test
    void handlerErrorDeactivatesSubscriptionAndReportsFailure() throws Exception {
        KafkaMessageService service = mock(KafkaMessageService.class);
        Consumer<String, String> kafka = mock(Consumer.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordingConsumer handler = new RecordingConsumer();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig(
                "test-topic", handler, new MessageSubscribeOptions());

        org.apache.kafka.clients.consumer.ConsumerRecords<String, String> one = singleRecord(record(0, "boom"));
        when(kafka.poll(any(Duration.class)))
                .thenReturn(one)
                .thenAnswer(inv -> {
                    // Error round exits the loop; any further poll (defensive) blocks briefly.
                    Thread.sleep(50);
                    return org.apache.kafka.clients.consumer.ConsumerRecords.empty();
                });

        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, kafka, config, 10L, 100L);
        try {
            task.start();

            long deadline = System.currentTimeMillis() + 10_000;
            while (task.isActive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            // 订阅非僵尸：isActive() must stop misreporting the dead subscription.
            assertFalse(task.isActive(),
                    "an Error-killed consume loop must deactivate the subscription (no zombie)");

            // Error 分支走失败上报路径：the user's onConsumeFailure must have received the
            // failure (the raw Error wrapped, cause attached).
            assertEquals(1, handler.failures.size(),
                    "the Error must be reported through onConsumeFailure");
            ConsumeFailure failure = handler.failures.get(0);
            assertTrue(failure.getThrowable().getCause() instanceof LinkageError,
                    "the reported failure must carry the injected Error as cause: "
                            + failure.getThrowable());
        } finally {
            task.stop();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * C3-009 barrier ⑥ regression (plan 2496 Phase 4): the {@code onConsumeFailure}
     * callback itself throwing an Error must be contained by the dispatch guard
     * ({@code nop.err.message.kafka.failure-callback-throw}) — pre-fix the
     * Exception-only guard let the callback Error escape into the consume thread on
     * top of the already-failing round. The loop must still exit honestly
     * ({@code active=false}).
     */
    @Test
    void failureCallbackErrorIsContainedAndLoopStillExitsHonestly() throws Exception {
        KafkaMessageService service = mock(KafkaMessageService.class);
        Consumer<String, String> kafka = mock(Consumer.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        IMessageConsumer throwingCallback = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext context) {
                throw new LinkageError("injected handler Error");
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                throw new LinkageError("injected failure-callback Error");
            }
        };
        MessageSubscriptionConfig config = new MessageSubscriptionConfig(
                "test-topic", throwingCallback, new MessageSubscribeOptions());

        org.apache.kafka.clients.consumer.ConsumerRecords<String, String> one = singleRecord(record(0, "boom"));
        when(kafka.poll(any(Duration.class)))
                .thenReturn(one)
                .thenAnswer(inv -> {
                    Thread.sleep(50);
                    return org.apache.kafka.clients.consumer.ConsumerRecords.empty();
                });

        Logger taskLogger = (Logger) LoggerFactory.getLogger(KafkaConsumeTask.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        taskLogger.addAppender(appender);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, kafka, config, 10L, 100L);
        try {
            task.start();

            long deadline = System.currentTimeMillis() + 10_000;
            while (task.isActive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            assertFalse(task.isActive(),
                    "the loop must still exit honestly when the failure callback itself throws an Error");
            boolean guardLogged = appender.list.stream().anyMatch(
                    e -> e.getFormattedMessage().contains("nop.err.message.kafka.failure-callback-throw"));
            assertTrue(guardLogged,
                    "the callback's Error must be contained and observed via the ⑥ guard log");
        } finally {
            taskLogger.detachAppender(appender);
            appender.stop();
            task.stop();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static org.apache.kafka.clients.consumer.ConsumerRecords<String, String> singleRecord(
            ConsumerRecord<String, String> rec) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = Map.of(
                new TopicPartition(rec.topic(), rec.partition()), List.of(rec));
        return new org.apache.kafka.clients.consumer.ConsumerRecords<>(map);
    }
}
