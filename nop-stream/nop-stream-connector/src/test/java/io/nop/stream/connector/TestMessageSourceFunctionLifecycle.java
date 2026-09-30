/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 368 Phase 4 (audit R5-CON-04): {@link MessageSourceFunction} must never leak a
 * live message subscription after {@code run()} returns.
 *
 * <p>Two leak paths are pinned here:
 * <ol>
 *   <li><strong>cancel/subscribe race</strong>: {@code cancel()} landing inside the
 *       {@code subscribe()} window reads a null/stale subscription field and cancels
 *       nothing; previously run() then stored the new subscription and returned, leaking
 *       the consumer thread + group membership forever.</li>
 *   <li><strong>failed exit</strong>: a type-mismatch/collect failure makes run()
 *       throw; the subscription created by that same run() must be cancelled on that
 *       exit path too.</li>
 * </ol>
 */
class TestMessageSourceFunctionLifecycle {

    /** Recording subscription: counts cancel() invocations. */
    static class RecordingSubscription implements IMessageSubscription {
        final AtomicInteger cancelCount = new AtomicInteger();

        @Override public void cancel() {
            cancelCount.incrementAndGet();
        }

        @Override public boolean isSuspended() {
            return false;
        }

        @Override public boolean isCancelled() {
            return cancelCount.get() > 0;
        }

        @Override public void suspend() {
        }

        @Override public void resume() {
        }
    }

    private static SourceFunction.SourceContext<String> noopCtx() {
        return new SourceFunction.SourceContext<>() {
            @Override public void collect(String element) {
            }

            @Override public void collectWithTimestamp(String element, long timestamp) {
            }

            @Override public void emitWatermark(long mark) {
            }

            @Override public void markAsTemporarilyIdle() {
            }

            @Override public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    /**
     * Race window: cancel() fires DURING subscribe() — before run() can publish the
     * subscription to the field — so cancel() cannot see this run's subscription. run()
     * must still exit promptly and unsubscribe the subscription it created.
     */
    @Test
    void testCancelDuringSubscribeDoesNotLeakSubscription() throws Exception {
        RecordingSubscription subscription = new RecordingSubscription();
        CountDownLatch runEntered = new CountDownLatch(1);
        // Holder so the stub subscribe() (invoked later, during run()) can reach the
        // source instance without capturing it in its own initializer.
        final MessageSourceFunction<String>[] holder = new MessageSourceFunction[1];

        IMessageService service = new IMessageService() {
            @Override
            public IMessageSubscription subscribe(String topic, IMessageConsumer consumer,
                                                  MessageSubscribeOptions options) {
                runEntered.countDown();
                // cancel() lands inside the subscribe window, before run() publishes
                // the subscription to its field.
                holder[0].cancel();
                return subscription;
            }

            @Override
            public CompletionStage<Void> sendAsync(String topic, Object message,
                                                   MessageSendOptions options) {
                return CompletableFuture.completedFuture(null);
            }
        };
        MessageSourceFunction<String> source = new MessageSourceFunction<>(
                service, "lifecycle-race", String.class);
        holder[0] = source;

        final CountDownLatch runFinished = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            try {
                source.run(noopCtx());
            } catch (Exception e) {
                // normal cancel-driven exit
            } finally {
                runFinished.countDown();
            }
        });
        runner.start();

        assertTrue(runEntered.await(5, TimeUnit.SECONDS), "run must enter subscribe()");
        assertTrue(runFinished.await(10, TimeUnit.SECONDS),
                "run() must exit promptly after the in-window cancel (not block forever)");
        runner.join(5_000);

        assertEquals(1, subscription.cancelCount.get(),
                "the subscription created inside the cancel window must be unsubscribed "
                        + "exactly via run()'s own cleanup (R5-CON-04 leak path 1)");
    }

    /**
     * Failed exit: a type-mismatch failure surfaces from run() (P1-9) AND the
     * subscription of that run is cancelled (R5-CON-04 leak path 2) — no orphaned
     * consumer stays subscribed after the task failed.
     */
    @Test
    void testFailedExitUnsubscribesSubscription() throws Exception {
        RecordingSubscription subscription = new RecordingSubscription();

        MessageSourceFunction<String> source = new MessageSourceFunction<>(
                new IMessageService() {
                    @Override
                    public IMessageSubscription subscribe(String topic, IMessageConsumer consumer,
                                                          MessageSubscribeOptions options) {
                        // Deliver one mismatched message synchronously: an Integer for a
                        // String-typed source triggers the P1-9 capture-and-rethrow path.
                        consumer.onMessage(topic, 42, null);
                        return subscription;
                    }

                    @Override
                    public CompletionStage<Void> sendAsync(String topic, Object message,
                                                           MessageSendOptions options) {
                        return CompletableFuture.completedFuture(null);
                    }
                }, "lifecycle-failed", String.class);

        StreamException failure = assertThrows(StreamException.class, () -> source.run(noopCtx()),
                "the type-mismatch failure must surface from run() as a typed StreamException");
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH.getErrorCode(),
                failure.getErrorCode());
        assertEquals(1, subscription.cancelCount.get(),
                "the failed run must unsubscribe its own subscription (R5-CON-04 leak path 2)");
    }
}
