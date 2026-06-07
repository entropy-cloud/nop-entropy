package io.nop.stream.runtime.checkpoint;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointParticipantRestore {

    private static final TaskLocation LOC = new TaskLocation("job1", "pipe1", "v1", 0);

    @Test
    void testRestoreFromEpochIsCallable() throws Exception {
        long expectedEpoch = 5L;
        TaskStateSnapshot state = TaskStateSnapshot.empty(LOC);

        long[] capturedEpoch = {0};
        TaskStateSnapshot[] capturedState = {null};

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.builder(LOC).build();
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot s) {
                capturedEpoch[0] = epochId;
                capturedState[0] = s;
            }
        };

        participant.restoreFromEpoch(expectedEpoch, state);
        assertEquals(expectedEpoch, capturedEpoch[0]);
        assertSame(state, capturedState[0]);
    }

    @Test
    void testRestoreFromEpochWithNullState() throws Exception {
        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.builder(LOC).build();
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
                assertNull(state);
            }
        };

        participant.restoreFromEpoch(0, null);
    }

    @Test
    void testRestoreFromEpochPropagatesException() {
        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return null;
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
                throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "restore failed");
            }
        };

        assertThrows(StreamException.class, () ->
                participant.restoreFromEpoch(1, TaskStateSnapshot.empty(LOC)));
    }
}
