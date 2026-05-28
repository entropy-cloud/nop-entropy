package io.nop.stream.core.common.functions.sink;

import org.junit.jupiter.api.Test;
import io.nop.stream.core.exceptions.StreamRuntimeException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestTwoPhaseCommitSinkFunction {

    /**
     * A test sink that tracks rollback calls and can simulate rollback failures
     * only during the pending-rollback loop (not during recover).
     */
    static class TestSink extends TwoPhaseCommitSinkFunction<String> {
        int rollbackCallCount = 0;
        int failOnRollbackCall = -1;
        private Map<Long, Object> pendingCommits = new HashMap<>();

        TestSink withPendingCommits() {
            pendingCommits.put(1L, "tx1");
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
        @Override public Map<Long, Object> getPendingCommits() {
            return pendingCommits;
        }
        @Override public void setPendingCommits(Map<Long, Object> pending) {
            this.pendingCommits = pending;
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
}
