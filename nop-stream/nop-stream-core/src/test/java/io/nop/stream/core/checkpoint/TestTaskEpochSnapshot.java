package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.state.shard.StateShard;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskEpochSnapshot {

    @Test
    void testExtendsTaskStateSnapshot() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(loc, 1);
        assertTrue(snapshot instanceof TaskStateSnapshot);
    }

    @Test
    void testShardManagement() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(loc, 1);
        snapshot.addShard(StateShard.singleShard(0));
        assertEquals(1, snapshot.getShards().size());
    }

    @Test
    void testTimerStateManagement() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(loc, 1);
        snapshot.putTimerState("event-timers", Map.of("key1", "value1"));
        assertEquals("value1", ((Map<?, ?>) snapshot.getTimerState("event-timers")).get("key1"));
    }

    @Test
    void testFromTaskStateSnapshot() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskStateSnapshot original = new TaskStateSnapshot(loc, 1);
        original.putOperatorState("op-state", "value");

        TaskEpochSnapshot epoch = TaskEpochSnapshot.fromTaskStateSnapshot(original);
        assertEquals("value", epoch.getOperatorState("op-state"));
        assertEquals(0, epoch.getShards().size());
        assertTrue(epoch.getTimerStates().isEmpty());
    }

    @Test
    void testFromTaskEpochSnapshotReturnsSame() {
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskEpochSnapshot original = new TaskEpochSnapshot(loc, 1);
        original.addShard(StateShard.singleShard(0));

        TaskEpochSnapshot converted = TaskEpochSnapshot.fromTaskStateSnapshot(original);
        assertSame(original, converted);
    }
}
