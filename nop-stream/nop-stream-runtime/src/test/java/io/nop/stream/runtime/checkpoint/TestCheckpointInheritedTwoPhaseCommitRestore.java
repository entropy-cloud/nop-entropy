/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C3 × 2PC sink (review F-8): a COMPLETED two-phase-commit
 * sink task is contracted out of the checkpoint participant set, its durable
 * pending-commit state is INHERITED into the next epoch, and a restore from
 * that inherited-carrying checkpoint re-commits the durable-but-uncommitted
 * transaction — with the CON-01 epoch-ledger guard making a repeated
 * recovery-from-the-same-snapshot idempotent (no duplicate rows).
 *
 * <p>Scenario: epoch 1 acks the sink's snapshot carrying
 * {@code pending-commits[1]} (batch durable in the checkpoint, commit never
 * ran — the kill-after-ack window). The sink task then reaches its terminal
 * COMPLETED state and is contracted. Epoch 2 inherits the sink state. Restore
 * from the epoch-2 checkpoint re-commits the inherited epoch-1 batch; a second
 * recovery cycle from the SAME inherited snapshot must not duplicate.
 */
class TestCheckpointInheritedTwoPhaseCommitRestore {

    private static final TaskLocation LOC_A = new TaskLocation("job-tpc-inh", "pipe-0", "va", 0);
    private static final TaskLocation LOC_SINK = new TaskLocation("job-tpc-inh", "pipe-0", "vsink", 0);

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private LocalFileCheckpointStorage storage;
    private CheckpointCoordinator coordinator;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE target_data (id BIGINT, name VARCHAR(100), amount BIGINT)")) {
                ps.execute();
            }
        }

        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setAsyncSnapshotEnabled(false);
        config.setCheckpointInterval(1000);
        // Back-to-back epochs in this test must not hit the default minPause throttle.
        config.setMinPause(0L);
        coordinator = new CheckpointCoordinator("job-tpc-inh", "pipe-0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_A, LOC_SINK));
    }

    @AfterEach
    void tearDown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints("job-tpc-inh");
        dataSource.close();
    }

    private JdbcTwoPhaseCommitSink<Map<String, Object>> createJdbcSink() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(Function.identity())
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        return sink;
    }

    private Map<String, Object> row(long id, String name, long amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("amount", amount);
        return m;
    }

    private List<Map<String, Object>> sampleRecords() {
        return Arrays.asList(
                row(1L, "a", 100L), row(2L, "b", 200L), row(3L, "c", 300L),
                row(4L, "d", 400L), row(5L, "e", 500L));
    }

    private int countRows(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Wraps a sink saveState snapshot into the task-level participant-* form. */
    private TaskStateSnapshot participantTaskSnapshot(TaskLocation loc, long cpId,
                                                      TaskStateSnapshot sinkSnapshot) {
        TaskStateSnapshot taskSnapshot = new TaskStateSnapshot(loc, cpId);
        for (Map.Entry<String, Object> entry : sinkSnapshot.getOperatorStates().entrySet()) {
            taskSnapshot.putOperatorState("participant-" + entry.getKey(), entry.getValue());
        }
        return taskSnapshot;
    }

    /**
     * One recovery cycle from the inherited snapshot: rebuild the sink via
     * {@code StreamSinkOperator.restoreState} and run the durable re-commit
     * ({@code restoreFromEpoch}), then assert the exactly-once outcome. The
     * inherited snapshot already carries the production {@code participant-*}
     * operator-state keys (added at snapshot time by
     * {@code StreamSinkOperator.processBarrier}); they are copied verbatim, as
     * the production restore path does.
     */
    private void restoreFromInheritedAndAssert(TaskStateSnapshot inherited, long restoreEpoch)
            throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> recoveredSink = createJdbcSink();
        OperatorSnapshotResult snapshotResult = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> entry : inherited.getOperatorStates().entrySet()) {
            snapshotResult.putOperatorState(entry.getKey(), entry.getValue());
        }
        new StreamSinkOperator<>(recoveredSink).restoreState(snapshotResult);
        recoveredSink.restoreFromEpoch(restoreEpoch, null);

        assertEquals(5, countRows("target_data"),
                "the inherited durable batch must be re-committed exactly once");
        assertEquals(1, countRows("stream_epoch_ledger_v2"),
                "the CON-01 ledger records the epoch exactly once (composite-PK guard)");
        assertTrue(recoveredSink.getPendingCommits().isEmpty(),
                "all inherited pending commits are resolved after the restore re-commit");
    }

    @Test
    void testInherited2PCPendingCommitsRecommitIdempotentAfterRestore() throws Exception {
        // --- Epoch 1: the sink task acks its durable pending-commits state ---
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createJdbcSink();
        for (Map<String, Object> record : sampleRecords()) {
            sink.consume(record);
        }
        // saveState moves the batch into pendingCommits[1] and captures it.
        TaskStateSnapshot sinkSnapshot = sink.saveState(1L);
        sink.preCommit(1L);

        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1);
        long cp1 = p1.getCheckpointId();
        // No participant registered with the coordinator: the commit never runs
        // after the epoch turns durable (the kill-after-ack window).
        coordinator.acknowledgeTask(LOC_SINK, cp1,
                participantTaskSnapshot(LOC_SINK, cp1, sinkSnapshot));
        coordinator.acknowledgeTask(LOC_A, cp1,
                TaskStateSnapshot.builder(LOC_A).checkpointId(cp1)
                        .putOperatorState("op", "e1-va").build());
        CompletedCheckpoint c1 = p1.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(c1);

        assertEquals(0, countRows("target_data"),
                "durable-but-uncommitted window: the batch is only in the checkpoint");

        // --- The sink task reaches terminal COMPLETED and is contracted (C3) ---
        coordinator.markTaskCompleted(LOC_SINK);

        // --- Epoch 2: only the live task acks; the sink state is inherited ---
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2);
        assertEquals(1, p2.getNumberOfNotAcknowledgedTasks(),
                "the completed sink is contracted out of the participant set");
        assertEquals(1, p2.getNumberOfAcknowledgedTasks(),
                "the sink's state is pre-acknowledged (inherited)");
        long cp2 = p2.getCheckpointId();
        coordinator.acknowledgeTask(LOC_A, cp2,
                TaskStateSnapshot.builder(LOC_A).checkpointId(cp2)
                        .putOperatorState("op", "e2-va").build());
        CompletedCheckpoint c2 = p2.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(c2);

        // The epoch-2 checkpoint carries the inherited 2PC pending-commits state.
        TaskStateSnapshot inherited = c2.getTaskState(LOC_SINK);
        assertNotNull(inherited, "the new epoch must carry the completed sink's state");
        Object pendingState = inherited.getOperatorState("participant-pending-commits");
        assertNotNull(pendingState,
                "the inherited snapshot must carry the durable pending-commits map");
        @SuppressWarnings("unchecked")
        Map<Long, Object> pending = (Map<Long, Object>) pendingState;
        assertEquals(1, pending.size(), "exactly the epoch-1 batch is pending");
        assertTrue(pending.containsKey(1L),
                "the inherited pending batch is keyed by its original epoch: " + pending.keySet());

        // --- Restore #1 from the inherited-carrying checkpoint: re-commit ---
        restoreFromInheritedAndAssert(inherited, cp2);

        // --- Restore #2 from the SAME inherited snapshot (a second crash before
        // the commit became externally visible): the CON-01 ledger guard makes
        // the repeated re-commit a no-op — no duplicate rows. ---
        restoreFromInheritedAndAssert(inherited, cp2);
    }
}
