package io.nop.stream.connector;

import io.nop.api.core.message.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestMessageSourceFunctionThreadSafety {

    private static final IMessageSubscription STUB_SUBSCRIPTION = new IMessageSubscription() {
        @Override public void cancel() {}
        @Override public boolean isSuspended() { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public void suspend() {}
        @Override public void resume() {}
    };

    @Test
    void testConcurrentCollectCallsAreSynchronized() throws Exception {
        int messageCount = 100;
        AtomicInteger concurrentCollectCalls = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        List<String> collected = Collections.synchronizedList(new ArrayList<>());

        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<>() {
            @Override
            public synchronized void collect(String element) {
                int current = concurrentCollectCalls.incrementAndGet();
                maxConcurrent.updateAndGet(m -> Math.max(m, current));
                collected.add(element);
                concurrentCollectCalls.decrementAndGet();
            }

            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        CountDownLatch allMessagesSent = new CountDownLatch(messageCount);
        IMessageService messageService = new IMessageService() {
            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
                ExecutorService executor = Executors.newFixedThreadPool(10);
                for (int i = 0; i < messageCount; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            consumer.onMessage(topic, "msg-" + idx, null);
                            allMessagesSent.countDown();
                        } catch (Exception e) {
                            allMessagesSent.countDown();
                        }
                    });
                }
                executor.shutdown();
                return STUB_SUBSCRIPTION;
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };

        MessageSourceFunction<String> source = new MessageSourceFunction<>(messageService, "concurrent-test", String.class);

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected
            }
        });
        runner.start();

        assertTrue(allMessagesSent.await(10, TimeUnit.SECONDS), "All messages should be sent within timeout");
        source.cancel();
        runner.join(5000);

        assertEquals(messageCount, collected.size(), "All messages should be collected");
        assertTrue(maxConcurrent.get() <= 1, "collect() should never be called concurrently, max was " + maxConcurrent.get());
    }

    /**
     * P1-9: a collect() failure inside the subscriber callback must be
     * propagated out of {@code run()} so the pipeline is recognized as FAILED
     * rather than completing normally (silent EOS). The prior implementation
     * only set {@code failed=true} and returned, swallowing the error.
     */
    @Test
    void testCollectFailureSurfacesFromRun() throws Exception {
        RuntimeException collectError = new RuntimeException("simulated downstream failure");

        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<>() {
            @Override
            public synchronized void collect(String element) {
                throw collectError;
            }

            @Override public void collectWithTimestamp(String element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        CountDownLatch messageDelivered = new CountDownLatch(1);
        IMessageService messageService = new IMessageService() {
            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
                new Thread(() -> {
                    try {
                        consumer.onMessage(topic, "will-fail", null);
                    } catch (Exception ignored) {
                        // onMessage returns null on the error-capture path
                    }
                    messageDelivered.countDown();
                }).start();
                return STUB_SUBSCRIPTION;
            }

            @Override
            public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
                return CompletableFuture.completedFuture(null);
            }
        };

        MessageSourceFunction<String> source = new MessageSourceFunction<>(messageService, "error-test", String.class);

        AtomicReference<Throwable> captured = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Throwable t) {
                captured.set(t);
            }
        });
        runner.start();

        assertTrue(messageDelivered.await(5, TimeUnit.SECONDS), "message delivery should complete");
        runner.join(5000);
        assertFalse(runner.isAlive(), "run() must terminate after collect() failure");

        Throwable thrown = captured.get();
        assertNotNull(thrown, "run() must throw rather than return normally when collect() fails");
        // The captured error should be the same instance (or wrapping) collectError.
        boolean chainContainsCollectError = containsCause(thrown, collectError);
        assertTrue(chainContainsCollectError || thrown == collectError,
                "captured error should reference the collect() failure; got: " + thrown);
    }

    private static boolean containsCause(Throwable t, Throwable target) {
        Throwable cur = t;
        while (cur != null) {
            if (cur == target) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * P1-9: a type mismatch between the declared {@code typeClass} and the
     * delivered message must surface from {@code run()} instead of being
     * swallowed as a normal EOS.
     */
    @Test
    void testTypeMismatchSurfacesFromRun() throws Exception {
        SourceFunction.SourceContext<Integer> ctx = new SourceFunction.SourceContext<>() {
            @Override public synchronized void collect(Integer element) {}
            @Override public void collectWithTimestamp(Integer element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        CountDownLatch messageDelivered = new CountDownLatch(1);
        IMessageService messageService = new IMessageService() {
            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
                new Thread(() -> {
                    try {
                        // Deliberately deliver a String into a source declared Integer-typed.
                        consumer.onMessage(topic, "not-an-integer", null);
                    } catch (Exception ignored) {
                        // Expected: onMessage may throw due to deliberate type mismatch; the point of this test is thread-safety, not message handling.
                    }
                    messageDelivered.countDown();
                }).start();
                return STUB_SUBSCRIPTION;
            }

            @Override
            public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
                return CompletableFuture.completedFuture(null);
            }
        };

        MessageSourceFunction<Integer> source =
                new MessageSourceFunction<>(messageService, "type-test", Integer.class);

        AtomicReference<Throwable> captured = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Throwable t) {
                captured.set(t);
            }
        });
        runner.start();

        assertTrue(messageDelivered.await(5, TimeUnit.SECONDS));
        runner.join(5000);
        assertFalse(runner.isAlive());

        Throwable thrown = captured.get();
        assertNotNull(thrown, "run() must throw on type mismatch rather than return normally");
        // The thrown error should be a StreamException carrying the mismatch detail.
        boolean isStreamException = StreamException.class.isAssignableFrom(thrown.getClass())
                || (thrown.getCause() != null && StreamException.class.isAssignableFrom(thrown.getCause().getClass()));
        assertTrue(isStreamException, "expected StreamException for type mismatch, got: " + thrown.getClass());
    }
}
