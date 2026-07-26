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
     * A test sink that tracks rollback/abort/commit calls and can simulate rollback failures
     * only during the pending-rollback loop (not during recover).
     */
    static class TestSink extends TwoPhaseCommitSinkFunction<String> {
        int rollbackCallCount = 0;
        int abortCallCount = 0;
        int commitCallCount = 0;
        int beginCallCount = 0;
        final List<Long> committedEpochs = new ArrayList<>();
        final List<Long> abortedEpochs = new ArrayList<>();
        int failOnRollbackCall = -1;

        TestSink withPendingCommits() {
            getPendingCommits().put(1L, "tx1");
            return this;
        }

        TestSink failOnRollbackCall(int callNumber) {
            this.failOnRollbackCall = callNumber;
            return this;
        }

        @Override public void beginTransaction() { beginCallCount++; }
        @Override public void invoke(String value) {}
        @Override public void preCommit(long checkpointId) {}
        @Override public void commit(long checkpointId) {
            commitCallCount++;
            committedEpochs.add(checkpointId);
        }
        @Override public void rollback() throws Exception {
            rollbackCallCount++;
            if (rollbackCallCount == failOnRollbackCall) {
                throw new StreamRuntimeException("rollback failed on call " + rollbackCallCount);
            }
        }

        @Override
        public void abort(long epochId) throws Exception {
            abortCallCount++;
            abortedEpochs.add(epochId);
            // Default behavior preserves parity with rollback() count semantics so
            // legacy assertions about rollback still work.
            rollback();
        }

        @Override
        public void recover(long checkpointId) throws Exception {
            beginTransaction();
        }
    }

    @Test
    void testRestoreFromEpoch_pendingRollbackFailureIsCaught() throws Exception {
        // With the P0-2 fix: pending(1L) with epochId=1 is durable (1 <= 1), so it
        // is committed (no rollback). To exercise the rollback-failure-caught path
        // we use a pending tx whose epoch is strictly greater than the durable
        // epochId, which routes through abort() -> rollback() and remains
        // best-effort (no throw).
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(5L, "tx-non-durable");
        sink.failOnRollbackCall(1);

        assertDoesNotThrow(() -> sink.restoreFromEpoch(1, null));
        assertTrue(sink.rollbackCallCount >= 1, "Abort path should have invoked rollback at least once");
    }

    @Test
    void testRestoreFromEpoch_successfulRollbackClearsPending() throws Exception {
        // After P0-2: a pending tx with epochId=1 against durable epoch=0 is
        // non-durable, so abort() -> rollback() must run and clear it from pending.
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(1L, "tx1");

        sink.restoreFromEpoch(0, null);
        assertTrue(sink.getPendingCommits().isEmpty(),
                "Pending commits should be cleared after restore");
        assertTrue(sink.abortedEpochs.contains(1L),
                "Non-durable pending tx should be aborted");
    }

    @Test
    void testRestoreFromEpoch_durablePendingIsCommittedNotAborted() throws Exception {
        // P0-2 anti-hollow proof: durable pending (epoch <= restore epoch) MUST be
        // committed, not rolled back. Removing the commit() call in restoreFromEpoch
        // makes this test fail.
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(1L, "tx-durable-1");
        sink.getPendingCommits().put(2L, "tx-durable-2");

        sink.restoreFromEpoch(5L, null);

        assertEquals(2, sink.commitCallCount, "Both durable pending tx must be committed");
        assertTrue(sink.committedEpochs.contains(1L));
        assertTrue(sink.committedEpochs.contains(2L));
        assertEquals(0, sink.abortCallCount, "Durable pending tx must NOT be aborted");
        assertEquals(0, sink.rollbackCallCount, "Durable pending tx must NOT trigger rollback");
        assertTrue(sink.getPendingCommits().isEmpty(), "Cleared after restore");
        assertEquals(1, sink.beginCallCount,
                "beginTransaction() invoked exactly once at end");
    }

    @Test
    void testRestoreFromEpoch_mixedDurableAndNonDurable() throws Exception {
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(1L, "durable");
        sink.getPendingCommits().put(2L, "durable");
        sink.getPendingCommits().put(8L, "non-durable");
        sink.getPendingCommits().put(9L, "non-durable");

        sink.restoreFromEpoch(3L, null);

        assertEquals(2, sink.commitCallCount, "epochId <= 3 must be committed");
        assertTrue(sink.committedEpochs.contains(1L));
        assertTrue(sink.committedEpochs.contains(2L));
        assertEquals(2, sink.abortCallCount, "epochId > 3 must be aborted");
        assertTrue(sink.abortedEpochs.contains(8L));
        assertTrue(sink.abortedEpochs.contains(9L));
        assertTrue(sink.getPendingCommits().isEmpty());
    }

    @Test
    void testRestoreFromEpoch_noPendingCommits() throws Exception {
        TestSink sink = new TestSink();

        assertDoesNotThrow(() -> sink.restoreFromEpoch(1, null));
        assertEquals(0, sink.commitCallCount, "No commit when pending is empty");
        assertEquals(0, sink.abortCallCount, "No abort when pending is empty");
        assertEquals(0, sink.rollbackCallCount, "No rollback when pending is empty");
        assertEquals(1, sink.beginCallCount,
                "beginTransaction still invoked exactly once at end of restore");
    }

    @Test
    void testAbortDefaultsToRollbackForBackCompat() throws Exception {
        // Anti-regression: subclasses that only override rollback() still observe
        // rollback being called via the default abort() delegation.
        TestSink sink = new TestSink();
        sink.getPendingCommits().put(9L, "non-durable");
        sink.restoreFromEpoch(1L, null);
        assertEquals(1, sink.rollbackCallCount, "Default abort() should delegate to rollback()");
        assertTrue(sink.abortedEpochs.contains(9L));
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
        // P0-2 fix: StreamSinkOperator.restoreState no longer calls restoreFromEpoch(-1, null).
        // It only rebuilds pendingCommits from durable snapshot. The pending set
        // must therefore contain the durable pending tx (1L) untouched — the real
        // restoreFromEpoch(realEpochId) is now invoked solely by
        // GraphModelCheckpointExecutor.restoreOperatorsFromState.
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

        // P0-3: restoreState no longer triggers restoreFromEpoch, so the pending
        // tx must survive and no commit/abort/rollback may have fired yet.
        assertEquals(1, restoredSink.getPendingCommits().size(),
                "Pending commits must be rebuilt from durable snapshot and survive until restoreFromEpoch is called");
        assertTrue(restoredSink.getPendingCommits().containsKey(1L));
        assertEquals(0, restoredSink.rollbackCallCount, "No rollback during restoreState");
        assertEquals(0, restoredSink.commitCallCount, "No commit during restoreState");
        assertEquals(0, restoredSink.abortCallCount, "No abort during restoreState");
    }

    @Test
    void testTwoPhaseCommitSaveRestoreRoundTrip() throws Exception {
        // After P0-2/P0-3: restoreState no longer touches pending commits semantically;
        // the real restoreFromEpoch(realEpochId) is owned by the executor. The test
        // verifies that restoreState restores the pending map intact and defers the
        // commit/abort decision to restoreFromEpoch.
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

        // After P0-3 fix: pendingCommits are rebuilt but not consumed in restoreState.
        assertEquals(1, restoredSink.getPendingCommits().size(),
                "Pending commits restored from durable snapshot, awaiting restoreFromEpoch");
        // Now simulate the executor-driven restoreFromEpoch with the real epochId:
        restoredSink.restoreFromEpoch(1L, null);
        // durable pending (1 <= 1) must be committed, not rolled back
        assertEquals(1, restoredSink.commitCallCount, "Durable pending tx committed");
        assertTrue(restoredSink.committedEpochs.contains(1L));
        assertTrue(restoredSink.getPendingCommits().isEmpty(),
                "Pending cleared after restoreFromEpoch(realEpochId)");
    }

    @Test
    void testFinishCommitFailurePathKeepsPendingForSubsuming() throws Exception {
        TestSink sink = new TestSink().withPendingCommits();

        sink.finishCommit(1L, false);

        assertFalse(sink.getPendingCommits().isEmpty(),
                "Pending commits should be kept after failure path for subsuming commit");
    }
}
