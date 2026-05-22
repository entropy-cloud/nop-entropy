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
        taskState.putOperatorState("operator-0", "state-A");
        taskState.putOperatorState("operator-1", "state-B");
        taskState.putOperatorState("operator-2", "state-C");

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(1, "operator-1", null, false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 1, mappings);

        assertFalse(result.isEmpty());
        assertEquals(1, result.getOperatorStates().size());
        assertEquals("state-B", result.getOperatorState("operator-1"));

        assertNull(result.getOperatorState("operator-0"));
        assertNull(result.getOperatorState("operator-2"));
    }

    @Test
    void testNoMatchFallsBackToDefaultKey() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "fallback-state");

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(5, "operator-5", null, false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, mappings);

        assertFalse(result.isEmpty());
        assertEquals("fallback-state", result.getOperatorState("operator-0"));
    }

    @Test
    void testKeyedStateRouted() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-1", "op-state");
        taskState.putKeyedState("operator-1-keyed-key1", "keyed-1");
        taskState.putKeyedState("operator-1-keyed-key2", "keyed-2");
        taskState.putKeyedState("operator-0-keyed-key1", "wrong-keyed");

        List<OperatorStateMapping> mappings = Collections.singletonList(
                new OperatorStateMapping(1, "operator-1", "operator-1-keyed", false)
        );

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 1, mappings);

        assertFalse(result.isEmpty());
        assertEquals("op-state", result.getOperatorState("operator-1"));
        assertEquals(2, result.getKeyedStates().size());
        assertEquals("keyed-1", result.getKeyedState("operator-1-keyed-key1"));
        assertEquals("keyed-2", result.getKeyedState("operator-1-keyed-key2"));
        assertNull(result.getKeyedState("operator-0-keyed-key1"));
    }

    @Test
    void testEmptyMappingsFallsBackToDefault() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "state-A");
        taskState.putOperatorState("operator-1", "state-B");

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, Collections.emptyList());

        assertFalse(result.isEmpty());
        assertEquals("state-A", result.getOperatorState("operator-0"));
    }

    @Test
    void testNullMappingsFallsBackToDefault() {
        TaskStateSnapshot taskState = new TaskStateSnapshot(LOC, 1L);
        taskState.putOperatorState("operator-0", "state-A");

        OperatorSnapshotResult result = GraphModelCheckpointExecutor.buildSnapshotFromTaskState(taskState, 0, null);

        assertFalse(result.isEmpty());
        assertEquals("state-A", result.getOperatorState("operator-0"));
    }
}
