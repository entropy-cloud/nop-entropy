/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N1/N5 regression (plan 366 Phase 2): recovery replay attaches to the
 * partition as pending replay and is delivered ahead of queue content without
 * ever blocking on the bounded queue.
 *
 * <p>Pre-fix, region-restart replay pushed the (unbounded) materialization
 * replay set through {@code injectFront}'s blocking {@code queue.put} into the
 * capacity-1024 queue BEFORE any consumer was running — a replay set larger
 * than the queue capacity blocked the supervision thread forever (silent job
 * hang). The EOS sentinel also leaked one buffer-pool permit per injection.
 */
class TestResultPartitionPendingReplay {

    private static List<StreamElement> records(String prefix, int count) {
        List<StreamElement> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(new StreamRecord<>(prefix + i));
        }
        return list;
    }

    private static String value(StreamElement element) {
        assertTrue(element instanceof StreamRecord, "expected a data record");
        return ((StreamRecord<?>) element).getValue().toString();
    }

    /**
     * Core N1 invariant: a replay set far larger than the bounded queue capacity
     * is attached O(1) and drained lazily by the consumer, in order, ahead of
     * queue content written after the attachment.
     */
    @Test
    void replayLargerThanQueueCapacityDrainsWithoutBlocking() throws Exception {
        ResultPartition partition = new ResultPartition(128);
        partition.write(new StreamRecord<>("q0"));

        partition.attachPendingReplay(records("p", 2000));

        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            StreamElement element = partition.read(50, TimeUnit.MILLISECONDS);
            assertNotNull(element, "partition must keep delivering past the queue capacity");
            seen.add(value(element));
        }
        for (int i = 1; i <= 2000; i++) {
            assertEquals("p" + i, seen.get(i - 1), "replay must come first, in write order");
        }
        assertEquals("q0", seen.get(2000), "queue content follows the replay");
    }

    /**
     * Finished partition + pending replay: the consumer sees replay data first
     * and end-of-stream only after the deque drains. The gate loop consumes via
     * the TIMEOUT overload — a premature null there would be misread as EOS
     * (silent replay loss masquerading as task completion).
     */
    @Test
    void finishedPartitionDeliversReplayThenEosOnBothReadOverloads() throws Exception {
        ResultPartition partition = new ResultPartition(8);
        partition.write(new StreamRecord<>("q0"));
        partition.close();
        partition.attachPendingReplay(records("p", 3));

        // Timeout overload (the gate path) must NOT return null while replay data remains.
        for (int i = 1; i <= 3; i++) {
            StreamElement element = partition.read(10, TimeUnit.MILLISECONDS);
            assertNotNull(element, "pending replay must be delivered before EOS on the timeout overload");
            assertEquals("p" + i, value(element));
        }
        // Blocking overload: queued content follows the replay, then the sentinel.
        assertEquals("q0", value(partition.read()));
        assertNull(partition.read(), "finished + drained partition signals EOS");
        assertNull(partition.read(10, TimeUnit.MILLISECONDS), "EOS is sticky");
    }

    /**
     * Unaligned-checkpoint capture sees pending replay too (capture → restore
     * round-trip must not drop the not-yet-delivered replay segment), and the
     * queue sentinel stays in place so the restored partition still signals EOS.
     */
    @Test
    void captureIncludesPendingReplayAndKeepsSentinel() throws Exception {
        ResultPartition partition = new ResultPartition(8);
        partition.write(new StreamRecord<>("q0"));
        partition.write(new StreamRecord<>("q1"));
        partition.close();
        partition.attachPendingReplay(records("p", 2));

        List<StreamElement> captured = partition.drainBufferedElements();
        assertEquals(4, captured.size(), "capture moves pending replay + queued records");
        assertEquals("p1", value(captured.get(0)));
        assertEquals("p2", value(captured.get(1)));
        assertEquals("q0", value(captured.get(2)));
        assertEquals("q1", value(captured.get(3)));

        // Sentinel was not handed out; the finished partition still signals EOS.
        assertTrue(partition.isFinished());
        assertNull(partition.read(10, TimeUnit.MILLISECONDS));
    }

    /**
     * N5 invariant: replay elements hold no buffer-pool permit — attaching and
     * consuming them neither leaks nor doubles releases. Permits flow only for
     * queue elements (acquire on write, release on read).
     */
    @Test
    void replayPathHoldsNoPoolPermits() throws Exception {
        BufferPool pool = new BufferPool(4);
        ResultPartition partition = new ResultPartition(8, pool);

        partition.write(new StreamRecord<>("q0"));
        partition.write(new StreamRecord<>("q1"));
        assertEquals(2, pool.getGlobalAvailableCapacity(), "two queue writes hold two permits");

        partition.attachPendingReplay(records("p", 10));
        assertEquals(2, pool.getGlobalAvailableCapacity(), "attach acquires nothing");

        for (int i = 1; i <= 10; i++) {
            assertEquals("p" + i, value(partition.read()));
        }
        assertEquals(2, pool.getGlobalAvailableCapacity(), "replay delivery releases nothing");

        assertEquals("q0", value(partition.read()));
        assertEquals("q1", value(partition.read()));
        assertEquals(4, pool.getGlobalAvailableCapacity(), "queue reads release their permits; total conserved");
    }

    /**
     * Wiring: the unaligned-restore path ({@code InputChannel.injectElements})
     * routes through pending replay — elements injected on the recovery path are
     * delivered ahead of live queue content.
     */
    @Test
    void channelInjectElementsDeliversAheadOfQueueContent() throws Exception {
        ResultPartition partition = new ResultPartition(8);
        partition.write(new StreamRecord<>("live"));
        InputChannel channel = new InputChannel(partition);

        channel.injectElements(records("p", 2));

        assertEquals("p1", value(channel.read(10, TimeUnit.MILLISECONDS)));
        assertEquals("p2", value(channel.read(10, TimeUnit.MILLISECONDS)));
        assertEquals("live", value(channel.read(10, TimeUnit.MILLISECONDS)));
    }
}
