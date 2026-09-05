/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.streamrecord.StreamElement;

import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-5 wiring proof (plan 1326-2 Phase 2): the {@code abortedBarriers} set that
 * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler} produces must
 * actually take effect on a SINGLE-input task. The test registers the REAL
 * production abort handler (not a hand-copied body) and drives the REAL
 * coordinator abort flow ({@code CheckpointCoordinator.abortPendingCheckpoint}),
 * then proves the aborted epoch's straggler barrier is discarded by the
 * single-channel gate while the next live epoch passes through.
 */
class TestSingleInputAbortWiringE2E {

    private static final TaskLocation LOC = new TaskLocation("abort-wiring-job", "v0", "v0", 0);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(5_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(2)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("abort-wiring-job", "v0", idCounter, storage, config);
        coordinator.registerTask(LOC);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void testLocalAbortHandlerFiltersStragglerBarrierOnSingleInputTask() throws Exception {
        // --- build a real MIDDLE-role task: chain + single-channel input gate + writer ---
        List<AbstractStreamOperator<?>> operators = mockOperators(2);
        OperatorChain chain = new OperatorChain(new ArrayList<>(operators));

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);

        ResultPartition sink = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(sink);

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, writer, gate);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC, new ArrayList<>(operators), snapshot -> { /* completion sink */ });
        invokable.setBarrierTracker(tracker);

        Subtask subtask = new Subtask("v0", 0, LOC, invokable);
        SubtaskTask task = new SubtaskTask(subtask, new JobVertex("v0", "v0", 1,
                java.util.Collections.singletonList(chain), invokable));

        // --- register the REAL production abort handler ---
        java.util.Map<String, SubtaskTask> tasks = new ConcurrentHashMap<>();
        tasks.put("v0-0", task);
        GraphModelCheckpointExecutor.registerLocalAbortHandler(coordinator, tasks);

        // --- trigger a real epoch at the coordinator AND at the task-side tracker ---
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "epoch must be triggerable");
        long cpId = pending.getCheckpointId();
        assertTrue(tracker.triggerCheckpoint(cpId, 0L, CheckpointType.CHECKPOINT),
                "task-side tracker accepts the epoch");

        // --- REAL abort flow: coordinator.abortPendingCheckpoint → production handler
        //     → tracker.notifyCheckpointAborted + inputGate.abortBarrierAlignment(cpId) ---
        coordinator.abortPendingCheckpoint(pending, "single-input abort wiring test");

        assertEquals(PendingCheckpoint.Status.ABORTED, pending.getStatus().get());
        assertFalse(tracker.hasInFlightCheckpoints(),
                "production handler must release the tracker's wait for this epoch");
        assertTrue(gate.getInFlightBarrierIds().isEmpty(),
                "production handler must release the gate's alignment for this epoch");

        // --- the aborted epoch's straggler barrier meanders through the data plane
        //     AFTER the abort; the next live epoch follows immediately ---
        upstream.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT)); // dead-epoch straggler
        upstream.write(new CheckpointBarrier(cpId + 1, 0, CheckpointType.CHECKPOINT)); // next live epoch
        upstream.close();

        List<Long> emitted = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) {
                if (gate.isAllFinished()) {
                    break;
                }
                continue;
            }
            if (e.get().isCheckpointBarrier()) {
                emitted.add(e.get().asCheckpointBarrier().getId());
            }
        }

        assertEquals(List.of(cpId + 1), emitted,
                "the aborted epoch's straggler barrier must NOT reach the operator of a "
                        + "single-input task (no spurious snapshot / no downstream forwarding); "
                        + "the next live epoch passes through normally");
    }

    private static List<AbstractStreamOperator<?>> mockOperators(int count) {
        List<AbstractStreamOperator<?>> ops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ops.add(new AbstractStreamOperator<Object>() {
                private static final long serialVersionUID = 1L;
            });
        }
        return ops;
    }
}
