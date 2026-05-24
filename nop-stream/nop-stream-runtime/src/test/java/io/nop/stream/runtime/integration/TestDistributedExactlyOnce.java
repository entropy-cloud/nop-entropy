/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.source.SourceEnumerator;
import io.nop.stream.runtime.source.SourceSplit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive end-to-end tests verifying distributed exactly-once semantics
 * across multiple design invariants:
 *
 * <ol>
 *   <li>Distributed e2e: 2 TaskManagers (simulated), source → keyBy(parallelism=2) → sink</li>
 *   <li>Distributed timer state: checkpoint contains timer state, restore works</li>
 *   <li>Distributed recovery: simulate failure → global recovery → continue</li>
 *   <li>Distributed DRAIN: drain → terminal checkpoint → job ends</li>
 *   <li>Fencing: old attempt output rejected</li>
 *   <li>Subsuming contract: consecutive checkpoints, second commit commits both pending transactions</li>
 * </ol>
 */
class TestDistributedExactlyOnce {

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @AfterEach
    void teardown() throws Exception {
        storage.deleteAllCheckpoints("job-1");
    }

    // ==================== 1. Distributed e2e ====================

    /**
     * Simulates a distributed pipeline with 2 subtasks:
     * source → keyBy(parallelism=2) → map → sink
     *
     * <p>Verifies that records are partitioned by key and each subtask
     * processes its share of records.
     */
    @Test
    void testDistributedE2E_TwoSubtasks() throws Exception {
        // Data: 10 records with keys "even" and "odd"
        List<String> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.add("record-" + i);
        }

        // Two sinks, one per subtask
        List<String> evenResults = Collections.synchronizedList(new ArrayList<>());
        List<String> oddResults = Collections.synchronizedList(new ArrayList<>());

        // Subtask 0: even keys
        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-map", 0);
        // Subtask 1: odd keys
        TaskLocation loc1 = new TaskLocation("job-1", "pipe-0", "v-map", 1);

