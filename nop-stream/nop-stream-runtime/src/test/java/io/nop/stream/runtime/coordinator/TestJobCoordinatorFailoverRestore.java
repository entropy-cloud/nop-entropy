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
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.JdbcCheckpointStorage;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 46 Phase 1 — G32 failover-safe rebuild focused tests.
 *
 * <p>Verifies that a newly-elected coordinator (fresh JVM, in-memory
 * {@code latestCompletedCheckpoint == null}) can deterministically rebuild the
 * completed-checkpoint view from durable {@link ICheckpointStorage} and resume
 * from the latest durable epoch + 1. This closes the load-bearing correctness
 * gap recorded in plan {@code 2026-08-03-0900-2-coordinator-ha-checkpoint-store.md}:
 * {@code activateAsLeader} previously only read the in-memory field (null on a
 * new JVM) and never called {@link CheckpointCoordinator#restoreFromCheckpoint()}.
 *
 * <p>Coverage (Phase 1 Exit Criteria):
 * <ul>
 *   <li>Behaviour: fresh CheckpointCoordinator over the same JDBC storage
 *       restores the latest durable epoch; the next trigger produces epoch+1.</li>
 *   <li>Idempotency: repeated restoreFromCheckpoint() does not corrupt state.</li>
 *   <li>No silent skip (guide #24): storage failure during leadership-activation
 *       rebuild fails loud (StreamException), not a warn-and-continue.</li>
 *   <li>HA wiring: activateAsLeader actually invokes the restore path when the
 *       in-memory view is empty (verified via observable state).</li>
 * </ul>
 */
class TestJobCoordinatorFailoverRestore {

    private static final String JOB_ID = "failover-restore-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "source", 0);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "sink", 0);

    private static HikariDataSource dataSource;

    @TempDir
    Path tempDir;

    private IJdbcTemplate jdbcTemplate;
    private JdbcCheckpointStorage jdbcStorage;

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
        for (String t : new String[]{"stream_checkpoint", "stream_epoch_manifest"}) {
            try {
                jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS " + t).end());
            } catch (Exception ignored) {
                // best-effort
            }
        }
        jdbcStorage = new JdbcCheckpointStorage(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        // JVM-shared H2 tables are reset per-test in setUp.
    }

    // ==================== Behaviour: fresh coordinator restores from JDBC storage ====================

    /**
     * Phase 1 Exit Criterion: "构造 fresh CheckpointCoordinator 指向同一 JDBC storage
     * → restoreFromCheckpoint() → 获得最近 durable epoch → 后续 trigger 从 epoch+1 继续".
     *
     * <p>Simulates a new coordinator JVM: the first coordinator completes
     * checkpoint epoch 5 into JDBC storage, then a FRESH CheckpointCoordinator
     * (new in-memory state, new ID counter) is built over the same storage.
     * Calling restoreFromCheckpoint() must yield epoch 5 and advance the counter
     * so the next trigger produces epoch 6.
     */
    @Test
    void testFreshCoordinatorRestoresLatestDurableEpochFromJdbcStorage() throws Exception {
        // 1. "Old leader" persists a durable checkpoint at epoch 5.
        CompletedCheckpoint durable = buildCompletedCheckpoint(JOB_ID, PIPELINE_ID, 5L);
        jdbcStorage.storeCheckPoint(durable);

        // 2. Fresh coordinator (simulates new JVM: in-memory view is null, counter at 0).
        CheckpointIDCounter freshCounter = new CheckpointIDCounter();
        CheckpointConfig cfg = baseConfig();
        CheckpointCoordinator freshCoord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID, freshCounter, jdbcStorage, cfg);

        // Precondition: fresh coordinator has no in-memory latest checkpoint.
        assertNull(freshCoord.getLatestCheckpoint(),
                "fresh coordinator must start with null latestCompletedCheckpoint");

        // 3. Restore from durable storage.
        CompletedCheckpoint restored = freshCoord.restoreFromCheckpoint();
        assertNotNull(restored, "restore must return the durable checkpoint");
        assertEquals(5L, restored.getCheckpointId(), "restore must return the latest durable epoch");
        assertTrue(restored.isRestored(), "restored checkpoint must be marked restored");
        assertEquals(5L, freshCoord.getLatestCheckpoint().getCheckpointId(),
                "in-memory latestCompletedCheckpoint must be populated by restore");

        // 4. Counter advanced so the next trigger produces epoch + 1 (6).
        assertEquals(6L, freshCounter.get(),
                "ID counter must advance past the restored epoch so the next trigger is epoch+1");
    }

    /**
     * Phase 1 Exit Criterion: restore + next-trigger integration. After restore,
     * triggering a checkpoint on the fresh coordinator must produce a pending
     * checkpoint whose id is strictly greater than the restored durable epoch.
     */
    @Test
    void testFreshCoordinatorTriggerProducesEpochAfterRestore() throws Exception {
        // Old leader durable epoch = 7.
        jdbcStorage.storeCheckPoint(buildCompletedCheckpoint(JOB_ID, PIPELINE_ID, 7L));

        CheckpointIDCounter freshCounter = new CheckpointIDCounter();
        CheckpointCoordinator freshCoord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, freshCounter, jdbcStorage, baseConfig());

        assertNull(freshCoord.getLatestCheckpoint());
        freshCoord.restoreFromCheckpoint();

        // Register the tasks so a trigger can proceed.
        freshCoord.setTasksToAcknowledge(java.util.Collections.singletonList(LOC_1));

        PendingCheckpoint pending = freshCoord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "trigger after restore must succeed");
        assertEquals(8L, pending.getCheckpointId(),
                "first trigger after restoring epoch 7 must produce epoch 8 (durable + 1)");
    }

    // ==================== Idempotency ====================

    /**
     * Phase 1 Exit Criterion: "重复 restoreFromCheckpoint() 调用不产生状态损坏（字段覆写幂等）".
     */
    @Test
    void testRestoreFromCheckpointIsIdempotent() throws Exception {
        jdbcStorage.storeCheckPoint(buildCompletedCheckpoint(JOB_ID, PIPELINE_ID, 9L));

        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, counter, jdbcStorage, baseConfig());

        // First restore.
        CompletedCheckpoint first = coord.restoreFromCheckpoint();
        assertNotNull(first);
        assertEquals(9L, first.getCheckpointId());
        long counterAfterFirst = counter.get();

        // Second restore: must not corrupt state, must be idempotent (counter stays put).
        CompletedCheckpoint second = coord.restoreFromCheckpoint();
        assertNotNull(second);
        assertEquals(9L, second.getCheckpointId());
        assertEquals(counterAfterFirst, counter.get(),
                "idempotent restore must not advance the counter twice (monotonic-only advance)");

        // Third restore still consistent.
        CompletedCheckpoint third = coord.restoreFromCheckpoint();
        assertEquals(9L, third.getCheckpointId());
        assertEquals(first.getCheckpointId(), coord.getLatestCheckpoint().getCheckpointId());
    }

    // ==================== No silent skip (guide #24) ====================

    /**
     * Phase 1 Exit Criterion: storage failure during leadership-activation rebuild
     * fails loud (StreamException), not a silent warn-and-continue. Uses a storage
     * that throws on {@code getLatestCheckpoint}.
     */
    @Test
    void testStorageFailureDuringActivateAsLeaderFailsLoud() {
        FailingCheckpointStorage failingStorage = new FailingCheckpointStorage();
        CheckpointCoordinator coord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), failingStorage, baseConfig());

        TestLeaderElector elector = new TestLeaderElector("host-failover");
        JobCoordinator coordinator = buildHaCoordinator(coord, elector, failingStorage);
        coordinator.start();
        assertFalse(coordinator.isActive());

        // grantLeadership triggers activateAsLeader -> rotateFencingEpochAndRestore(true)
        // -> in-memory null -> restoreFromCheckpoint() -> storage throws -> fail loud.
        StreamException ex = assertThrows(StreamException.class,
                () -> elector.grantLeadership(1L),
                "storage failure during failover rebuild must throw, not silently continue");

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("rebuild") || ex.getMessage().contains("restore")
                        || ex.toString().contains("rebuild") || ex.toString().contains("restore"),
                "exception must identify the rebuild path: " + ex);

        // The coordinator never silently entered an inconsistent ACTIVE-with-no-view state:
        // either it stayed non-active or the throw prevented the assignment. The exception
        // itself is the observable fail-loud evidence (guide #24).
        coordinator.stop();
    }

    // ==================== HA wiring: activateAsLeader invokes restore ====================

    /**
     * Phase 1 Exit Criterion (wiring): when a fresh HA coordinator is granted
     * leadership, {@code activateAsLeader} rebuilds the in-memory
     * latestCompletedCheckpoint from storage (it was null before activation).
     *
     * <p>This is the single-process observable proof of the G32 fix; the multi-JVM
     * end-to-end proof is in Phase 4 ({@code TestMultiJvmCoordinatorFailover}).
     */
    @Test
    void testActivateAsLeaderRebuildsFromStorageWhenInMemoryIsNull() throws Exception {
        // Pre-seed durable storage with a completed checkpoint at epoch 3.
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        storage.storeCheckPoint(buildCompletedCheckpoint(JOB_ID, PIPELINE_ID, 3L));

        CheckpointCoordinator coord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, baseConfig());
        assertNull(coord.getLatestCheckpoint(), "precondition: in-memory view empty before activation");

        TestLeaderElector elector = new TestLeaderElector("host-rebuild");
        JobCoordinator coordinator = buildHaCoordinator(coord, elector, storage);
        coordinator.start();
        assertFalse(coordinator.isActive());

        // Grant leadership -> activateAsLeader -> rotateFencingEpochAndRestore(true)
        // -> in-memory null -> restoreFromCheckpoint() loads epoch 3.
        elector.grantLeadership(2L);

        assertTrue(coordinator.isActive(), "coordinator must be active after grant");
        assertNotNull(coord.getLatestCheckpoint(),
                "activateAsLeader must rebuild latestCompletedCheckpoint from storage");
        assertEquals(3L, coord.getLatestCheckpoint().getCheckpointId(),
                "rebuilt view must be the latest durable epoch");

        // Fencing epoch derived from the granted leadership epoch (Stage 39 encoding).
        assertEquals(JobCoordinator.deriveHaFencingEpoch(2L, 0L), coordinator.getFencingEpoch());

        // The next trigger would produce epoch 4 (counter advanced by restore).
        coord.setTasksToAcknowledge(java.util.Collections.singletonList(LOC_1));
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertEquals(4L, pending.getCheckpointId(), "next trigger after restore(3) must be epoch 4");

        coordinator.stop();
    }

    /**
     * Same-leader global recovery must NOT re-query storage: the in-memory view
     * survives within the same JVM. This guards against a redundant DB round-trip
     * per recovery (plan Phase 1 item 2).
     */
    @Test
    void testSameLeaderGlobalRecoveryDoesNotRequeryStorage() throws Exception {
        CountingStorage storage = new CountingStorage(new LocalFileCheckpointStorage(tempDir.toString()));
        storage.delegate.storeCheckPoint(buildCompletedCheckpoint(JOB_ID, PIPELINE_ID, 11L));

        CheckpointCoordinator coord = new CheckpointCoordinator(
                JOB_ID, PIPELINE_ID, new CheckpointIDCounter(), storage, baseConfig());

        TestLeaderElector elector = new TestLeaderElector("host-sameleader");
        JobCoordinator coordinator = buildHaCoordinator(coord, elector, storage);
        coordinator.start();
        elector.grantLeadership(1L); // activateAsLeader -> restore (1 storage read so far)
        long readsAfterActivation = storage.getLatestCheckpointReads;
        assertTrue(readsAfterActivation >= 1, "activation must have read storage at least once");

        // Trigger a same-leader global recovery. restoreFromStorage=false, so the
        // coordinator must NOT issue another getLatestCheckpoint query.
        coordinator.globalRecovery();
        assertEquals(readsAfterActivation, storage.getLatestCheckpointReads,
                "same-leader globalRecovery must not re-query storage (in-memory view survives)");

        // In-memory view intact after recovery.
        assertEquals(11L, coord.getLatestCheckpoint().getCheckpointId());

        coordinator.stop();
    }

    // ==================== Helpers ====================

    private CheckpointConfig baseConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    private CompletedCheckpoint buildCompletedCheckpoint(String jobId, String pipelineId, long checkpointId) {
        return CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .addTaskState(LOC_2, TaskStateSnapshot.empty(LOC_2))
                .build();
    }

    private DeploymentPlan buildDeploymentPlan() {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, PIPELINE_ID, vertexPlans, edgePlans, null, null);
        return new DeploymentPlan(JOB_ID, PIPELINE_ID, partitionedPlan,
                "local", "memory", "local", null, null);
    }

    private JobCoordinator buildHaCoordinator(CheckpointCoordinator coord, TestLeaderElector elector,
                                              ICheckpointStorage storage) {
        MockClusterRegistry registry = new MockClusterRegistry();
        registry.registerNode("node-1", "localhost:9001", 4);
        MockTaskRpcService rpc = new MockTaskRpcService();
        Map<String, IStreamTaskRpcService> rpcs = new java.util.HashMap<>();
        rpcs.put("node-1", rpc);
        JobCoordinator c = new JobCoordinator(
                JOB_ID, "coord-" + elector.getHostId(), buildDeploymentPlan(),
                registry, coord, rpcs);
        c.setLeaderElector(elector);
        c.setTerminationCheckpointTimeoutMs(500L);
        return c;
    }

    // ==================== Mocks ====================

    static class MockClusterRegistry implements ClusterRegistry {
        final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();

        @Override
        public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
        }

        @Override
        public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) {
            return null;
        }

        @Override
        public void registerNode(String nodeId, String endpoint, int capacity) {
            nodes.put(nodeId, new NodeInfo(nodeId, endpoint, capacity,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }

        @Override
        public boolean renewLease(String nodeId, long leaseTimeoutMs) {
            return true;
        }

        @Override
        public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) {
            return null;
        }

        @Override
        public List<NodeInfo> getActiveNodes() {
            return new ArrayList<>(nodes.values());
        }

        @Override
        public void assignTask(String jobId, String vertexId, int subtaskIndex,
                               String nodeId, String attemptId, long fencingEpoch, int attemptNumber) {
        }

        @Override
        public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
            return null;
        }

        @Override
        public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return new ArrayList<>();
        }

        @Override
        public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        }
    }

    static class MockTaskRpcService implements IStreamTaskRpcService {
        final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();
        final AtomicReference<io.nop.stream.core.checkpoint.CheckpointBarrier> lastBarrier = new AtomicReference<>();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public void triggerCheckpoint(io.nop.stream.core.checkpoint.CheckpointBarrier barrier, long fencingEpoch) {
            lastBarrier.set(barrier);
        }

        @Override
        public void deployTask(io.nop.stream.runtime.rpc.TaskDeploymentDescriptor descriptor, long fencingEpoch) {
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
        }
    }

    /**
     * Storage that always throws on getLatestCheckpoint, to verify the fail-loud
     * contract (guide #24 — no silent swallow during failover rebuild).
     */
    static class FailingCheckpointStorage implements ICheckpointStorage {
        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) {
            return String.valueOf(checkpoint.getCheckpointId());
        }

        @Override
        public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) {
            throw new CheckpointStorageException("simulated storage outage during failover rebuild", null);
        }

        @Override
        public List<CompletedCheckpoint> getAllCheckpoints(String jobId) {
            return new ArrayList<>();
        }

        @Override
        public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) {
            return new ArrayList<>();
        }

        @Override
        public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {
        }

        @Override
        public void deleteAllCheckpoints(String jobId) {
        }

        @Override
        public String getName() {
            return "FailingCheckpointStorage";
        }

        @Override
        public int getCheckpointCount(String jobId) {
            return 0;
        }

        @Override
        public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) {
            return targetPath;
        }

        @Override
        public CompletedCheckpoint loadSavepoint(String savepointPath) {
            return null;
        }

        @Override
        public io.nop.stream.core.checkpoint.SavepointMetadata loadSavepointMetadata(String savepointPath) {
            return null;
        }

        @Override
        public void storeEpochManifest(String jobId, String pipelineId,
                                       io.nop.stream.core.checkpoint.EpochManifest manifest) {
        }

        @Override
        public io.nop.stream.core.checkpoint.EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) {
            return null;
        }
    }

    /**
     * Delegating storage that counts getLatestCheckpoint invocations, to verify
     * that same-leader globalRecovery does not re-query storage.
     */
    static class CountingStorage implements ICheckpointStorage {
        final ICheckpointStorage delegate;
        long getLatestCheckpointReads = 0L;

        CountingStorage(ICheckpointStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
            return delegate.storeCheckPoint(checkpoint);
        }

        @Override
        public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException {
            getLatestCheckpointReads++;
            return delegate.getLatestCheckpoint(jobId, pipelineId);
        }

        @Override
        public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException {
            return delegate.getAllCheckpoints(jobId);
        }

        @Override
        public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException {
            return delegate.getLatestCheckpoints(jobId, count);
        }

        @Override
        public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
            delegate.deleteCheckpoint(jobId, pipelineId, checkpointId);
        }

        @Override
        public void deleteAllCheckpoints(String jobId) throws CheckpointStorageException {
            delegate.deleteAllCheckpoints(jobId);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public int getCheckpointCount(String jobId) throws CheckpointStorageException {
            return delegate.getCheckpointCount(jobId);
        }

        @Override
        public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException {
            return delegate.storeSavepoint(checkpoint, targetPath);
        }

        @Override
        public CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException {
            return delegate.loadSavepoint(savepointPath);
        }

        @Override
        public io.nop.stream.core.checkpoint.SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException {
            return delegate.loadSavepointMetadata(savepointPath);
        }

        @Override
        public void storeEpochManifest(String jobId, String pipelineId,
                                       io.nop.stream.core.checkpoint.EpochManifest manifest) throws CheckpointStorageException {
            delegate.storeEpochManifest(jobId, pipelineId, manifest);
        }

        @Override
        public io.nop.stream.core.checkpoint.EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException {
            return delegate.loadLatestEpochManifest(jobId, pipelineId);
        }
    }
}
