/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 34 (HA failover takeover) focused tests — attempt-counter seeding from the
 * registry's persisted attempt history on leadership activation.
 *
 * <p>Defect being pinned: a fresh coordinator JVM taking over a shared registry that
 * still holds the old leader's {@code nop_stream_task_assignment} rows used to
 * re-issue attempt numbers from 1 and collide on the (job_id, vertex_id,
 * subtask_index, attempt_number) primary key, aborting the become-leader listener
 * (the platform elector swallows the exception) and freezing the job half-activated.
 *
 * <p>Coverage (plan Phase 1):
 * <ul>
 *   <li>Takeover completion: preset attempts 1..k → fresh coordinator activation
 *       assigns k+1 (strict continuation), no duplicate-key, old rows preserved
 *       (G56 append-only), new rows carry the new leader's fencing epoch.</li>
 *   <li>Regression pinning: fresh-job activation starts attempts at 1; same-JVM
 *       re-activation (in-memory counters already present) neither regresses nor
 *       re-issues duplicate attempt numbers.</li>
 *   <li>InMemory parity: the same takeover scenario over a shared
 *       {@link InMemoryClusterRegistry} stays monotonic (the registry's
 *       "Attempt number regression" caller-bug WARN branch is never exercised —
 *       its trigger condition is attemptNumber <= lastAttempt).</li>
 *   <li>No silent skip (guide #24): a registry read failure during seeding
 *       de-activates the coordinator (explicit STANDBY) and rethrows a typed
 *       exception — never a frozen half-active leader.</li>
 * </ul>
 */
class TestJobCoordinatorAttemptSeedOnTakeover {

    private static final String JOB_ID = "attempt-seed-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final String NODE_1 = "node-1";
    private static final int PRESET_ATTEMPTS = 3;

    private static HikariDataSource dataSource;

    @TempDir
    Path tempDir;

    private IJdbcTemplate jdbcTemplate;

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
        // Fresh registry tables per test (MiniStreamCluster parity: each cluster start
        // drops the stream tables; only the "old leader rows persisted + fresh
        // coordinator activates" window triggers the defect).
        for (String t : new String[]{"nop_stream_task_assignment", "nop_stream_node", "nop_stream_coordinator"}) {
            try {
                jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS " + t).end());
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    // ==================== Takeover completion (JDBC/H2) ====================

    /**
     * Phase 1 Exit Criterion: preset attempt_number=1..k rows for the same jobId → a
     * second, fresh {@link JobCoordinator} instance activates over the same JDBC
     * registry → (1) new assignment rows land with attempt_number = k+1 (strict
     * continuation), (2) no duplicate-key exception aborts the activation, (3)
     * {@code getAttemptHistory} is monotonically increasing and the old rows are
     * preserved verbatim (G56 append-only).
     */
    @Test
    void testTakeoverAssignmentContinuesPresetAttemptHistory() throws Exception {
        JdbcClusterRegistry registry = new JdbcClusterRegistry(jdbcTemplate);
        long oldEpoch = JobCoordinator.deriveHaFencingEpoch(1L, 0L);
        presetOldLeaderRows(registry, oldEpoch, PRESET_ATTEMPTS);

        // Sanity: the preset history is really persisted before takeover.
        List<TaskAssignment> before = registry.getAttemptHistory(JOB_ID, "source", 0);
        assertEquals(PRESET_ATTEMPTS, before.size(), "preset rows must be persisted before takeover");

        TestLeaderElector elector = new TestLeaderElector("host-takeover");
        RecordingTaskRpcService rpc = new RecordingTaskRpcService();
        JobCoordinator coordinator = buildFreshCoordinator(registry, elector, rpc);
        coordinator.start();
        assertFalse(coordinator.isActive(), "fresh HA coordinator must start in STANDBY");

        // The takeover: grant leadership → activateAsLeader → seed → assign.
        // TestLeaderElector propagates listener exceptions, so reaching the assertion
        // below without a throw is itself the "no duplicate-key" proof.
        elector.grantLeadership(2L);

        assertTrue(coordinator.isActive(), "coordinator must complete activation after takeover");
        long newEpoch = JobCoordinator.deriveHaFencingEpoch(2L, 0L);

        for (String vertexId : new String[]{"source", "sink"}) {
            int parallelism = "source".equals(vertexId) ? 2 : 1;
            for (int s = 0; s < parallelism; s++) {
                List<TaskAssignment> history = registry.getAttemptHistory(JOB_ID, vertexId, s);
                assertEquals(PRESET_ATTEMPTS + 1, history.size(),
                        "history must gain exactly one row per subtask (append-only)");
                for (int i = 0; i < history.size(); i++) {
                    assertEquals(i + 1, history.get(i).getAttemptNumber(),
                            "attempt numbers must stay monotonically increasing 1..k+1");
                }
                // Old rows preserved verbatim (G56): same attempt ids and old epoch.
                for (int a = 1; a <= PRESET_ATTEMPTS; a++) {
                    TaskAssignment old = history.get(a - 1);
                    assertEquals("old-attempt-" + vertexId + "-" + s + "-" + a, old.getAttemptId(),
                            "old row " + a + " must be preserved verbatim (append-only)");
                    assertEquals(oldEpoch, old.getFencingEpoch(),
                            "old row " + a + " must keep the old leader's fencing epoch");
                }
                // New row: strictly continuing attempt number + the new leader's epoch.
                TaskAssignment latest = history.get(history.size() - 1);
                assertEquals(PRESET_ATTEMPTS + 1, latest.getAttemptNumber(),
                        "new leader's assignment must strictly continue the persisted history");
                assertEquals(newEpoch, latest.getFencingEpoch(),
                        "new assignment must carry the new leader's fencing epoch");
            }
        }
        // Wiring proof: the fan-out actually delivered the new attempts.
        assertEquals(3, rpc.assignments.size(), "all three subtasks must be dispatched to the TM rpc");
        coordinator.stop();
    }

    // ==================== Regression pinning ====================

    /**
     * Phase 1 regression pin: fresh job (registry has no rows) — the first activation
     * still assigns attempt numbers starting at 1 (byte-identical to the pre-fix
     * fresh-job behavior; seeding with no history is a no-op).
     */
    @Test
    void testFreshJobActivationStartsAttemptsAtOne() throws Exception {
        JdbcClusterRegistry registry = new JdbcClusterRegistry(jdbcTemplate);
        registry.registerNode(NODE_1, "localhost:9001", 4);

        TestLeaderElector elector = new TestLeaderElector("host-fresh");
        JobCoordinator coordinator = buildFreshCoordinator(registry, elector, new RecordingTaskRpcService());
        coordinator.start();
        elector.grantLeadership(1L);
        assertTrue(coordinator.isActive());

        for (String vertexId : new String[]{"source", "sink"}) {
            int parallelism = "source".equals(vertexId) ? 2 : 1;
            for (int s = 0; s < parallelism; s++) {
                List<TaskAssignment> history = registry.getAttemptHistory(JOB_ID, vertexId, s);
                assertEquals(1, history.size(), "fresh job must produce exactly one attempt row");
                assertEquals(1, history.get(0).getAttemptNumber(),
                        "fresh-job first assignment must start at attempt 1");
            }
        }
        coordinator.stop();
    }

    /**
     * Phase 1 regression pin: same-JVM re-activation (in-memory counters already
     * present from the first leadership) — the counter neither regresses to 1 nor
     * re-issues the same attempt number (which would duplicate-key); the history
     * continues 1 → 2.
     */
    @Test
    void testSameJvmReactivationContinuesWithoutRegressionOrDuplicate() throws Exception {
        JdbcClusterRegistry registry = new JdbcClusterRegistry(jdbcTemplate);
        registry.registerNode(NODE_1, "localhost:9001", 4);

        TestLeaderElector elector = new TestLeaderElector("host-samejvm");
        JobCoordinator coordinator = buildFreshCoordinator(registry, elector, new RecordingTaskRpcService());
        coordinator.start();

        elector.grantLeadership(1L);
        assertTrue(coordinator.isActive());

        // Leadership loss → standby; counters stay in memory (same JVM / same instance).
        elector.revokeLeadership();
        assertFalse(coordinator.isActive());

        // Re-grant on the SAME instance: seeding reads history max (=1), memory (=1),
        // max() is a no-op → the next assignment is attempt 2 (no regression, no
        // duplicate re-issue of attempt 1).
        elector.grantLeadership(2L);
        assertTrue(coordinator.isActive());

        for (String vertexId : new String[]{"source", "sink"}) {
            int parallelism = "source".equals(vertexId) ? 2 : 1;
            for (int s = 0; s < parallelism; s++) {
                List<TaskAssignment> history = registry.getAttemptHistory(JOB_ID, vertexId, s);
                assertEquals(2, history.size(), "two activations must leave exactly two rows");
                assertEquals(1, history.get(0).getAttemptNumber());
                assertEquals(2, history.get(1).getAttemptNumber(),
                        "re-activation must continue with attempt 2 (no regression, no duplicate)");
            }
        }
        coordinator.stop();
    }

    // ==================== InMemory parity ====================

    /**
     * Phase 1 InMemory parity: the same takeover scenario over a shared
     * {@link InMemoryClusterRegistry} — a fresh coordinator instance continues the
     * history monotonically. The registry's "Attempt number regression" caller-bug
     * WARN branch triggers only when attemptNumber <= lastAttempt; the strict
     * continuation asserted here proves that branch is never exercised on the
     * takeover path.
     */
    @Test
    void testInMemorySharedRegistryTakeoverContinuesMonotonically() throws Exception {
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        long oldEpoch = JobCoordinator.deriveHaFencingEpoch(1L, 0L);
        presetOldLeaderRows(registry, oldEpoch, PRESET_ATTEMPTS);

        TestLeaderElector elector = new TestLeaderElector("host-inmemory");
        JobCoordinator coordinator = buildFreshCoordinator(registry, elector, new RecordingTaskRpcService());
        coordinator.start();
        elector.grantLeadership(2L);
        assertTrue(coordinator.isActive(), "InMemory takeover must also complete activation");

        for (String vertexId : new String[]{"source", "sink"}) {
            int parallelism = "source".equals(vertexId) ? 2 : 1;
            for (int s = 0; s < parallelism; s++) {
                List<TaskAssignment> history = registry.getAttemptHistory(JOB_ID, vertexId, s);
                assertEquals(PRESET_ATTEMPTS + 1, history.size());
                for (int i = 0; i < history.size(); i++) {
                    assertEquals(i + 1, history.get(i).getAttemptNumber(),
                            "InMemory history must stay monotonically increasing 1..k+1 "
                                    + "(the regression-WARN branch is never exercised)");
                }
                assertEquals(JobCoordinator.deriveHaFencingEpoch(2L, 0L),
                        history.get(history.size() - 1).getFencingEpoch());
            }
        }
        coordinator.stop();
    }

    // ==================== No silent skip (guide #24) ====================

    /**
     * Phase 1 failure-semantics pin (adjudicated: de-active then rethrow): a registry
     * read failure during seeding must (1) throw a typed {@link StreamException} out
     * of the activation path and (2) leave the coordinator explicitly STANDBY
     * (active=false) — never a frozen half-active leader (active=true with no
     * assignments) after the platform elector swallows the listener exception.
     */
    @Test
    void testSeedReadFailureDeactivatesCoordinatorAndRethrows() throws Exception {
        FailingAttemptHistoryRegistry registry = new FailingAttemptHistoryRegistry(new InMemoryClusterRegistry());
        registry.registerNode(NODE_1, "localhost:9001", 4);

        TestLeaderElector elector = new TestLeaderElector("host-seedfail");
        JobCoordinator coordinator = buildFreshCoordinator(registry, elector, new RecordingTaskRpcService());
        coordinator.start();
        assertFalse(coordinator.isActive());

        StreamException ex = assertThrows(StreamException.class, () -> elector.grantLeadership(1L),
                "seeding read failure must fail loud, not silently continue");
        assertTrue(ex.toString().contains("seed attempt counters"),
                "exception must identify the seeding path: " + ex);

        assertFalse(coordinator.isActive(),
                "coordinator must be an explicit STANDBY after the seeding failure "
                        + "(no frozen half-active leader)");
        assertTrue(coordinator.getTaskAssignments().isEmpty(),
                "no assignments may be materialized when seeding failed");
        assertTrue(registry.assignTaskCalls == 0,
                "no assignment INSERT may be attempted when seeding failed");
        coordinator.stop();
    }

    // ==================== Helpers ====================

    /**
     * Writes the "old leader's" persisted rows: for every deployment-plan subtask,
     * attempts 1..k with distinct attempt ids and the old leader's fencing epoch.
     */
    private static void presetOldLeaderRows(ClusterRegistry registry, long oldEpoch, int k) {
        registry.registerNode(NODE_1, "localhost:9001", 4);
        for (String vertexId : new String[]{"source", "sink"}) {
            int parallelism = "source".equals(vertexId) ? 2 : 1;
            for (int s = 0; s < parallelism; s++) {
                for (int a = 1; a <= k; a++) {
                    registry.assignTask(JOB_ID, vertexId, s, NODE_1,
                            "old-attempt-" + vertexId + "-" + s + "-" + a, oldEpoch, a);
                }
            }
        }
    }

    private JobCoordinator buildFreshCoordinator(ClusterRegistry registry,
                                                 TestLeaderElector elector,
                                                 RecordingTaskRpcService rpc) throws Exception {
        // Fresh in-memory checkpoint state over an empty storage dir: the activation
        // restore path finds no durable checkpoint and starts fresh (G32 logging path).
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, baseConfig());

        Map<String, IStreamTaskRpcService> rpcs = new LinkedHashMap<>();
        rpcs.put(NODE_1, rpc);
        JobCoordinator coordinator = new JobCoordinator(
                JOB_ID, "coord-" + elector.getHostId(), buildDeploymentPlan(),
                registry, checkpointCoordinator, rpcs);
        coordinator.setLeaderElector(elector);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
        return coordinator;
    }

    private static CheckpointConfig baseConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    private static DeploymentPlan buildDeploymentPlan() {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 2, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, PIPELINE_ID, vertexPlans, edgePlans, null, null);
        return new DeploymentPlan(JOB_ID, PIPELINE_ID, partitionedPlan,
                "local", "memory", "local", null, null);
    }

    // ==================== Mocks ====================

    /** Records received assignments (wiring proof for the fan-out). */
    static class RecordingTaskRpcService implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
        }

        @Override
        public void deployTask(io.nop.stream.runtime.rpc.TaskDeploymentDescriptor descriptor, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }

    /**
     * Delegating registry whose {@code getAttemptHistory} always fails — pins the
     * adjudicated seeding-failure semantics (de-active + typed rethrow).
     */
    static class FailingAttemptHistoryRegistry implements ClusterRegistry {
        final ClusterRegistry delegate;
        int assignTaskCalls = 0;

        FailingAttemptHistoryRegistry(ClusterRegistry delegate) {
            this.delegate = delegate;
        }

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
            delegate.registerCoordinator(jobId, coordinatorId, fencingEpoch);
        }

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return delegate.getActiveCoordinator(jobId);
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            delegate.registerNode(nodeId, endpoint, capacity);
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            return delegate.renewLease(nodeId, leaseTimeoutMs);
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return delegate.getNodeLease(nodeId);
        }

        @Override
        public List<NodeInfo> getActiveNodes() {
            return delegate.getActiveNodes();
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch, int attemptNumber) {
            assignTaskCalls++;
            delegate.assignTask(jobId, vertexId, subtaskIndex, nodeId, attemptId, fencingEpoch, attemptNumber);
        }

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return delegate.getTaskAssignment(jobId, vertexId, subtaskIndex);
        }

        @Override
        public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            throw new IllegalStateException("simulated registry outage during takeover seeding");
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            delegate.removeTaskAssignment(jobId, vertexId, subtaskIndex);
        }
    }
}
