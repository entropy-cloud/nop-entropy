/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.runtime.execution.RescaleStateAssembler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R5 ST-03 regression pin: the rescaled keyed-state envelope must preserve the
 * source snapshot's {@code keyType} header. Before the fix the merged envelope
 * only carried {@code states} — a second rescale/reshard pass over the product
 * lost key re-materialization and the range filter silently dropped
 * misrouted (JSON-native-drifted) entries.
 */
class TestRescaleEnvelopeKeyTypePreserved {

    private static final String JOB = "job-kt";
    private static final String PIPELINE = "pipeline-0";
    private static final String VERTEX = "keyed-vertex";

    private static TaskLocation loc(int taskIndex) {
        return new TaskLocation(JOB, PIPELINE, VERTEX, taskIndex);
    }

    private static CheckpointPlan planOf(TaskLocation... locations) {
        return new CheckpointPlan(JOB, PIPELINE,
                java.util.Arrays.asList(locations), java.util.Arrays.asList(locations), Collections.emptyMap());
    }

    private static GraphModelCheckpointExecutor.TaskStateLookup lookupOf(Map<TaskLocation, TaskStateSnapshot> states) {
        return states::get;
    }

    private static TaskStateSnapshot keyedSnapshotWithHeader(long checkpointId, int taskIndex) {
        TaskStateSnapshot snap = new TaskStateSnapshot(loc(taskIndex), checkpointId);
        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put("keyType", "java.lang.Long");
        Map<String, Object> stateInfo = new LinkedHashMap<>();
        stateInfo.put("stateType", "ValueState");
        stateInfo.put("valueType", "java.lang.Long");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("namespace", "_default_");
        entry.put("key", 42L);
        entry.put("value", 7L);
        stateInfo.put("entries", Collections.singletonList(entry));
        stateData.put("states", Map.of("touch", stateInfo));
        snap.putKeyedState("keyed-state", new StateSnapshot(stateData));
        return snap;
    }

    @Test
    void rescaledEnvelopePreservesSnapshotKeyTypeHeader() throws Exception {
        Map<TaskLocation, TaskStateSnapshot> states = new LinkedHashMap<>();
        states.put(loc(0), keyedSnapshotWithHeader(7L, 0));

        KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(1, 1, 0);
        TaskStateSnapshot merged = RescaleStateAssembler.buildRescaledTaskState(VERTEX, 0, newRange,
                Collections.singletonList(loc(0)), 1, 1, 1, lookupOf(states), planOf(loc(0)));

        Object keyedState = merged.getKeyedStates().get("keyed-state");
        assertNotNull(keyedState, "rescaled snapshot must carry the keyed state");
        StateSnapshot snap = (StateSnapshot) keyedState;
        assertEquals("java.lang.Long", snap.getStateData().get("keyType"),
                "rescaled envelope must preserve the source keyType header (R5 ST-03)");
        assertNotNull(snap.getStateData().get("states"));
    }
}
