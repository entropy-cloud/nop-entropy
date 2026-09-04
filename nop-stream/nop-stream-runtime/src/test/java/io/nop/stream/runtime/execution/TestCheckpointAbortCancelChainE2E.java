/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
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
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-7 end-to-end proof (plan 1326-2 Phase 3): the checkpoint-abort → cancel →
 * terminal-state chain. A MIDDLE task cancelled through the REAL production
 * abort handler (driven by the REAL coordinator abort flow) must NOT finalize
 * its truncated stream as a bounded-complete one: no finish() (2PC sink commit
 * window stays closed), no MAX_WATERMARK, and — the downstream-visible half —
 * no EOS on the output partition, so an un-cancelled downstream task cannot
 * mistake the truncated stream for a complete one and commit it.
 */
class TestCheckpointAbortCancelChainE2E {

    private static final TaskLocation LOC = new TaskLocation("abort-cancel-job", "v0", "v0", 0);

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
        coordinator = new CheckpointCoordinator("abort-cancel-job", "v0", idCounter, storage, config);
        coordinator.registerTask(LOC);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    /** Chain element recording finish()/MAX_WATERMARK arrival. */
    static class RecordingOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Input<String> {

        private static final long serialVersionUID = 1L;

        final AtomicBoolean finishCalled = new AtomicBoolean(false);
        final AtomicBoolean sawMaxWatermark = new AtomicBoolean(false);
        final CountDownLatch elementLatch = new CountDownLatch(1);

        @Override
        public void processElement(StreamRecord<String> element) {
            elementLatch.countDown();
        }

        @Override
        public void processWatermark(Watermark mark) {
            if (mark.getTimestamp() == Long.MAX_VALUE) {
                sawMaxWatermark.set(true);
            }
            if (getOutput() != null) {
                getOutput().emitWatermark(mark);
            }
        }

        @Override
        public void finish() throws Exception {
            finishCalled.set(true);
            super.finish();
        }
    }

    @Test
    void testAbortedCheckpointCancelsMiddleTaskWithoutSuccessTerminalState() throws Exception {
        RecordingOperator op = new RecordingOperator();
        OperatorChain chain = new OperatorChain(new ArrayList<>(List.of(op)));

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);
        ResultPartition downstream = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, writer, gate);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                LOC, new ArrayList<>(List.of(op)), snapshot -> { /* completion sink */ });
        invokable.setBarrierTracker(tracker);

        Subtask subtask = new Subtask("v0", 0, LOC, invokable);
        SubtaskTask task = new SubtaskTask(subtask, new JobVertex("v0", "v0", 1,
                java.util.Collections.singletonList(chain), invokable));

        // register the REAL production abort handler
        Map<String, SubtaskTask> tasks = new ConcurrentHashMap<>();
        tasks.put("v0-0", task);
        GraphModelCheckpointExecutor.registerLocalAbortHandler(coordinator, tasks);

        // one record, upstream stays OPEN (truncated stream — no EOS)
        upstream.write(new StreamRecord<>("a", 0));

        // run the task through the REAL SubtaskTask lifecycle
        Thread taskThread = new Thread(task, "abort-cancel-middle");
        taskThread.start();
        assertTrue(op.elementLatch.await(10, TimeUnit.SECONDS),
                "task must process the record before the abort");

        // REAL abort flow: coordinator abort → production handler → tracker cleanup +
        // gate abort + cooperative cancel + interrupt
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertTrue(tracker.triggerCheckpoint(pending.getCheckpointId(), 0L, CheckpointType.CHECKPOINT));
        coordinator.abortPendingCheckpoint(pending, "abort-cancel chain e2e");

        taskThread.join(30_000);
        assertFalse(taskThread.isAlive(), "task thread must exit after abort-cancel");
        assertEquals(SubtaskTask.State.CANCELED, task.getState(),
                "the cancelled task must land in CANCELED, not COMPLETED");

        // AR-7: no success terminal state on the cancelled task
        assertFalse(op.finishCalled.get(),
                "the aborted-cancelled MIDDLE task must NOT run finish() "
                        + "(2PC sink commit window closed on a truncated stream)");
        assertFalse(op.sawMaxWatermark.get(),
                "the aborted-cancelled MIDDLE task must NOT emit MAX_WATERMARK");

        // the downstream-visible half: no EOS on the output partition — an
        // un-cancelled downstream consumer cannot mistake the truncated stream
        // for a bounded-complete one
        assertFalse(downstream.isFinished(),
                "the aborted-cancelled MIDDLE task must NOT send EOS downstream "
                        + "(the truncated stream must not be committable as complete)");

        // the aborted epoch's barrier is still filtered by the gate (AR-5 chain)
        upstream.write(new CheckpointBarrier(pending.getCheckpointId(), 0, CheckpointType.CHECKPOINT));
        upstream.close();
        List<Long> emitted = new ArrayList<>();
        while (true) {
            java.util.Optional<io.nop.stream.core.streamrecord.StreamElement> e = gate.read();
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
        assertTrue(emitted.isEmpty(),
                "the aborted epoch's straggler barrier must not be forwarded downstream "
                        + "after the cancel (full chain: abort → cancel → no spurious barrier)");
    }
}
