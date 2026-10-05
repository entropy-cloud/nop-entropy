/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang.impl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI20 (MSG-F-25): {@code Cancellable.cancel} is an atomic check-and-set — concurrent
 * cancels have exactly one winner, the callback list is consumed exactly once, and the
 * cancelReason attribution is deterministic.
 */
class TestCancellable {

    @Test
    void cancelRunsCallbackOnceAndSetsReason() {
        Cancellable cancellable = new Cancellable();
        AtomicInteger runs = new AtomicInteger();
        cancellable.appendOnCancel(reason -> runs.incrementAndGet());

        cancellable.cancel("first");
        cancellable.cancel("second");

        assertEquals(1, runs.get(), "repeat cancel must not re-run callbacks");
        assertEquals("first", cancellable.getCancelReason());
        assertTrue(cancellable.isCancelled());
    }

    @Test
    void appendAfterCancelRunsImmediately() {
        Cancellable cancellable = new Cancellable();
        AtomicInteger runs = new AtomicInteger();
        cancellable.cancel("done");
        cancellable.appendOnCancel(reason -> runs.incrementAndGet());
        assertEquals(1, runs.get(), "a callback registered after cancel must run at once");
    }

    @Test
    void concurrentCancelsHaveOneWinnerAndDeterministicReason() throws Exception {
        Cancellable cancellable = new Cancellable();
        AtomicInteger runs = new AtomicInteger();
        cancellable.appendOnCancel(reason -> runs.incrementAndGet());

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final String reason = "reason-" + i;
            new Thread(() -> {
                try {
                    barrier.await();
                    cancellable.cancel(reason);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();

        assertEquals(0, failures.get());
        assertEquals(1, runs.get(), "callback must be consumed exactly once");
        assertNotNull(cancellable.getCancelReason());
        assertTrue(cancellable.getCancelReason().startsWith("reason-"),
                "reason must be one of the submitted values, got: " + cancellable.getCancelReason());
        assertTrue(cancellable.isCancelled());
    }

    @Test
    void freshCancellableIsUncancelledWithNullReason() {
        Cancellable cancellable = new Cancellable();
        assertNull(cancellable.getCancelReason());
        assertEquals(false, cancellable.isCancelled());
    }
}
