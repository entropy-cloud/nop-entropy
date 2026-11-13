/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑤ acceptance pin — W-05 face① (finding
 * 1.2-C3-012; adjudication C.1 / seam S5): on the close() join-timeout path the
 * typed failure must NOT skip the {@code queue.clear()}, and the runner must get
 * a single-shot interrupt. Pre-fix the throw fired BEFORE the clear, so a
 * sender blocked in {@code queue.put} on the full, abandoned queue stayed
 * blocked forever (the Local-bus sender is a business thread — no interrupt
 * ever reaches it) and the runner itself was never interrupted.
 *
 * <p>Three legs: (a) close() still fails typed with the unchanged timeout
 * message (the timeout signal is preserved, only its ordering/neighborhood
 * changes); (b) the blocked sender's put RETURNS within the release budget —
 * proof that clear() actually ran (a freed slot is the only channel that can
 * release an in-flight put); (c) the parked runner was interrupted and
 * terminated (the collect InterruptedException branch becomes reachable; its
 * body is untouched).
 *
 * <p>Drive form: the embedded stub publishes the reader's real
 * QueueSourceContext, parks forever (simulating a runner stuck inside
 * source.stop()), and the test's own sender thread drives the queue to full and
 * then into the blocking put — the exact production hang shape.
 */
class TestMessageSourceReaderCloseR404 {

    private static final class ParkedStubFunction implements SourceFunction<String> {
        final AtomicReference<SourceFunction.SourceContext<String>> publishedContext =
                new AtomicReference<>();
        final CountDownLatch contextPublished = new CountDownLatch(1);
        final CountDownLatch runnerTerminated = new CountDownLatch(1);

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            publishedContext.set(ctx);
            contextPublished.countDown();
            try {
                // parked forever = the runner is stuck inside source.stop(); the only
                // exit is the interrupt delivered by the close path
                new CountDownLatch(1).await();
            } finally {
                runnerTerminated.countDown();
            }
        }

        @Override
        public void cancel() {
            // the production cancel cannot unstick a source blocked in stop(); the
            // close path's own join/interrupt/clear sequence is the face under test
        }
    }

    @Test
    void closeJoinTimeoutPreservesTypedSignalAndReleasesTheBlockedSender() throws Exception {
        ParkedStubFunction stub = new ParkedStubFunction();
        MessageSourceReader<String> reader = new MessageSourceReader<>(stub);
        reader.addSplits(List.of(new MessageTopicSplit("r404-topic", 0)));
        reader.start();
        assertTrue(stub.contextPublished.await(5, TimeUnit.SECONDS),
                "the runner must publish the reader's source context");

        // ---- drive the queue to full and then into the blocking put ----
        AtomicBoolean putReturned = new AtomicBoolean(false);
        AtomicBoolean senderInPut = new AtomicBoolean(false);
        SourceFunction.SourceContext<String> ctx = stub.publishedContext.get();
        Thread sender = new Thread(() -> {
            for (int i = 0; i < MessageSourceReader.QUEUE_CAPACITY; i++) {
                ctx.collect("filler-" + i);
            }
            senderInPut.set(true);
            ctx.collect("the-record-that-blocks");
            putReturned.set(true);
        });
        sender.setDaemon(true);
        sender.start();
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!senderInPut.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(senderInPut.get(), "the sender must reach the blocking put");
        Thread.sleep(200); // settle: the 257th put is parked on the full queue
        assertTrue(sender.isAlive(), "the sender must be parked inside queue.put");

        // ---- close(): join times out (5s) inside the reader ----
        StreamException closeFailure = assertThrows(StreamException.class, reader::close,
                "the join timeout must keep failing typed (signal preserved)");
        assertTrue(String.valueOf(closeFailure).contains("did not terminate within"),
                "the timeout message must be unchanged, got: " + closeFailure);

        // ---- leg (b): the blocked sender must be released by the clear ----
        long releaseAt = System.currentTimeMillis();
        deadline = System.currentTimeMillis() + 5_000L;
        while (!putReturned.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(putReturned.get(),
                "the sender's in-flight put must return after close() — pre-fix the typed "
                        + "throw skipped queue.clear() and the put stayed blocked forever "
                        + "(CRA 8.44 R4-04 W-05 red⑤ leg b, released in "
                        + (System.currentTimeMillis() - releaseAt) + "ms)");

        // ---- leg (c): the parked runner was interrupted and terminated ----
        assertTrue(stub.runnerTerminated.await(5, TimeUnit.SECONDS),
                "the parked runner must be interrupted and terminate on the close path "
                        + "(pre-fix: never interrupted, parked forever — red⑤ leg c)");
    }
}
