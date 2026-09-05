package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 0830-3 Phase 2 (item 24 / W-4): KeyGroupRange ownership-record
 * consistency guard on {@link TaskEpochSnapshot#getKeyGroupRange()}.
 */
class TestTaskEpochSnapshotKeyGroupRangeGuard {

    private static TaskEpochSnapshot snapshot(int start, int end) {
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(new TaskLocation("job", "pipe", "vertex", 0), 1L);
        snapshot.setKeyGroupRangeStart(start);
        snapshot.setKeyGroupRangeEnd(end);
        return snapshot;
    }

    @Test
    void inconsistentRecordFailsFastAsTypedErrorWithBothBounds() {
        StreamException e = assertThrows(StreamException.class, () -> snapshot(5, 2).getKeyGroupRange());
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("keyGroupRangeStart=5"), "error must carry start bound: " + e.getMessage());
        assertTrue(e.getMessage().contains("keyGroupRangeEnd=2"), "error must carry end bound: " + e.getMessage());
    }

    @Test
    void consistentRecordsAndUnmaterializedRecordsAreUnaffected() {
        assertEquals(new KeyGroupRange(1, 3), snapshot(1, 3).getKeyGroupRange());
        assertEquals(new KeyGroupRange(0, 0), snapshot(0, 0).getKeyGroupRange());
        assertNull(snapshot(-1, 2).getKeyGroupRange(), "start=-1 means ownership not recorded");

        // the production stamping path keeps working
        TaskEpochSnapshot produced = new TaskEpochSnapshot(new TaskLocation("job", "pipe", "vertex", 0), 1L);
        produced.setKeyGroupOwnership(2, 8, new KeyGroupRange(2, 5));
        assertEquals(new KeyGroupRange(2, 5), produced.getKeyGroupRange());
    }
}
