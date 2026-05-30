package io.nop.stream.core.common.functions.sink;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.operators.StreamSinkOperator;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TestTwoPhaseCommitSinkFunction {

    /**
     * A test sink that tracks rollback calls and can simulate rollback failures
     * only during the pending-rollback loop (not during recover).
     */
    static class TestSink extends TwoPhaseCommitSinkFunction<String> {
        int rollbackCallCount = 0;
        int failOnRollbackCall = -1;

        TestSink withPendingCommits() {
            getPendingCommits().put(1L, "tx1");
            return this;
        }

        TestSink failOnRollbackCall(int callNumber) {
            this.failOnRollbackCall = callNumber;
            return this;
        }

        @Override public void beginTransaction() {}
        @Override public void invoke(String value) {}
        @Override public void preCommit(long checkpointId) {}
        @Override public void commit(long checkpointId) {}
        @Override public void rollback() throws Exception {
            rollbackCallCount++;
            if (rollbackCallCount == failOnRollbackCall) {
                throw new StreamRuntimeException("rollback failed on call " + rollbackCallCount);
            }
        }

        @Override
        public void recover(long checkpointId) throws Exception {
            beginTransaction();
        }
    }

    @Test
    void testRestoreFromEpoch_pendingRollbackFailureIsCaught() throws Exception {
        // The first rollback call (during pending rollback loop) should fail,
        // but restoreFromEpoch catches it and logs. The test verifies the method
        // does not throw even when pending rollback fails.
        TestSink sink = new TestSink().withPendingCommits().failOnRollbackCall(1);

        assertDoesNotThrow(() -> sink.restoreFromEpoch(1, null));
        assertTrue(sink.rollbackCallCount >= 1, "Rollback should have been attempted at least once");
    }

    @Test
    void testRestoreFromEpoch_successfulRollbackClearsPending() throws Exception {
        TestSink sink = new TestSink().withPendingCommits();

        sink.restoreFromEpoch(1, null);
        assertTrue(sink.getPendingCommits().isEmpty(),
                "Pending commits should be cleared after restore");
    }

    @Test
    void testRestoreFromEpoch_noPendingCommits() throws Exception {
        TestSink sink = new TestSink();

        assertDoesNotThrow(() -> sink.restoreFromEpoch(1, null));
        assertEquals(0, sink.rollbackCallCount, "No rollback should be called when no pending");
    }

    @Test
    void testConcurrentFinishCommitNoConcurrentModificationException() throws Exception {
        TestSink sink = new TestSink();
        Map<Long, Object> pending = Collections.synchronizedMap(new TreeMap<>());
        for (long i = 1; i <= 50; i++) {
            pending.put(i, "tx" + i);
        }
        sink.setPendingCommits(pending);

        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final long epochId = 5L * (i + 1);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    sink.finishCommit(epochId, true);
                } catch (Throwable t) {
                    failed.set(true);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertFalse(failed.get(), "Concurrent finishCommit should not throw ConcurrentModificationException");
    }

    @Test
    void testSaveStatePersistsPendingCommits() throws Exception {
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(1L, "tx1");
        sink.getPendingCommits().put(2L, "tx2");

        TaskStateSnapshot snapshot = sink.saveState(1L);
        assertNotNull(snapshot, "saveState should return non-null");

        Object raw = snapshot.getOperatorState(TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY);
        assertNotNull(raw, "snapshot should contain pending-commits");
        assertTrue(raw instanceof Map, "pending-commits should be a Map");

        @SuppressWarnings("unchecked")
        Map<Long, Object> restored = (Map<Long, Object>) raw;
        assertEquals(2, restored.size());
        assertEquals("tx1", restored.get(1L));
        assertEquals("tx2", restored.get(2L));
    }

    @Test
    void testSaveStateReturnsNonEmptySnapshotWhenNoPendingCommits() throws Exception {
        TestSink sink = new TestSink();

        TaskStateSnapshot snapshot = sink.saveState(1L);
        assertNotNull(snapshot, "saveState should return non-null even when no pending commits");

        Object raw = snapshot.getOperatorState(TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY);
        assertNotNull(raw, "snapshot should contain pending-commits key even when empty");
        assertTrue(raw instanceof Map);
        assertTrue(((Map<?, ?>) raw).isEmpty());
    }

    @Test
    void testRestoreStateRecoversPendingCommitsAndRollbacks() throws Exception {
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(1L, "tx1");

        TaskStateSnapshot saved = sink.saveState(1L);
        OperatorSnapshotResult snapshotResult = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> entry : saved.getOperatorStates().entrySet()) {
            snapshotResult.putOperatorState("participant-" + entry.getKey(), entry.getValue());
        }

        TestSink restoredSink = new TestSink();
        assertTrue(restoredSink.getPendingCommits().isEmpty());

        StreamSinkOperator<String> operator = new StreamSinkOperator<>(restoredSink);
        operator.restoreState(snapshotResult);

        assertTrue(restoredSink.rollbackCallCount >= 1,
                "rollback should have been called for pending tx");
        assertTrue(restoredSink.getPendingCommits().isEmpty(),
                "pending commits should be cleared after restoreFromEpoch");
    }

    @Test
    void testTwoPhaseCommitSaveRestoreRoundTrip() throws Exception {
        TestSink sink = new TestSink();
        sink.beginTransaction();
        sink.preCommit(1L);
        sink.getPendingCommits().put(1L, "tx_epoch_1");

        TaskStateSnapshot saved = sink.saveState(1L);
        assertNotNull(saved);

        OperatorSnapshotResult snapshotResult = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> entry : saved.getOperatorStates().entrySet()) {
            snapshotResult.putOperatorState("participant-" + entry.getKey(), entry.getValue());
        }

        TestSink restoredSink = new TestSink();
        StreamSinkOperator<String> operator = new StreamSinkOperator<>(restoredSink);
        operator.restoreState(snapshotResult);

        assertTrue(restoredSink.getPendingCommits().isEmpty(),
                "Restored pending commits should be rolled back and cleared");
    }

    @Test
    void testFinishCommitFailurePathKeepsPendingForSubsuming() throws Exception {
        TestSink sink = new TestSink().withPendingCommits();

        sink.finishCommit(1L, false);

        assertFalse(sink.getPendingCommits().isEmpty(),
                "Pending commits should be kept after failure path for subsuming commit");
    }
}
