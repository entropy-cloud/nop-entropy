package io.nop.stream.core.checkpoint.participant;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

        TaskStateSnapshot state = sink.saveState(1);
        assertNotNull(state, "saveState should return non-null TaskStateSnapshot");
        assertTrue(state.getOperatorStates().containsKey(TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY));
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
    void testFinishCommitFailureKeepsPreparedTransaction() throws Exception {
        TestTwoPhaseSink sink = new TestTwoPhaseSink();
        sink.finishCommit(1, false);
        assertFalse(sink.committed);
        // finishCommit(false) does NOT call rollback — prepared tx is kept for subsuming commit
        assertFalse(sink.rolledBack);
    }

    @Test
    void testRestoreFromEpochCallsRecover() throws Exception {
        TestTwoPhaseSink sink = new TestTwoPhaseSink();
        sink.restoreFromEpoch(1, null);
        assertTrue(sink.recovered);
    }

    static class TestTwoPhaseSink extends TwoPhaseCommitSinkFunction<String> {
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
