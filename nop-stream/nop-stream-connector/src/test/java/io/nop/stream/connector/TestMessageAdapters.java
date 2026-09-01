/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Test;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

public class TestMessageAdapters {

    /** Purely event-driven wait: returns as soon as the condition holds; the deadline only guards against hangs. */
    private static void awaitUntil(String message, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail(message + " (condition not met within 30s)");
    }

    private <T> SourceFunction.SourceContext<T> collectingContext(List<T> target) {
        return new SourceFunction.SourceContext<>() {
            @Override
            public void collect(T element) {
                target.add(element);
            }

            @Override
            public void collectWithTimestamp(T element, long timestamp) {
                target.add(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    @Test
    void testMessageSinkSendsMessages() {
        LocalMessageService messageService = new LocalMessageService();
        List<Object> received = new ArrayList<>();
        messageService.subscribe("test-topic", (topic, msg, context) -> {
            received.add(msg);
            return null;
        });

        MessageSinkFunction<String> sink = new MessageSinkFunction<>(messageService, "test-topic");

        sink.consume("hello");
        sink.consume("world");

        assertEquals(List.of("hello", "world"), received);
    }

    @Test
    void testMessageSourceReceivesMessages() throws Exception {
        LocalMessageService messageService = new LocalMessageService();
        List<Object> collected = new CopyOnWriteArrayList<>();

        MessageSourceFunction<Object> source = new MessageSourceFunction<>(messageService, "test-topic");

        Thread runner = new Thread(() -> {
            try {
                source.run(collectingContext(collected));
            } catch (Exception e) {
                throw new StreamException("Source.run() failed", e);
            }
        });
        runner.start();

        // Wait until the source has actually subscribed (event-driven, no fixed sleep).
        awaitUntil("source must subscribe before messages are sent",
                () -> !messageService.getConsumers().getOrDefault("test-topic", java.util.Collections.emptyList()).isEmpty());

        messageService.send("test-topic", "msg1");
        messageService.send("test-topic", "msg2");

        source.cancel();
        runner.join(5000);

        assertFalse(runner.isAlive());
        assertEquals(List.of("msg1", "msg2"), collected);
    }

    @Test
    void testMessageSourceCancelUnsubscribes() throws Exception {
        LocalMessageService messageService = new LocalMessageService();
        List<Object> collected = new CopyOnWriteArrayList<>();

        MessageSourceFunction<Object> source = new MessageSourceFunction<>(messageService, "cancel-topic");

        Thread runner = new Thread(() -> {
            try {
                source.run(collectingContext(collected));
            } catch (Exception e) {
                throw new StreamException("Source.run() failed", e);
            }
        });
        runner.start();

        // Wait for the subscription to be established before asserting on it.
        awaitUntil("source must subscribe before assertions",
                () -> !messageService.getConsumers().getOrDefault("cancel-topic", java.util.Collections.emptyList()).isEmpty());
        assertFalse(messageService.getConsumers().get("cancel-topic").isEmpty());

        source.cancel();
        runner.join(5000);
        assertFalse(runner.isAlive());

        assertTrue(messageService.getConsumers().get("cancel-topic").isEmpty());
    }

    @Test
    void testNullArgumentsRejected() {
        assertThrows(StreamException.class,
                () -> new MessageSourceFunction<>(null, "topic"));
        assertThrows(StreamException.class,
                () -> new MessageSinkFunction<>(new LocalMessageService(), null));
    }

    /**
     * Roadmap item 10 audit fix CN-7: the message sink rejects null values at the
     * boundary (typed fail-fast) instead of deferring the failure to the message
     * backend, mirroring the other connectors' data contract.
     */
    @Test
    void testSinkRejectsNullValueAtBoundary() {
        MessageSinkFunction<String> sink = new MessageSinkFunction<>(new LocalMessageService(), "null-sink-topic");
        StreamException ex = assertThrows(StreamException.class, () -> sink.consume(null));
        assertTrue(ex.getMessage().contains("value") || ex.getMessage().contains("null"),
                "rejection must identify the offending argument");
    }

    @Test
    void testDeserializedSourceRunDoesNotThrowNPE() throws Exception {
        LocalMessageService messageService = new LocalMessageService();
        MessageSourceFunction<Object> source = new MessageSourceFunction<>(messageService, "ser-topic");

        java.lang.reflect.Field latchField = MessageSourceFunction.class.getDeclaredField("shutdownLatch");
        latchField.setAccessible(true);
        latchField.set(source, null);

        List<Object> collected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<Object> ctx = collectingContext(collected);

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                throw new StreamException("run() with null shutdownLatch failed", e);
            }
        });
        runner.start();

        // Wait for the subscribe call to have registered the consumer before cancel
        // (event-driven; also proves run() got past the null-latch re-init).
        awaitUntil("source must subscribe before cancel",
                () -> !messageService.getConsumers().getOrDefault("ser-topic", java.util.Collections.emptyList()).isEmpty());

        source.cancel();
        runner.join(5000);
        assertFalse(runner.isAlive(), "Source with null shutdownLatch should complete after cancel without NPE");
    }
}
