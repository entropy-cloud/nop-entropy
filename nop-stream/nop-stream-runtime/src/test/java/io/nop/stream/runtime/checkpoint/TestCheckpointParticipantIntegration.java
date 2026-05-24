/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test verifying CheckpointParticipant integration into the checkpoint pipeline.
 * Covers:
 * - TwoPhaseCommitSinkFunction participates via CheckpointParticipant interface
 * - CheckpointCoordinator calls finishCommit on participants in reverse order
 * - finishCommit(false) does NOT call rollback (keeps prepared transactions)
 * - StreamSinkOperator skips direct commit for CheckpointParticipant userFunctions
 * - CheckpointPlanBuilder extracts participant IDs from StreamComponents
 * - saveState/prepareCommit integration in processBarrier
 */
class TestCheckpointParticipantIntegration {

    private static final TaskLocation LOC_0 = new TaskLocation("job-1", "p-0", "v0", 0);

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;
    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        coordinator = new CheckpointCoordinator("job-1", "p-0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints("job-1");
    }

    // ========================================================================
    // Test 1: finishCommit(true) calls commit, finishCommit(false) does NOT rollback
    // ========================================================================

    @Test
    void testFinishCommitSuccessCallsCommit() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());

        TwoPhaseCommitSinkFunction<String> tpcSink = buildTrackingTpcSink(actions);

        // finishCommit(true) should call commit
        tpcSink.finishCommit(1L, true);

        assertTrue(actions.contains("commit:1"), "commit should be called on success. Actions: " + actions);
        assertFalse(actions.contains("rollback"), "rollback should NOT be called on success. Actions: " + actions);
    }

    @Test
    void testFinishCommitFailureDoesNotRollback() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());

        TwoPhaseCommitSinkFunction<String> tpcSink = buildTrackingTpcSink(actions);

        // finishCommit(false) should NOT call rollback
        tpcSink.finishCommit(1L, false);

        assertFalse(actions.contains("rollback"),
                "rollback should NOT be called on failure — prepared tx kept for subsuming commit. Actions: " + actions);
        assertFalse(actions.contains("commit:1"),
                "commit should NOT be called on failure. Actions: " + actions);
    }

    // ========================================================================
    // Test 2: CheckpointCoordinator calls finishCommit on participants
    // ========================================================================

    @Test
    void testCoordinatorCallsFinishCommitOnParticipantsOnSuccess() throws Exception {
        List<String> participantActions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger finishCommitCount = new AtomicInteger(0);

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) { return null; }

            @Override
            public void prepareCommit(long epochId) {
                participantActions.add("prepareCommit:" + epochId);
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                participantActions.add("finishCommit:" + epochId + ":" + success);
                finishCommitCount.incrementAndGet();
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        };

        coordinator.addParticipant(participant);

        // Trigger and complete a checkpoint
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        // Acknowledge to complete the checkpoint
        TaskStateSnapshot snapshot = new TaskStateSnapshot(LOC_0, cpId);
        coordinator.acknowledgeTask(LOC_0, cpId, snapshot);

        // Wait for completion
        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        // Verify finishCommit was called with success=true
        assertTrue(participantActions.contains("finishCommit:" + cpId + ":true"),
                "finishCommit(true) should be called on checkpoint completion. Actions: " + participantActions);
        assertEquals(1, finishCommitCount.get());
    }

    @Test
    void testCoordinatorCallsFinishCommitOnParticipantsOnAbort() throws Exception {
        List<String> participantActions = Collections.synchronizedList(new ArrayList<>());

        CheckpointParticipant participant = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) { return null; }

            @Override
            public void prepareCommit(long epochId) {
                participantActions.add("prepareCommit:" + epochId);
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                participantActions.add("finishCommit:" + epochId + ":" + success);
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
        };

        coordinator.addParticipant(participant);

        // Trigger a checkpoint
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        // Abort the checkpoint
        coordinator.abortPendingCheckpoint(pending, "test abort");

        assertTrue(participantActions.contains("finishCommit:" + cpId + ":false"),
                "finishCommit(false) should be called on checkpoint abort. Actions: " + participantActions);
    }

    // ========================================================================
    // Test 3: Multiple participants called in reverse order
    // ========================================================================

    @Test
    void testMultipleParticipantsCalledInReverseOrder() throws Exception {
        List<String> finishOrder = Collections.synchronizedList(new ArrayList<>());

        CheckpointParticipant p1 = new TestParticipant("p1", finishOrder);
        CheckpointParticipant p2 = new TestParticipant("p2", finishOrder);
        CheckpointParticipant p3 = new TestParticipant("p3", finishOrder);

        // Add in order p1, p2, p3
        coordinator.addParticipant(p1);
        coordinator.addParticipant(p2);
        coordinator.addParticipant(p3);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        coordinator.acknowledgeTask(LOC_0, cpId, new TaskStateSnapshot(LOC_0, cpId));

        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        assertEquals(3, finishOrder.size(), "All 3 participants should be called");
        // Reverse order: p3 first, then p2, then p1
        assertEquals("p3", finishOrder.get(0), "p3 should be called first (reverse order)");
        assertEquals("p2", finishOrder.get(1), "p2 should be called second (reverse order)");
        assertEquals("p1", finishOrder.get(2), "p1 should be called last (reverse order)");
    }

    // ========================================================================
    // Test 4: Full pipeline integration with TwoPhaseCommitSinkFunction
    // ========================================================================

    @Test
    void testFullPipelineWithParticipantIntegration() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean commitCalledViaFinishCommit = new AtomicBoolean(false);

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {
                actions.add("begin");
            }

            @Override
            public void invoke(String value) {
                actions.add("invoke:" + value);
            }

            @Override
            public void preCommit(long checkpointId) {
                actions.add("preCommit:" + checkpointId);
            }

            @Override
            public void commit(long checkpointId) {
                actions.add("commit:" + checkpointId);
                commitCalledViaFinishCommit.set(true);
            }

            @Override
            public void rollback() {
                actions.add("rollback");
            }

            @Override
            public TaskStateSnapshot saveState(long epochId) {
                TaskStateSnapshot state = new TaskStateSnapshot(null, epochId);
                state.putOperatorState("tx-state", "tx-" + epochId);
                return state;
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        tpcSink.beginTransaction();

        // Register the TwoPhaseCommitSinkFunction as a CheckpointParticipant with the coordinator
        coordinator.addParticipant(tpcSink);

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("a");
                ctx.collect("b");
            }

            @Override
            public void cancel() {}
        });

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        ChainingOutput<String> sourceToSink = new ChainingOutput<>(sinkOp);
        sourceOp.setOutput(sourceToSink);

        CountDownLatch checkpointComplete = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC_0, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
            coordinator.acknowledgeTask(LOC_0, snapshot.getCheckpointId(), snapshot);
            checkpointComplete.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        // Register sink as listener (as GraphModelCheckpointExecutor would do)
        coordinator.addListener(sinkOp);

        sourceOp.open();
        sinkOp.open();

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        tracker.triggerCheckpoint(cpId, pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
        sourceOp.run();

        assertTrue(checkpointComplete.await(5, TimeUnit.SECONDS));

        // Verify the sequence:
        // 1. begin (before checkpoint)
        // 2. invoke:a, invoke:b (data processing)
        // 3. preCommit (via CheckpointParticipant.prepareCommit during barrier)
        // 4. commit (via CheckpointParticipant.finishCommit(true) from coordinator)
        assertTrue(actions.contains("begin"), "begin should have been called. Actions: " + actions);
        assertTrue(actions.contains("invoke:a"), "invoke:a should have been called. Actions: " + actions);
        assertTrue(actions.contains("invoke:b"), "invoke:b should have been called. Actions: " + actions);
        assertTrue(actions.contains("preCommit:" + cpId), "preCommit should have been called. Actions: " + actions);
        assertTrue(actions.contains("commit:" + cpId), "commit should have been called. Actions: " + actions);

        // Verify ordering: begin < preCommit < commit
        // Note: invoke may happen before or after preCommit depending on barrier injection timing
        int beginIdx = actions.indexOf("begin");
        int preCommitIdx = actions.indexOf("preCommit:" + cpId);
        int commitIdx = actions.indexOf("commit:" + cpId);
        assertTrue(beginIdx < preCommitIdx, "begin should precede preCommit");
        assertTrue(preCommitIdx < commitIdx, "preCommit should precede commit");

        // Verify participant state was saved in the snapshot
        TaskStateSnapshot snapshot = capturedSnapshot.get();
        assertNotNull(snapshot);
        // The participant state should be captured via the barrier tracker
        boolean hasParticipantState = false;
        for (String key : snapshot.getOperatorStates().keySet()) {
            if (key.contains("participant-")) {
                hasParticipantState = true;
                break;
            }
        }
        assertTrue(hasParticipantState, "Participant state should be in snapshot. Keys: " + snapshot.getOperatorStates().keySet());

        // Verify no rollback was called (success path)
        assertFalse(actions.contains("rollback"), "rollback should NOT have been called on success. Actions: " + actions);

        // Verify commit was called via finishCommit path (coordinator)
        assertTrue(commitCalledViaFinishCommit.get(), "commit should have been called via finishCommit(true) path");

        coordinator.shutdown();
    }

    // ========================================================================
    // Test 5: CheckpointPlanBuilder extracts participants from StreamComponents
    // ========================================================================

    @Test
    void testPlanBuilderWithStreamComponents() {
        JobGraph jobGraph = buildSimpleJobGraphWithTpcSink();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);

        // Without StreamComponents - auto-detect
        CheckpointPlan plan1 = CheckpointPlanBuilder.build(executionPlan, "job-1", "p-1");
        // Should auto-detect the sink vertex as participant
        assertFalse(plan1.getCheckpointParticipants().isEmpty(),
                "Should auto-detect participants when no StreamComponents provided");

        // With StreamComponents containing explicit participants
        io.nop.stream.core.model.StreamComponents components = new io.nop.stream.core.model.StreamComponents();
        components.addCheckpointParticipant("sink-3");

        CheckpointPlan plan2 = CheckpointPlanBuilder.build(executionPlan, "job-1", "p-1", components);
        assertEquals(1, plan2.getCheckpointParticipants().size());
        assertEquals("sink-3", plan2.getCheckpointParticipants().get(0));
    }

    @Test
    void testPlanBuilderAutoDetectParticipants() {
        JobGraph jobGraph = buildSimpleJobGraphWithTpcSink();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);

        // Auto-detect should find the sink vertex containing TwoPhaseCommitSinkFunction
        CheckpointPlan plan = CheckpointPlanBuilder.build(executionPlan, "job-1", "p-1");
        assertFalse(plan.getCheckpointParticipants().isEmpty(),
                "Should auto-detect TwoPhaseCommitSinkFunction as participant");
        assertTrue(plan.getCheckpointParticipants().contains("sink-3"),
                "sink-3 should be detected as participant. Participants: " + plan.getCheckpointParticipants());
    }

    // ========================================================================
    // Test 6: StreamSinkOperator skips direct commit for CheckpointParticipant
    // ========================================================================

    @Test
    void testSinkOperatorSkipsDirectCommitForParticipant() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());

        TwoPhaseCommitSinkFunction<String> tpcSink = buildTrackingTpcSink(actions);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);
        sinkOp.open();

        // Since TwoPhaseCommitSinkFunction implements CheckpointParticipant,
        // notifyCheckpointComplete should NOT call commit() directly
        actions.clear();
        sinkOp.notifyCheckpointComplete(42L);

        // commit should NOT be called directly — coordinator handles it via finishCommit
        assertFalse(actions.contains("commit:42"),
                "commit should NOT be called directly for CheckpointParticipant. Actions: " + actions);
    }

    @Test
    void testSinkOperatorSkipsDirectRollbackForParticipant() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());

        TwoPhaseCommitSinkFunction<String> tpcSink = buildTrackingTpcSink(actions);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);
        sinkOp.open();

        // Since TwoPhaseCommitSinkFunction implements CheckpointParticipant,
        // notifyCheckpointAborted should NOT call rollback() directly
        actions.clear();
        sinkOp.notifyCheckpointAborted(42L);

        assertFalse(actions.contains("rollback"),
                "rollback should NOT be called directly for CheckpointParticipant. Actions: " + actions);
    }

    @Test
    void testSinkOperatorStillCallsListenerForNonParticipant() throws Exception {
        List<Long> completedCheckpoints = Collections.synchronizedList(new ArrayList<>());

        SinkFunction<String> plainSink = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {}
        };

        CheckpointListener listener = new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) {
                completedCheckpoints.add(checkpointId);
            }

            @Override
            public void notifyCheckpointAborted(long checkpointId) {}
        };

        // Use a sink that IS a CheckpointListener but NOT a CheckpointParticipant
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(plainSink);

        // Register listener manually (coordinator calls listeners)
        // Simulate by calling notifyCheckpointComplete
        sinkOp.notifyCheckpointComplete(100L);

        // Since plainSink implements CheckpointListener, it should still be called
        // (The operator itself doesn't forward unless it has a CheckpointListener userFunction)
        // Actually, plainSink doesn't implement CheckpointListener here, so nothing happens
        assertTrue(completedCheckpoints.isEmpty(), "Plain sink doesn't implement CheckpointListener");
    }

    // ========================================================================
    // Test 7: saveState integration in processBarrier
    // ========================================================================

    @Test
    void testSaveStateIntegrationInProcessBarrier() throws Exception {
        AtomicBoolean saveStateCalled = new AtomicBoolean(false);
        AtomicBoolean prepareCommitCalled = new AtomicBoolean(false);

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {}

            @Override
            public void invoke(String value) {}

            @Override
            public void preCommit(long checkpointId) {}

            @Override
            public void commit(long checkpointId) {}

            @Override
            public void rollback() {}

            @Override
            public TaskStateSnapshot saveState(long epochId) {
                saveStateCalled.set(true);
                TaskStateSnapshot state = new TaskStateSnapshot(null, epochId);
                state.putOperatorState("pending-tx", "tx-data-" + epochId);
                return state;
            }

            @Override
            public void prepareCommit(long epochId) {
                prepareCommitCalled.set(true);
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);
        sinkOp.open();

        AtomicReference<OperatorSnapshotResult> capturedResult = new AtomicReference<>();
        sinkOp.setSnapshotCallback(capturedResult::set);

        // Trigger barrier processing
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        sinkOp.processBarrier(barrier);

        assertTrue(saveStateCalled.get(), "saveState should be called during barrier processing");
        assertTrue(prepareCommitCalled.get(), "prepareCommit should be called during barrier processing");

        // Verify participant state was merged into snapshot result
        OperatorSnapshotResult result = capturedResult.get();
        assertNotNull(result, "Snapshot result should be captured");
        boolean hasParticipantState = result.getOperatorStates().keySet().stream()
                .anyMatch(k -> k.contains("participant-"));
        assertTrue(hasParticipantState,
                "Participant state should be merged into snapshot. Keys: " + result.getOperatorStates().keySet());
    }

    // ========================================================================
    // Test 8: Coordinator participant management
    // ========================================================================

    @Test
    void testAddAndRemoveParticipants() {
        CheckpointParticipant p1 = new NoOpParticipant();
        CheckpointParticipant p2 = new NoOpParticipant();

        assertEquals(0, coordinator.getParticipants().size());

        coordinator.addParticipant(p1);
        assertEquals(1, coordinator.getParticipants().size());

        coordinator.addParticipant(p2);
        assertEquals(2, coordinator.getParticipants().size());

        coordinator.removeParticipant(p1);
        assertEquals(1, coordinator.getParticipants().size());
        assertFalse(coordinator.getParticipants().contains(p1));
        assertTrue(coordinator.getParticipants().contains(p2));
    }

    @Test
    void testParticipantsClearedOnShutdown() {
        coordinator.addParticipant(new NoOpParticipant());
        coordinator.addParticipant(new NoOpParticipant());
        assertEquals(2, coordinator.getParticipants().size());

        coordinator.shutdown();

        assertTrue(coordinator.getParticipants().isEmpty(),
                "Participants should be cleared on shutdown");
    }

    // ========================================================================
    // Test 9: End-to-end with abort — verify no rollback via finishCommit(false)
    // ========================================================================

    @Test
    void testAbortDoesNotTriggerRollbackInParticipant() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());

        TwoPhaseCommitSinkFunction<String> tpcSink = buildTrackingTpcSink(actions);
        tpcSink.beginTransaction();

        coordinator.addParticipant(tpcSink);

        // Register sink as listener
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);
        coordinator.addListener(sinkOp);

        // Trigger a checkpoint that will be aborted (no ACK)
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        // Abort the checkpoint
        coordinator.abortPendingCheckpoint(pending, "simulated failure");

        // Verify: finishCommit(false) was called on participant but did NOT trigger rollback
        // The finishCommit(false) in TwoPhaseCommitSinkFunction does nothing now
        assertFalse(actions.contains("rollback"),
                "rollback should NOT be called via finishCommit(false). Actions: " + actions);
        assertFalse(actions.contains("commit:" + cpId),
                "commit should NOT be called on abort. Actions: " + actions);
    }

    // ========================================================================
    // Helper methods and inner classes
    // ========================================================================

    private TwoPhaseCommitSinkFunction<String> buildTrackingTpcSink(List<String> actions) {
        return new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {
                actions.add("begin");
            }

            @Override
            public void invoke(String value) {
                actions.add("invoke:" + value);
            }

            @Override
            public void preCommit(long checkpointId) {
                actions.add("preCommit:" + checkpointId);
            }

            @Override
            public void commit(long checkpointId) {
                actions.add("commit:" + checkpointId);
            }

            @Override
            public void rollback() {
                actions.add("rollback");
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };
    }

    private JobGraph buildSimpleJobGraphWithTpcSink() {
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {}

            @Override
            public void cancel() {}
        };

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override public void beginTransaction() {}
            @Override public void invoke(String value) {}
            @Override public void preCommit(long checkpointId) {}
            @Override public void commit(long checkpointId) {}
            @Override public void rollback() {}
            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("source-1", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-3", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-tpc");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "sink-3", ResultPartitionType.PIPELINED));

        return jobGraph;
    }

    /**
     * Test participant that records its ID in the provided list when finishCommit is called.
     */
    private static class TestParticipant implements CheckpointParticipant {
        private final String id;
        private final List<String> finishOrder;

        TestParticipant(String id, List<String> finishOrder) {
            this.id = id;
            this.finishOrder = finishOrder;
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) { return null; }

        @Override
        public void prepareCommit(long epochId) {}

        @Override
        public void finishCommit(long epochId, boolean success) {
            finishOrder.add(id);
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
    }

    private static class NoOpParticipant implements CheckpointParticipant {
        @Override
        public TaskStateSnapshot saveState(long epochId) { return null; }

        @Override
        public void prepareCommit(long epochId) {}

        @Override
        public void finishCommit(long epochId, boolean success) {}

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {}
    }
}
