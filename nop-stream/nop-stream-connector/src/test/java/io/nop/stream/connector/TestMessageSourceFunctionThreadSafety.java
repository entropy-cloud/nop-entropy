package io.nop.stream.connector;

import io.nop.api.core.message.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
}
