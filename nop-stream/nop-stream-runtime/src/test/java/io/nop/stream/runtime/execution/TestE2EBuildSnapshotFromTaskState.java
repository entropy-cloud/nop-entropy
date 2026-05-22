package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EBuildSnapshotFromTaskState {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    @Test
    void testOnlyMappedOperatorStateReturned() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "state-A".getBytes());
        taskState.putOperatorState("operator-1", "state-B".getBytes());
        taskState.putOperatorState("operator-2", "state-C".getBytes());

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(1, "operator-1", null, false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 1, mappings);

        assertFalse(result.isEmpty());
        assertEquals(1, result.getOperatorStates().size());
        assertArrayEquals("state-B".getBytes(), result.getOperatorStates().get("operator-1"));

        assertNull(result.getOperatorStates().get("operator-0"));
        assertNull(result.getOperatorStates().get("operator-2"));
    }

    @Test
    void testNoMatchFallsBackToDefaultKey() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "fallback-state".getBytes());

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(5, "operator-5", null, false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, mappings);

        assertFalse(result.isEmpty());
        assertArrayEquals("fallback-state".getBytes(), result.getOperatorStates().get("operator-0"));
    }

    @Test
    void testKeyedStateRouted() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-1", "op-state".getBytes());
        taskState.putKeyedState("operator-1-keyed-key1", "keyed-1".getBytes());
        taskState.putKeyedState("operator-1-keyed-key2", "keyed-2".getBytes());
        taskState.putKeyedState("operator-0-keyed-key1", "wrong-keyed".getBytes());

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(1, "operator-1", "operator-1-keyed", false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 1, mappings);

        assertFalse(result.isEmpty());
        assertArrayEquals("op-state".getBytes(), result.getOperatorStates().get("operator-1"));
        assertEquals(2, result.getKeyedStates().size());
        assertArrayEquals("keyed-1".getBytes(), result.getKeyedStates().get("operator-1-keyed-key1"));
        assertArrayEquals("keyed-2".getBytes(), result.getKeyedStates().get("operator-1-keyed-key2"));
        assertNull(result.getKeyedStates().get("operator-0-keyed-key1"));
    }

    @Test
    void testEmptyMappingsFallsBackToDefault() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "state-A".getBytes());
        taskState.putOperatorState("operator-1", "state-B".getBytes());

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, Collections.emptyList());

        assertFalse(result.isEmpty());
        assertArrayEquals("state-A".getBytes(), result.getOperatorStates().get("operator-0"));
    }

    @Test
    void testNullMappingsFallsBackToDefault() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "state-A".getBytes());

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, null);

        assertFalse(result.isEmpty());
        assertArrayEquals("state-A".getBytes(), result.getOperatorStates().get("operator-0"));
    }
}
