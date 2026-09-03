/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items 28+31 (D2, Phase 2 focused tests): bounded-enqueue queue-full
 * semantics of {@link RemoteInputChannel}.
 *
 * <p>Anchors (from the design decision, {@code dataplane-transport-design.md} §三):
 * <ul>
 *   <li>no reader + full queue → the dispatch thread (here: the synchronous
 *       sender of LocalMessageService, which plays the dispatch role) is NOT
 *       blocked forever; the channel flags a typed overflow failure that the
 *       reader surfaces as {@link NopStreamErrors#ERR_STREAM_CHANNEL_OVERFLOW}
 *       — observable, never a clean end-of-stream (silent truncation);</li>
 *   <li>healthy reader → in-order delivery, no loss, no duplication;</li>
 *   <li>healthy SLOW reader (slot freed continuously) → bounded waits succeed,
 *       no overflow false-positive (dispatch-level backpressure preserved —
 *       the BP-1 500ms-throttle regime);</li>
 *   <li>overflow observability: {@code isOverflowed()} is queryable even with
 *       no reader ever calling read().</li>
 * </ul>
 */
class TestRemoteInputChannelQueueFull {

    private static final String TOPIC = "nop-stream.job-q.edge-q.0.0";
    private static final long EPOCH = 1L;

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    /** Delivers a STREAM_RECORD envelope synchronously through the message service (the dispatch path). */
    private void deliver(RemoteInputChannel channel, String value) {
        StreamRecord<String> record = new StreamRecord<>(value);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(record, String.class.getName(), EPOCH);
        long start = System.nanoTime();
        messageService.send(TOPIC, envelope);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Guard assertion helper: the sender (dispatch role) must never block
        // unbounded — a hard bound here would be the enqueue timeout itself;
        // individual tests assert the concrete expected bounds.
        assertTrue(elapsedMs < 60_000L, "dispatch must not block unbounded (took " + elapsedMs + "ms)");
    }

    @Test
    void fullQueueWithNoReaderFailsTypedInsteadOfBlockingForever() throws Exception {
        // Small capacity + short bounded window keeps the test fast while
        // exercising the exact production semantics.
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 2, 0L, 300L, true);

        // Fill the queue (2 slots) with no reader, then keep delivering.
        deliver(channel, "a");
        deliver(channel, "b");
        assertFalse(channel.isOverflowed(), "within-capacity deliveries must not flag overflow");

        long start = System.nanoTime();
        deliver(channel, "c"); // queue full, no consumer progress → must time out (≈300ms), not hang
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMs >= 250L && elapsedMs < 5_000L,
                "the overflowing delivery must block ~the bounded window (300ms), observed " + elapsedMs + "ms");
        assertTrue(channel.isOverflowed(),
                "channel must flag the typed overflow failure (observable without a reader)");
        assertTrue(channel.isFinished(), "overflow failure finishes the channel");

        // A reader arriving later surfaces the TYPED error — never a clean EOS
        // (a null return here would silently acknowledge truncated data).
        StreamException ex = assertThrows(StreamException.class, channel::read);
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW.getErrorCode(), ex.getErrorCode(),
                "reader must surface ERR_STREAM_CHANNEL_OVERFLOW, got: " + ex.getMessage());
        assertEquals(300L, ex.getParam(NopStreamErrors.ARG_TIMEOUT_MS));
        assertEquals(TOPIC, ex.getParam(NopStreamErrors.ARG_TOPIC));

        channel.close();
    }

    @Test
    void healthyReaderReceivesEverythingInOrderNoLossNoDuplication() throws Exception {
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 4, 0L, 300L, true);

        int total = 50;
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    StreamElement e = channel.read(5, TimeUnit.SECONDS);
                    if (e == null) {
                        break;
                    }
                    received.add(((StreamRecord<String>) e).getValue());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();

        for (int i = 0; i < total; i++) {
            deliver(channel, "v" + i);
        }
        reader.join(10_000L);
        assertFalse(reader.isAlive(), "reader must drain and exit");
        assertEquals(total, received.size(), "no loss");
        for (int i = 0; i < total; i++) {
            assertEquals("v" + i, received.get(i), "in-order delivery at index " + i);
        }
        assertFalse(channel.isOverflowed(), "a draining consumer must never trip the overflow failure");
        channel.close();
    }

    @Test
    void healthySlowReaderDoesNotTripOverflow_boundedWaitsSucceedAsSlotsFree() throws Exception {
        // BP-1 regime: consumer drains slower than the producer fills, but it
        // DOES make continuous progress — every offer succeeds within the
        // window (dispatch-level backpressure), no typed failure.
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 2, 0L, 2_000L, true);

        int total = 12;
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        Thread slowReader = new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    StreamElement e = channel.read(10, TimeUnit.SECONDS);
                    if (e == null) {
                        return;
                    }
                    received.add(((StreamRecord<String>) e).getValue());
                    Thread.sleep(50L); // slow but continuous drain
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                readerError.set(t);
            }
        });
        slowReader.start();

        for (int i = 0; i < total; i++) {
            deliver(channel, "s" + i); // producer faster than consumer → queue repeatedly full
        }
        slowReader.join(30_000L);
        assertNull(readerError.get(), "slow-but-healthy reader must not observe a typed failure: " + readerError.get());
        assertFalse(channel.isOverflowed(), "continuous progress within the window must never trip overflow");
        assertEquals(total, received.size(), "no loss under sustained backpressure");
        for (int i = 0; i < total; i++) {
            assertEquals("s" + i, received.get(i), "in-order delivery at index " + i);
        }
        channel.close();
    }

    private final List<String> received = new ArrayList<>();
}
