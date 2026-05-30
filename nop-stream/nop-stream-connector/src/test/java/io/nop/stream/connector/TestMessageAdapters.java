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

import static org.junit.jupiter.api.Assertions.*;

public class TestMessageAdapters {

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

        Thread.sleep(100);
        messageService.send("test-topic", "msg1");
        messageService.send("test-topic", "msg2");
        Thread.sleep(200);

        source.cancel();
        runner.join(2000);

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

        Thread.sleep(100);
        assertFalse(messageService.getConsumers().get("cancel-topic").isEmpty());

        source.cancel();
        runner.join(2000);
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
        Thread.sleep(100);

        source.cancel();
        runner.join(2000);
        assertFalse(runner.isAlive(), "Source with null shutdownLatch should complete after cancel without NPE");
    }
}
