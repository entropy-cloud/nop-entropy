package io.nop.stream.connector;

import io.nop.api.core.message.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests resource-management behavior for the base-connector MessageSourceFunction.
 * Debezium-specific resource tests live in {@code nop-stream-connector-debezium}.
 */
class TestConnectorResourceManagement {

    @Test
    void testMessageSourceFunctionVolatileSubscription() throws Exception {
        IMessageService messageService = new SimpleTestMessageService();
        MessageSourceFunction<String> source = new MessageSourceFunction<>(messageService, "test-topic", String.class);

        CopyOnWriteArrayList<String> collected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) { collected.add(element); }
            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        CountDownLatch started = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            try {
                started.countDown();
                source.run(ctx);
            } catch (Exception e) {
                // expected on cancel
            }
        });
        runner.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));

        source.cancel();
        runner.join(5000);
    }

    @Test
    void testMessageSourceFunctionCollectExceptionSetsFailedFlag() throws Exception {
        IMessageService messageService = new FailingTestMessageService();
        MessageSourceFunction<String> source = new MessageSourceFunction<>(messageService, "fail-topic", String.class);

        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<String>() {
            @Override public void collect(String element) { throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "collect failure"); }
            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected
            }
        });
        runner.start();
        runner.join(10000);
        assertFalse(runner.isAlive(), "Runner should exit after collect failure sets failed flag");
    }

    private static final IMessageSubscription STUB_SUBSCRIPTION = new IMessageSubscription() {
        @Override public void cancel() {}
        @Override public boolean isSuspended() { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public void suspend() {}
        @Override public void resume() {}
    };

    private static class SimpleTestMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
            return STUB_SUBSCRIPTION;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static class FailingTestMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    consumer.onMessage(topic, "fail-msg", null);
                } catch (Exception e) {
                    // ignore
                }
            }).start();
            return STUB_SUBSCRIPTION;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
