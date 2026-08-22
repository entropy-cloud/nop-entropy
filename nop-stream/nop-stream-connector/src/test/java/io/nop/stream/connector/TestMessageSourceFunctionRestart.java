/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;

import io.nop.stream.core.common.functions.source.SourceFunction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (region restart): after a region restart, the runtime cancels the old
 * task ({@code StreamSourceOperator.close()} → {@code cancel()}, running=false) and then
 * rebuilds the task with a deep-copied operator chain that SHARES the source function
 * instance and calls {@code run()} again. {@code run()} must reset the lifecycle flags
 * so the source re-enters its wait loop and keeps delivering messages, instead of
 * returning immediately (silent EOS, stalled data flow).
 */
public class TestMessageSourceFunctionRestart {

    private static final IMessageSubscription STUB_SUBSCRIPTION = new IMessageSubscription() {
        @Override public void cancel() {}
        @Override public boolean isSuspended() { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public void suspend() {}
        @Override public void resume() {}
    };

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(20);
        }
    }

    @Test
    void testRunAfterCancelResumesDelivery() throws Exception {
        AtomicReference<IMessageConsumer> consumerRef = new AtomicReference<>();
        AtomicInteger subscribeCount = new AtomicInteger();
        IMessageService messageService = new IMessageService() {
            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer consumer,
                                                  MessageSubscribeOptions options) {
                subscribeCount.incrementAndGet();
                consumerRef.set(consumer);
                return STUB_SUBSCRIPTION;
            }

            @Override
            public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
                return CompletableFuture.completedFuture(null);
            }
        };

        List<String> collected = Collections.synchronizedList(new ArrayList<>());
        SourceFunction.SourceContext<String> ctx = new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) { collected.add(element); }
            @Override public void collectWithTimestamp(String element, long timestamp) { collected.add(element); }
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        MessageSourceFunction<String> source = new MessageSourceFunction<>(messageService, "restart-test", String.class);

        // ---- First run: subscribe, deliver one message, cancel (region restart Phase 1) ----
        Thread runner1 = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception ignored) {
                // cancel path
            }
        });
        runner1.start();
        await(() -> subscribeCount.get() >= 1, "first run must subscribe");
        consumerRef.get().onMessage("restart-test", "m1", (IMessageConsumeContext) null);
        await(() -> collected.contains("m1"), "first message must be delivered");

        source.cancel();
        runner1.join(5000);
        assertFalse(runner1.isAlive(), "first run must terminate after cancel()");

        // ---- Region restart Phase 3: the rebuilt task re-runs the SAME shared instance ----
        Thread runner2 = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // surface unexpected failure
                throw new IllegalStateException("restarted run failed", e);
            }
        });
        runner2.start();
        Thread.sleep(300);
        assertTrue(runner2.isAlive(),
                "run() after cancel() must re-enter the wait loop instead of returning immediately (silent EOS)");
        await(() -> subscribeCount.get() >= 2, "restarted run must re-subscribe");

        consumerRef.get().onMessage("restart-test", "m2", (IMessageConsumeContext) null);
        await(() -> collected.contains("m2"), "message delivered after restart must reach the context");

        source.cancel();
        runner2.join(5000);
        assertFalse(runner2.isAlive());
        assertEquals(List.of("m1", "m2"), collected);
    }
}
