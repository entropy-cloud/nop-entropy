/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessOperator;
import io.nop.stream.core.operators.TimerStateKeys;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 ST-11 regression pin: keyed ProcessFunction timers are REQUIRED state.
 * Before the fix, ProcessOperator never snapshotted its internal timer service
 * (all snapshotTimers/restoreTimers consumers were window/CEP), so every
 * event-time/processing-time timer silently vanished across a checkpoint.
 *
 * <p>Drives the REAL JSON persist path ({@code CheckpointSerDe} +
 * {@code JsonTool}) exactly like the storageType=local round trip, then a
 * brand-new operator restores and the timer registry must match — including
 * the key's runtime class ({@code Long(123)} must not come back as the JSON
 * {@code Integer(123)}; the snapshot keyType header feeds the backend and the
 * operator's {@code setKeyType} wiring).
 */
class TestProcessOperatorTimerCheckpointRestore {

    private static final TaskLocation LOC_0 = new TaskLocation("job-timer", "pipeline-0", "process-vertex", 0);

    /** Registers one event-time and one processing-time timer. */
    static class TimerRegistrarFunction extends ProcessFunction<Object, String> {
        @Override
        public void processElement(Object value, Context ctx, io.nop.stream.core.util.Collector<String> out) throws Exception {
            ctx.timerService().registerEventTimeTimer(100L);
            ctx.timerService().registerProcessingTimeTimer(5000L);
            out.collect("ok");
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, io.nop.stream.core.util.Collector<String> out) {
            out.collect("timer:" + timestamp);
        }
    }

    static class TestableProcessOperator extends ProcessOperator<Object, String> {
        TestableProcessOperator() {
            super(new TimerRegistrarFunction());
        }

        io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<?> backend() {
            return (io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<?>) keyedStateBackend;
        }
    }

    @Test
    void testProcessOperatorTimersSurviveJsonCheckpointRestore() throws Exception {
        TestOutput<String> output1 = new TestOutput<>();
        TestableProcessOperator op1 = new TestableProcessOperator();
        op1.setOutput((Output) output1);
        op1.setStateBackend(new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend());
        op1.open();

        // Long key 123 (< 2^31 → JSON round-trips as Integer without setKeyType wiring)
        op1.setCurrentKey(123L);
        // touch keyed state so the snapshot carries a keyType header (ST-02 capture)
        op1.backend().getState(new ValueStateDescriptor<>("touch", Long.class)).update(1L);
        op1.processElement(new StreamRecord<>("reg", 10));

        OperatorSnapshotResult snapshot = op1.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));
        assertNotNull(snapshot.getOperatorState(TimerStateKeys.PROCESS_INTERNAL_TIMERS),
                "ProcessOperator must snapshot its internal timer service (R5 ST-11)");

        // Real JSON persist path
        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0).checkpointId(1L).build();
        for (Map.Entry<String, Object> e : snapshot.getOperatorStates().entrySet()) {
            taskState.putOperatorState(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : snapshot.getKeyedStates().entrySet()) {
            taskState.putKeyedState(e.getKey(), e.getValue());
        }
        Map<String, Object> form = CheckpointSerDe.serializeTaskStateSnapshot(taskState);
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));
        TaskStateSnapshot restoredTaskState = CheckpointSerDe.deserializeTaskStateSnapshot(parsed, LOC_0);

        // Kill + restore: brand-new operator, restoreState before open()
        OperatorSnapshotResult restored = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> e : restoredTaskState.getOperatorStates().entrySet()) {
            restored.putOperatorState(e.getKey(), e.getValue());
        }
        // keyed states ride along so the keyType header reaches the backend
        for (Map.Entry<String, Object> e : restoredTaskState.getKeyedStates().entrySet()) {
            restored.putKeyedState(e.getKey(), e.getValue());
        }

        TestOutput<String> output2 = new TestOutput<>();
        TestableProcessOperator op2 = new TestableProcessOperator();
        op2.setOutput((Output) output2);
        op2.setStateBackend(new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend());
        op2.restoreState(restored);
        op2.open();

        // The restored timer registry must match the pre-checkpoint registry,
        // including the key's runtime class.
        OperatorSnapshotResult after = op2.snapshotState(
                new StateSnapshotContext(2L, System.currentTimeMillis()));
        HeapInternalTimerService.TimerSnapshot<Object, ?> timers =
                (HeapInternalTimerService.TimerSnapshot<Object, ?>)
                        after.getOperatorState(TimerStateKeys.PROCESS_INTERNAL_TIMERS);
        assertNotNull(timers, "restored operator must still snapshot timers after open()");

        List<? extends HeapInternalTimerService.TimerEntry<Object, ?>> eventTimers = timers.getEventTimeTimers();
        assertEquals(1, eventTimers.size(), "event-time timer must survive the checkpoint");
        assertEquals(100L, eventTimers.get(0).getTimestamp());
        Object restoredKey = eventTimers.get(0).getKey();
        assertTrue(restoredKey instanceof Long,
                "timer key must be re-materialized to Long (got " + restoredKey.getClass().getName() + ")");
        assertEquals(123L, restoredKey);

        List<? extends HeapInternalTimerService.TimerEntry<Object, ?>> processingTimers = timers.getProcessingTimeTimers();
        assertEquals(1, processingTimers.size(), "processing-time timer must survive the checkpoint");
        assertEquals(5000L, processingTimers.get(0).getTimestamp());
    }
}
