package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.buffer.IBufferPool;
import io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.testsupport.TestAwait;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAM-1 / C10-005 (plan 2510 R2-07, adjudication 10): the materialization-edge
 * overflow-bypass backpressure hole is closed.
 *
 * <p>Old contract (fake protection, rewritten in
 * {@code TestResultPartitionOverflowBypass} in the same diff): on a full queue the
 * producer silently released the permit and rerouted the record to the store only —
 * the IBufferPool bound was bypassed and the record was invisibly skipped by the
 * runtime consumer.
 *
 * <p>New contract (adjudication 10b/10c): pool + materialization quadrant uses a
 * permit-retaining blocking {@code queue.put} — the producer BLOCKS when the queue
 * is full (backpressure restored), permits are NOT released on overflow, and after
 * the consumer drains, every element reaches the consumer exactly-once. The
 * materialization store is a checkpoint-scoped recovery record plane (adjudication
 * 10a as revised by the combined-gate live evidence): unconfigured = no hard bound
 * (recovery plane must hold all in-flight uncommitted records, with once-per-tier
 * WARN at 1024/4096/...); an explicitly configured positive
 * {@code nop.stream.materialization.store-max-entries} enables the fail-loud bound.
 */
class TestFam1PermitRetentionC10_005 {

    private static final String STORE_MAX_ENTRIES_CONFIG =
            "nop.stream.materialization.store-max-entries";

    /**
     * Core red driver: queue full + pool + materialization → the producer must
     * BLOCK (not silently complete), permits must NOT be released, and after the
     * consumer drains, the element sequence must arrive exactly-once.
     */
    @Test
    void fam1_poolPlusMaterializationQueueFullBlocksProducerAndRetainsPermits() throws Exception {
        IBufferPool pool = new BufferPool(8);
        InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-fam1");
        ResultPartition partition = new ResultPartition(4, pool);
        partition.setMaterializationPoint(point);

        // Fill the queue: 4 elements, 4 permits held.
        for (int i = 0; i < 4; i++) {
            partition.write(new StreamRecord<>("fill-" + i));
        }
        assertEquals(4, pool.getGlobalUsage(), "fill elements hold 4 permits");
        assertEquals(0, partition.getAvailableCapacity());

        // 10 more elements: the producer must block on the 5th (queue full), and
        // the 4 acquired permits must stay retained (no overflow release).
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    partition.write(new StreamRecord<>("overflow-" + i));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        }, "fam1-producer");
        producer.start();

        // Negative window: with nobody draining, the producer must stay blocked.
        TestAwait.staysTrue("producer stays blocked on full materialization queue",
                () -> done.getCount() > 0 && producer.isAlive(), 300);
        assertTrue(producer.isAlive(),
                "FAM-1 red driver: producer must BLOCK when queue full + pool + materialization "
                        + "(permit-retaining put) — silent overflow completion = unfixed bypass");
        // Permit retention: 4 permits for the queued fill elements + 1 permit held
        // by the blocked put (a single producer thread has exactly one element in
        // flight). The old bypass released permits on queue-full — usage stayed 4.
        assertEquals(5, pool.getGlobalUsage(),
                "permits must be retained (4 queued + 1 held by the blocked put) — "
                        + "no overflow release");
        // Store mirrors the writes issued so far (dual-write precedes the blocking
        // put): 4 fill + the one in-flight overflow element.

        // Consumer drains everything; the producer unblocks and every element
        // arrives exactly-once, in order.
        List<String> received = new ArrayList<>();

        // Drain loop: read until EOS sentinel. The producer is unblocked by each read.
        Thread consumer = new Thread(() -> {
            try {
                StreamElement e;
                while ((e = partition.read(5, TimeUnit.SECONDS)) != null) {
                    received.add(e.<String>asRecord().getValue());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "fam1-consumer");
        consumer.start();

        assertTrue(done.await(10, TimeUnit.SECONDS),
                "producer must unblock once the consumer drains");
        assertNull(error.get(), "producer must finish all writes without error");

        // Finish the partition only AFTER the producer is done — closing earlier
        // would reject the producer's remaining writes (finished partition).
        partition.close();
        consumer.join(15_000);
        assertFalse(consumer.isAlive(), "consumer must reach EOS");

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            expected.add("fill-" + i);
        }
        for (int i = 0; i < 10; i++) {
            expected.add("overflow-" + i);
        }
        assertEquals(expected, received,
                "FAM-1 red driver: every element must reach the consumer exactly-once, in order "
                        + "(no silent store-only skipping)");
        assertEquals(14, point.size(),
                "store mirrors all in-flight records (checkpoint-scoped recovery plane, "
                        + "unbounded default)");
        assertEquals(0, pool.getGlobalUsage(),
                "all permits must be released after the consumer drained");
    }

    /**
     * Unconfigured (recovery-default) store: NO fail-loud bound — the
     * checkpoint-scoped recovery plane mirrors all in-flight uncommitted records
     * (adjudication 10a as revised by the combined-gate live evidence,
     * {@code TestRegionRestartInternalEdgeE2E.materializedEdge_replaySetLargerThanQueueCapacity_doesNotBlockRestart}).
     * Crossing the 1024 tier emits a one-per-tier WARN with the point id and size.
     */
    @Test
    void fam1_storeUnconfiguredDefaultGrowsForRecoveryWithTierWarn() throws Exception {
        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InMemoryMaterializationPoint.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-unbounded");
            // Well past the old default bound and past the first WARN tier: writes
            // must keep succeeding (recovery plane >= in-flight uncommitted records).
            for (int i = 0; i < 1100; i++) {
                point.write(new StreamRecord<>("e" + i), 0L);
            }
            assertEquals(1100, point.size(), "unconfigured store must not fail loud");
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel().toString().equals("WARN")
                    && e.getFormattedMessage().contains("p-unbounded")
                    && e.getFormattedMessage().contains("1024")),
                    "crossing the 1024 tier must emit a WARN with pointId and size");
            assertEquals(1, appender.list.stream()
                    .filter(e -> e.getLevel().toString().equals("WARN")).count(),
                    "the tier WARN must fire once, not per entry");
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    /**
     * An EXPLICITLY configured positive bound enables the operator-imposed
     * fail-loud signal; a configured {@code <= 0} value keeps the unbounded
     * recovery default (no fail-fast at assembly, per the revised 10a semantics).
     */
    @Test
    void fam1_storeExplicitBoundFailsLoudNonPositiveIsUnbounded() throws Exception {
        System.setProperty(STORE_MAX_ENTRIES_CONFIG, "16");
        try {
            InMemoryMaterializationPoint point = new InMemoryMaterializationPoint("p-cfg");
            point.write(new StreamRecord<>("a"), 0L);
            StreamException ex = assertThrows(StreamException.class, () -> {
                for (int i = 0; i < 16; i++) {
                    point.write(new StreamRecord<>("e" + i), 0L);
                }
            });
            assertEquals(NopStreamErrors.ERR_STREAM_MATERIALIZE_STORE_OVERFLOW.getErrorCode(),
                    ex.getErrorCode());
            assertEquals(16, point.size());

            System.setProperty(STORE_MAX_ENTRIES_CONFIG, "-1");
            InMemoryMaterializationPoint unbounded = new InMemoryMaterializationPoint("p-neg");
            for (int i = 0; i < 20; i++) {
                unbounded.write(new StreamRecord<>("e" + i), 0L);
            }
            assertEquals(20, unbounded.size(),
                    "a configured <= 0 value must keep the unbounded recovery default");
        } finally {
            System.clearProperty(STORE_MAX_ENTRIES_CONFIG);
        }
    }
}
