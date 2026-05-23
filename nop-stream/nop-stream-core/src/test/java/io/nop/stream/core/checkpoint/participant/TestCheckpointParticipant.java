package io.nop.stream.core.checkpoint.participant;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointParticipant {

    @Test
    void testTwoPhaseCommitSinkIsCheckpointParticipant() {
        assertTrue(CheckpointParticipant.class.isAssignableFrom(TwoPhaseCommitSinkFunction.class));
    }

    @Test
    void testTwoPhaseCommitDefaultMapping() throws Exception {
        TwoPhaseCommitSinkFunction<String> sink = new TestTwoPhaseSink();
        TaskLocation loc = new TaskLocation("j1", "p1", "v1", 0);

        assertNull(sink.saveState(1));
        assertDoesNotThrow(() -> sink.prepareCommit(1));
    }

    @Test
    void testFinishCommitSuccessCallsCommit() throws Exception {
        TestTwoPhaseSink sink = new TestTwoPhaseSink();
        sink.finishCommit(1, true);
        assertTrue(sink.committed);
        assertFalse(sink.rolledBack);
    }

    @Test
    void testFinishCommitFailureCallsRollback() throws Exception {
        TestTwoPhaseSink sink = new TestTwoPhaseSink();
        sink.finishCommit(1, false);
        assertFalse(sink.committed);
        assertTrue(sink.rolledBack);
    }

    @Test
    void testRestoreFromEpochCallsRecover() throws Exception {
        TestTwoPhaseSink sink = new TestTwoPhaseSink();
        sink.restoreFromEpoch(1, null);
        assertTrue(sink.recovered);
    }

    static class TestTwoPhaseSink implements TwoPhaseCommitSinkFunction<String> {
        boolean committed = false;
        boolean rolledBack = false;
        boolean recovered = false;

        @Override public void beginTransaction() {}
        @Override public void invoke(String value) {}
        @Override public void preCommit(long checkpointId) {}
        @Override public void commit(long checkpointId) { committed = true; }
        @Override public void rollback() { rolledBack = true; }
        @Override public void recover(long checkpointId) { recovered = true; }
    }
}
