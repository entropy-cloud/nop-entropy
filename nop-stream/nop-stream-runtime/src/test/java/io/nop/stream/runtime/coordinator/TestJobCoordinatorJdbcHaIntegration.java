/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.JdbcCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcLeaderElector;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 46 Phase 4 — in-process HA integration proof: {@link JdbcLeaderElector} (real
 * JDBC lease table) drives {@link JobCoordinator} activation, and the G32 failover-safe
 * rebuild (Phase 1) restores the checkpoint view from {@link JdbcCheckpointStorage} on
 * leadership grant.
 *
 * <p>This is the wiring-verification test (plan guide #23): it proves the production
 * {@code JdbcLeaderElector} bean is actually wired into {@code JobCoordinator} and drives
 * the activateAsLeader → restoreFromCheckpoint path end-to-end within a single JVM
 * (single-process analog of the multi-JVM failover). Runs by default (no gating).
 *
 * <p>The multi-JVM proof (two real coordinator OS processes sharing one lease table) is
 * in {@code TestMultiJvmCoordinatorFailover} (gated).
 */
class TestJobCoordinatorJdbcHaIntegration {

    private static final String JOB_ID = "jdbc-ha-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC = new TaskLocation(JOB_ID, PIPELINE_ID, "source", 0);

    private static HikariDataSource dataSource;

    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    private IJdbcTemplate jdbcTemplate;
    private JdbcCheckpointStorage checkpointStorage;
    private DefaultScheduledExecutor electorExec;
    private CapturingTaskRpc rpc;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        for (String t : new String[]{"stream_checkpoint", "stream_epoch_manifest", "nop_stream_leader"}) {
            try {
                jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS " + t).end());
            } catch (Exception ignored) {
                // table may not exist yet on first run
            }
        }
        checkpointStorage = new JdbcCheckpointStorage(jdbcTemplate);
        electorExec = DefaultScheduledExecutor.newSingleThreadTimer("ha-integration");
        rpc = new CapturingTaskRpc();
    }

    @AfterEach
    void tearDown() {
        if (electorExec != null) {
            electorExec.destroy();
        }
    }

    /**
     * Wiring proof: a real {@link JdbcLeaderElector} granted leadership drives the
     * coordinator to ACTIVE, bootstraps assignments with the leadership-derived fencing
     * epoch, and — critically — rebuilds the checkpoint view from JDBC storage (G32).
     */
    @Test
    void testJdbcLeaderElectorDrivesActivationAndCheckpointRebuild() throws Exception {
        // Pre-seed durable checkpoint storage with epoch 4 (simulating a prior
        // coordinator's durable work that a fresh JVM must rebuild from).
        checkpointStorage.storeCheckPoint(buildCheckpoint(4L));

        JdbcLeaderElector elector = new JdbcLeaderElector(jdbcTemplate);
        elector.setClusterId(JOB_ID);
        elector.setHostId("host-ha-1");
        elector.setScheduledExecutor(electorExec);
        elector.setLeaseMs(10000);
        elector.setCheckIntervalMs(1000);
        elector.setLeaseSafeGap(1000);
        elector.setAddr("localhost");
        elector.setPort(0);

        CheckpointCoordinator checkpointCoord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), checkpointStorage, baseConfig());
        ClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode("node-1", "localhost:9001", 4);
        Map<String, IStreamTaskRpcService> rpcs = new java.util.HashMap<>();
        rpcs.put("node-1", rpc);

        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coord-ha-1", buildDeploymentPlan(),
                registry, checkpointCoord, rpcs);
        coordinator.setLeaderElector(elector);
        coordinator.setTerminationCheckpointTimeoutMs(500L);

        try {
            coordinator.start();
            assertFalse(coordinator.isActive(), "must start in STANDBY");

            // Start the real JDBC elector — it grants leadership via the shared lease row.
            elector.start();
            LeaderEpoch granted = elector.whenElectionCompleted().toCompletableFuture().get();
            assertNotNull(granted);
            assertEquals("host-ha-1", granted.getLeaderId());

            // Wait for the becomeLeader callback to activate the coordinator.
            long deadline = System.currentTimeMillis() + 3000;
            while (!coordinator.isActive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(coordinator.isActive(),
                    "JdbcLeaderElector grant must drive the coordinator to ACTIVE");

            // G32: the checkpoint view was rebuilt from JDBC storage (was null before activation).
            assertNotNull(checkpointCoord.getLatestCheckpoint(),
                    "activateAsLeader must have rebuilt latestCompletedCheckpoint from storage");
            assertEquals(4L, checkpointCoord.getLatestCheckpoint().getCheckpointId(),
                    "rebuilt view must be the durable epoch 4");

            // Fencing epoch derived from the granted leadership epoch (Stage 39 encoding).
            assertEquals(JobCoordinator.deriveHaFencingEpoch(granted.getEpoch(), 0L),
                    coordinator.getFencingEpoch());

            // Assignments bootstrapped with the leadership-derived fencing epoch.
            assertFalse(rpc.assignments.isEmpty(), "ACTIVE coordinator must issue assignments");
            assertEquals(coordinator.getFencingEpoch(), rpc.assignments.get(0).getFencingEpoch());

            // The next trigger produces epoch 5 (durable 4 + 1, counter advanced by restore).
            checkpointCoord.setTasksToAcknowledge(java.util.Collections.singletonList(LOC));
            PendingCheckpoint pending = checkpointCoord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            assertEquals(5L, pending.getCheckpointId(),
                    "first trigger after restoring epoch 4 must produce epoch 5");
        } finally {
            coordinator.stop();
            elector.stop();
        }
    }

    private CheckpointConfig baseConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    private CompletedCheckpoint buildCheckpoint(long checkpointId) {
        return CompletedCheckpoint.builder()
                .jobId(JOB_ID)
                .pipelineId(PIPELINE_ID)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC, TaskStateSnapshot.empty(LOC))
                .build();
    }

    private DeploymentPlan buildDeploymentPlan() {
        Map<String, PartitionedPlan.VertexPlan> vp = new LinkedHashMap<>();
        vp.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vp.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> ep = new ArrayList<>();
        ep.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan pp = new PartitionedPlan(JOB_ID, PIPELINE_ID, vp, ep, null, null);
        return new DeploymentPlan(JOB_ID, PIPELINE_ID, pp, "local", "memory", "local", null, null);
    }

    static class CapturingTaskRpc implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }
}