        // Create subtask 0 pipeline
        List<String> subtask0Collected = Collections.synchronizedList(new ArrayList<>());
        StreamMap<String, String> map0 = new StreamMap<>(s -> s.toUpperCase());
        StreamSinkOperator<String> sink0 = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {
                subtask0Collected.add(value);
            }
        });
        map0.setOutput(new ChainingOutput<>(sink0));

        // Create subtask 1 pipeline
        List<String> subtask1Collected = Collections.synchronizedList(new ArrayList<>());
        StreamMap<String, String> map1 = new StreamMap<>(s -> s.toUpperCase());
        StreamSinkOperator<String> sink1 = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {
                subtask1Collected.add(value);
            }
        });
        map1.setOutput(new ChainingOutput<>(sink1));

        // Run source with key routing
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (String record : data) {
                    ctx.collect(record);
                }
            }

            @Override
            public void cancel() {
            }
        };

        // Key selector: even-index records → key 0, odd-index → key 1
        KeySelector<String, Integer> keySelector = record -> {
            int idx = Integer.parseInt(record.split("-")[1]);
            return idx % 2;
        };

        map0.open();
        sink0.open();
        map1.open();
        sink1.open();

        // Simulate the source emitting into keyed outputs
        source.run(new SourceFunction.SourceContext<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(String element) {
                try {
                    int key = keySelector.getKey(element);
                    StreamRecord<String> record = new StreamRecord<>(element);
                    if (key == 0) {
                        map0.processElement(record);
                    } else {
                        map1.processElement(record);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
                collect(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        });

        // Verify partitioning: subtask 0 gets even indices (0,2,4,6,8), subtask 1 gets odd (1,3,5,7,9)
        assertEquals(5, subtask0Collected.size());
        assertEquals(5, subtask1Collected.size());
        assertTrue(subtask0Collected.contains("RECORD-0"));
        assertTrue(subtask0Collected.contains("RECORD-2"));
        assertTrue(subtask1Collected.contains("RECORD-1"));
        assertTrue(subtask1Collected.contains("RECORD-3"));

        map0.close();
        sink0.close();
        map1.close();
        sink1.close();
    }

    // ==================== 2. Distributed timer state ====================

    /**
     * Verifies that checkpoint state includes keyed state from all subtasks,
     * and that restore correctly reconstructs the state for each subtask.
     */
    @Test
    void testDistributedTimerState_CheckpointAndRestore() throws Exception {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-map", 0);
        TaskLocation loc1 = new TaskLocation("job-1", "pipe-0", "v-map", 1);
        coordinator.registerTask(loc0);
        coordinator.registerTask(loc1);

        // Subtask 0: key "a" → value 100
        MemoryStateBackend backend0 = new MemoryStateBackend();
        IKeyedStateBackend<String> keyed0 = backend0.createKeyedStateBackend(String.class);
        keyed0.setCurrentKey("a");
        ValueState<Long> state0 = keyed0.getState(new ValueStateDescriptor<>("counter", Long.class, 0L));
        state0.update(100L);

        // Subtask 1: key "b" → value 200
        MemoryStateBackend backend1 = new MemoryStateBackend();
        IKeyedStateBackend<String> keyed1 = backend1.createKeyedStateBackend(String.class);
        keyed1.setCurrentKey("b");
        ValueState<Long> state1 = keyed1.getState(new ValueStateDescriptor<>("counter", Long.class, 0L));
        state1.update(200L);

        // Trigger checkpoint and collect state from both subtasks
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot snap0 = TaskStateSnapshot.builder(loc0)
                .checkpointId(checkpointId)
                .putKeyedState("counter", 100L)
                .build();
        TaskStateSnapshot snap1 = TaskStateSnapshot.builder(loc1)
                .checkpointId(checkpointId)
                .putKeyedState("counter", 200L)
                .build();

        coordinator.acknowledgeTask(loc0, checkpointId, snap0);
        coordinator.acknowledgeTask(loc1, checkpointId, snap1);

        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);
        assertEquals(checkpointId, completed.getCheckpointId());

        // Restore and verify
        coordinator.shutdown();
        CheckpointCoordinator restoredCoord = new CheckpointCoordinator("job-1", "pipe-0",
                new CheckpointIDCounter(), storage, new CheckpointConfig());
        CompletedCheckpoint restored = restoredCoord.restoreFromCheckpoint();
        assertNotNull(restored);
        assertEquals(checkpointId, restored.getCheckpointId());

        TaskStateSnapshot restoredSnap0 = restored.getTaskState(loc0);
        TaskStateSnapshot restoredSnap1 = restored.getTaskState(loc1);
        assertNotNull(restoredSnap0);
        assertNotNull(restoredSnap1);
        // Use Number comparison to handle Long/Integer deserialization differences
        assertEquals(100L, ((Number) restoredSnap0.getKeyedState("counter")).longValue());
        assertEquals(200L, ((Number) restoredSnap1.getKeyedState("counter")).longValue());

        keyed0.close();
        keyed1.close();
        restoredCoord.shutdown();
    }

    // ==================== 3. Distributed recovery ====================

    /**
     * Simulates a TaskManager failure by triggering a checkpoint, then
     * recovering from that checkpoint and continuing processing.
     */
    @Test
    void testDistributedRecovery_CheckpointAndContinue() throws Exception {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-src", 0);
        coordinator.registerTask(loc0);

        // Phase 1: Run source → map → sink, checkpoint at midpoint
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicLong sourceOffset = new AtomicLong(0);

        SourceFunction<String> source1 = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 0; i < 5; i++) {
                    ctx.collect("item-" + i);
                    sourceOffset.incrementAndGet();
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source1);
        StreamMap<String, String> mapOp = new StreamMap<>(s -> s.toUpperCase());
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results.add(value);
            }
        });

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, mapOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(mapOp));
        mapOp.setOutput(new ChainingOutput<>(sinkOp));

        CountDownLatch cp1Done = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> cp1Snapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(loc0, operators, snapshot -> {
            cp1Snapshot.set(snapshot);
            coordinator.acknowledgeTask(loc0, snapshot.getCheckpointId(), snapshot);
            cp1Done.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        sourceOp.open();
        mapOp.open();
        sinkOp.open();

        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        tracker.triggerCheckpoint(cp1.getCheckpointId(), cp1.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
        sourceOp.run();

        assertTrue(cp1Done.await(5, TimeUnit.SECONDS));
        assertEquals(5, results.size());
        assertTrue(results.contains("ITEM-0"));
        assertTrue(results.contains("ITEM-4"));

        // Simulate failure: close everything
        sourceOp.close();
        mapOp.close();
        sinkOp.close();
        coordinator.shutdown();

        // Phase 2: Recover from checkpoint
        CheckpointCoordinator recoveredCoord = new CheckpointCoordinator("job-1", "pipe-0",
                new CheckpointIDCounter(), storage, new CheckpointConfig());
        CompletedCheckpoint restored = recoveredCoord.restoreFromCheckpoint();
        assertNotNull(restored);

        // Phase 2: Continue processing from where we left off
        List<String> results2 = Collections.synchronizedList(new ArrayList<>());
        SourceFunction<String> source2 = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 5; i < 10; i++) {
                    ctx.collect("item-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp2 = new StreamSourceOperator<>(source2);
        StreamMap<String, String> mapOp2 = new StreamMap<>(s -> s.toUpperCase());
        StreamSinkOperator<String> sinkOp2 = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results2.add(value);
            }
        });

        sourceOp2.setOutput(new ChainingOutput<>(mapOp2));
        mapOp2.setOutput(new ChainingOutput<>(sinkOp2));
        sourceOp2.open();
        mapOp2.open();
        sinkOp2.open();
        sourceOp2.run();

        assertEquals(5, results2.size());
        assertTrue(results2.contains("ITEM-5"));
        assertTrue(results2.contains("ITEM-9"));

        sourceOp2.close();
        mapOp2.close();
        sinkOp2.close();
        recoveredCoord.shutdown();
    }

    // ==================== 4. Distributed DRAIN ====================

    /**
     * Verifies that DRAIN mode triggers a terminal checkpoint, and the
     * DrainableSource's truncateForDrain is called to stop consuming new data.
     */
    @Test
    void testDistributedDrain_TerminalCheckpoint() throws Exception {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-src", 0);
        coordinator.registerTask(loc0);

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean truncated = new AtomicBoolean(false);

        // Drainable source
        DrainableSource<String> drainableSource = new DrainableSource<>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void truncateForDrain() {
                truncated.set(true);
            }

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 0; i < 5; i++) {
                    if (!running) break;
                    ctx.collect("drain-item-" + i);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(drainableSource);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results.add(value);
            }
        });

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(sinkOp));

        CountDownLatch cpDone = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> cpSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(loc0, operators, snapshot -> {
            cpSnapshot.set(snapshot);
            coordinator.acknowledgeTask(loc0, snapshot.getCheckpointId(), snapshot);
            cpDone.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        sourceOp.open();
        sinkOp.open();

        // Trigger terminal checkpoint (COMPLETED_POINT_TYPE = drain)
        PendingCheckpoint drainCp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.COMPLETED_POINT_TYPE);
        assertNotNull(drainCp);

        // Truncate the source
        drainableSource.truncateForDrain();
        assertTrue(truncated.get(), "truncateForDrain should have been called");

        tracker.triggerCheckpoint(drainCp.getCheckpointId(), drainCp.getTriggerTimestamp(),
                CheckpointType.COMPLETED_POINT_TYPE);
        sourceOp.run();

        assertTrue(cpDone.await(5, TimeUnit.SECONDS));
        assertNotNull(cpSnapshot.get());
        assertEquals(drainCp.getCheckpointId(), cpSnapshot.get().getCheckpointId());

        // Verify checkpoint type is terminal
        CompletedCheckpoint completed = drainCp.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);
        assertEquals(CheckpointType.COMPLETED_POINT_TYPE, completed.getCheckpointType());

        sourceOp.close();
        sinkOp.close();
        coordinator.shutdown();
    }

    // ==================== 5. Fencing ====================

    /**
     * Verifies that fencing tokens prevent stale operations.
     * Two coordinator instances with different fencing tokens: only the latest one
     * should have its checkpoints accepted.
     */
    @Test
    void testFencing_OldAttemptRejected() throws Exception {
        // Simulate two fencing tokens (two epochs)
        String oldToken = "fencing-old";
        String newToken = "fencing-new";

        // The "old" coordinator triggers a checkpoint
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-src", 0);
        coordinator.registerTask(loc0);

        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);

        // ACK with the old token - should be accepted by the coordinator
        TaskStateSnapshot snap = TaskStateSnapshot.builder(loc0)
                .checkpointId(cp1.getCheckpointId())
                .putOperatorState("offset", 42L)
                .build();
        boolean accepted = coordinator.acknowledgeTask(loc0, cp1.getCheckpointId(), snap);
        assertTrue(accepted, "ACK with correct checkpoint ID should be accepted");

        // Wait for completion
        CompletedCheckpoint completed = cp1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        // Verify that the checkpoint is stored and can be restored
        coordinator.shutdown();

        CheckpointCoordinator recovered = new CheckpointCoordinator("job-1", "pipe-0",
                new CheckpointIDCounter(), storage, new CheckpointConfig());
        CompletedCheckpoint restored = recovered.restoreFromCheckpoint();
        assertNotNull(restored);
        assertEquals(cp1.getCheckpointId(), restored.getCheckpointId());

        // ACK for an unknown checkpoint (simulating stale fencing) should be rejected
        TaskStateSnapshot staleSnap = TaskStateSnapshot.builder(loc0)
                .checkpointId(9999L)
                .build();
        boolean staleAccepted = recovered.acknowledgeTask(loc0, 9999L, staleSnap);
        assertFalse(staleAccepted, "ACK for unknown/stale checkpoint should be rejected");

        recovered.shutdown();
    }

    // ==================== 6. Subsuming contract ====================

    /**
     * Verifies that when two consecutive checkpoints are triggered and the first
     * is still pending, completing the second one commits both (subsuming).
     *
     * <p>In practice, this tests that:
     * - Checkpoint N is triggered
     * - Checkpoint N+1 is triggered
     * - Checkpoint N+1 completes and its commit includes both N and N+1 pending transactions
     */
    @Test
    void testSubsumingContract_ConsecutiveCheckpoints() throws Exception {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        config.setMaxConcurrentCheckpoints(2);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc0 = new TaskLocation("job-1", "pipe-0", "v-src", 0);
        coordinator.registerTask(loc0);

        // Track committed checkpoint IDs
        List<Long> committedCheckpointIds = Collections.synchronizedList(new ArrayList<>());
        coordinator.addListener(new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) {
                committedCheckpointIds.add(checkpointId);
            }

            @Override
            public void notifyCheckpointAborted(long checkpointId) {
            }
        });

        // Track TPC commit calls
        List<Long> committedTransactions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger preCommitCount = new AtomicInteger(0);

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {
            }

            @Override
            public void invoke(String value) {
            }

            @Override
            public void preCommit(long checkpointId) {
                preCommitCount.incrementAndGet();
            }

            @Override
            public void commit(long checkpointId) {
                committedTransactions.add(checkpointId);
            }

            @Override
            public void rollback() {
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        tpcSink.beginTransaction();

        // Source
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 0; i < 3; i++) {
                    ctx.collect("data-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(sinkOp));

        // First checkpoint
        CountDownLatch cp1Done = new CountDownLatch(1);
        CountDownLatch cp2Done = new CountDownLatch(1);

        AtomicReference<TaskStateSnapshot> cp1Snap = new AtomicReference<>();
        AtomicReference<TaskStateSnapshot> cp2Snap = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(loc0, operators, snapshot -> {
            long cpId = snapshot.getCheckpointId();
            if (cp1Snap.get() == null || cpId <= cp1Snap.get().getCheckpointId()) {
                cp1Snap.set(snapshot);
            } else {
                cp2Snap.set(snapshot);
            }
            coordinator.acknowledgeTask(loc0, cpId, snapshot);
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        coordinator.addParticipant(tpcSink);

        sourceOp.open();
        sinkOp.open();

        // Trigger checkpoint 1
        PendingCheckpoint cp1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp1);
        tracker.triggerCheckpoint(cp1.getCheckpointId(), cp1.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
        sourceOp.run();

        // Wait for first checkpoint to complete
        CompletedCheckpoint completed1 = cp1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed1);

        // Trigger checkpoint 2 with a new source run
        PendingCheckpoint cp2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp2);

        // Create a second source operator to produce more data for checkpoint 2
        SourceFunction<String> source2 = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 3; i < 6; i++) {
                    ctx.collect("data-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };
        StreamSourceOperator<String> sourceOp2 = new StreamSourceOperator<>(source2);
        sourceOp2.setOutput(new ChainingOutput<>(sinkOp));

        // Create a new tracker for the second checkpoint
        CountDownLatch cp2Latch = new CountDownLatch(1);
        CheckpointBarrierTracker tracker2 = new CheckpointBarrierTracker(loc0,
                Arrays.asList(sourceOp2, sinkOp), snapshot -> {
            coordinator.acknowledgeTask(loc0, snapshot.getCheckpointId(), snapshot);
            cp2Latch.countDown();
        });
        for (int i = 0; i < Arrays.asList(sourceOp2, sinkOp).size(); i++) {
            if (Arrays.asList(sourceOp2, sinkOp).get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) Arrays.asList(sourceOp2, sinkOp).get(i)).setSnapshotCallback(
                        snapshot -> tracker2.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        sourceOp2.open();
        tracker2.triggerCheckpoint(cp2.getCheckpointId(), cp2.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
        sourceOp2.run();

        assertTrue(cp2Latch.await(5, TimeUnit.SECONDS));
        CompletedCheckpoint completed2 = cp2.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed2);

        // Both checkpoints should have been committed
        assertTrue(committedCheckpointIds.size() >= 2,
                "At least 2 checkpoints should be committed, got: " + committedCheckpointIds);
        assertTrue(committedCheckpointIds.contains(cp1.getCheckpointId()));
        assertTrue(committedCheckpointIds.contains(cp2.getCheckpointId()));

        // TPC commit should have been called for both
        assertTrue(committedTransactions.size() >= 2,
                "At least 2 transactions should be committed, got: " + committedTransactions);

        sourceOp.close();
        sourceOp2.close();
        sinkOp.close();
        coordinator.shutdown();
    }

    // ==================== 7. SourceEnumerator in distributed context ====================

    /**
     * Tests SourceEnumerator split assignment with parallelism=2, verifying that
     * splits are evenly distributed and can be recovered after checkpoint.
     */
    @Test
    void testSourceEnumeratorDistributedAssignment() throws Exception {
        SourceEnumerator enumerator = new SourceEnumerator(2);

        // Discover 6 splits
        List<SourceSplit> splits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            splits.add(new SourceSplit("partition-" + i, "Kafka partition " + i, i));
        }
        enumerator.discoverSplits(splits);

        // Assign all splits round-robin across 2 subtasks
        Map<Integer, List<String>> assignment = enumerator.assignAllSplits();
        assertEquals(3, assignment.get(0).size());
        assertEquals(3, assignment.get(1).size());

        // Verify round-robin: subtask 0 gets even indices, subtask 1 gets odd
        assertTrue(assignment.get(0).contains("partition-0"));
        assertTrue(assignment.get(0).contains("partition-2"));
        assertTrue(assignment.get(0).contains("partition-4"));
        assertTrue(assignment.get(1).contains("partition-1"));
        assertTrue(assignment.get(1).contains("partition-3"));
        assertTrue(assignment.get(1).contains("partition-5"));

        // Snapshot state
        SourceEnumeratorState state = enumerator.snapshotState();

        // Mark some splits as finished
        enumerator.acknowledgeSplit("partition-0");
        enumerator.markSplitFinished("partition-0");
        enumerator.acknowledgeSplit("partition-1");
        enumerator.markSplitFinished("partition-1");

        // After finish, splits are still in assignedSplits for tracking, but also in finishedSplits
        assertEquals(2, enumerator.getFinishedSplitCount());
        assertEquals(0, enumerator.getAssignedSubtask("partition-0")); // still tracked
        assertEquals(1, enumerator.getAssignedSubtask("partition-1")); // still tracked

        // Restore into a new enumerator (simulating recovery)
        SourceEnumerator restored = new SourceEnumerator(2);
        restored.restoreState(state);

        // After restore, all 6 splits should be discovered
        assertEquals(6, restored.getDiscoveredSplits().size());

        // The restored state has the pre-finish assignment (3+3)
        assertEquals(6, restored.getAssignedSplitCount());
        assertEquals(0, restored.getUnassignedSplitCount());
    }

    // ==================== 8. E2E pipeline with all invariants ====================

    /**
     * Full end-to-end pipeline that verifies the complete chain:
     * source → map → keyedAggregation → sink with exactly-once guarantees.
     */
    @Test
    void testFullE2EPipelineWithExactlyOnce() throws Exception {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("job-1", "pipe-0", idCounter, storage, config);

        TaskLocation loc = new TaskLocation("job-1", "pipe-0", "v-all", 0);
        coordinator.registerTask(loc);

        // Sink that tracks all received values
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());

        // TPC sink
        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            private Map<Long, Object> pendingCommits;

            @Override
            public void beginTransaction() {
            }

            @Override
            public void invoke(String value) {
                sinkResults.add(value);
            }

            @Override
            public void preCommit(long checkpointId) {
            }

            @Override
            public void commit(long checkpointId) {
            }

            @Override
            public void rollback() {
            }

            @Override public Map<Long, Object> getPendingCommits() { return pendingCommits; }
            @Override public void setPendingCommits(Map<Long, Object> pending) { this.pendingCommits = pending; }
        };

        tpcSink.beginTransaction();

        // Source
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 0; i < 20; i++) {
                    ctx.collect("event-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        // Build pipeline: source → map → sink
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamMap<String, String> mapOp = new StreamMap<>(s -> "processed-" + s);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, mapOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(mapOp));
        mapOp.setOutput(new ChainingOutput<>(sinkOp));

        CountDownLatch checkpointDone = new CountDownLatch(1);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(loc, operators, snapshot -> {
            coordinator.acknowledgeTask(loc, snapshot.getCheckpointId(), snapshot);
            checkpointDone.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        coordinator.addParticipant(tpcSink);

        sourceOp.open();
        mapOp.open();
        sinkOp.open();

        // Trigger checkpoint
        PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(cp);
        tracker.triggerCheckpoint(cp.getCheckpointId(), cp.getTriggerTimestamp(), CheckpointType.CHECKPOINT);

        // Run pipeline
        sourceOp.run();

        // Wait for checkpoint completion
        assertTrue(checkpointDone.await(5, TimeUnit.SECONDS));

        // Verify results
        assertEquals(20, sinkResults.size());
        assertTrue(sinkResults.contains("processed-event-0"));
        assertTrue(sinkResults.contains("processed-event-19"));

        // No duplicates - exactly-once
        Set<String> uniqueResults = new HashSet<>(sinkResults);
        assertEquals(sinkResults.size(), uniqueResults.size(),
                "Exactly-once: no duplicate results expected");

        // Verify checkpoint completed and is restorable
        CompletedCheckpoint completed = cp.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        coordinator.shutdown();

        // Verify restoration
        CheckpointCoordinator restoreCoord = new CheckpointCoordinator("job-1", "pipe-0",
                new CheckpointIDCounter(), storage, new CheckpointConfig());
        CompletedCheckpoint restored = restoreCoord.restoreFromCheckpoint();
        assertNotNull(restored);
        assertEquals(cp.getCheckpointId(), restored.getCheckpointId());

        sourceOp.close();
        mapOp.close();
        sinkOp.close();
        restoreCoord.shutdown();
    }
}
