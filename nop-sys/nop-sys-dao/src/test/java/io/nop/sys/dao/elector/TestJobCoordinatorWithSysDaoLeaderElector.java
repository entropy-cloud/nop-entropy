/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.elector;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.sys.dao.entity.NopSysClusterLeader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 38 Phase 3 — {@code SysDaoLeaderElector} as the first production user
 * of the platform leader-elector contract wired into
 * {@link JobCoordinator}. Real JDBC integration smoke check (H2 + AutoTest):
 *
 * <ul>
 *   <li>Proves the deploy-time wiring path {@code SysDaoLeaderElector bean ->
 *       JobCoordinator.setLeaderElector()} actually drives the HA lifecycle
 *       (start -> STANDBY -> grant via real lease acquisition -> ACTIVE with
 *       composite fencing token derived from the granted {@link LeaderEpoch}).</li>
 *   <li>Closes the M3 hollow risk: a test-only elector double could pass the
 *       Phase 1/2 contract tests while the real {@code SysDaoLeaderElector}
 *       JDBC callbacks would silently fail to integrate. This test runs the
 *       real production elector with a real H2-backed lease table.</li>
 *   <li>Records the platform integration findings (lease cadence, callback
 *       thread model, epoch monotonicity) — see the {@code ai-dev/logs/}
 *       Phase 3 entry that references this test.</li>
 * </ul>
 *
 * <p>Lives in {@code nop-sys-dao} (not {@code nop-stream-runtime}) because the
 * JDBC elector + ORM entity registration + AutoTest JDBC harness already live
 * here, and the wiring direction at deploy time is sys-dao bean ->
 * stream-runtime coordinator. The {@code nop-stream-runtime} dependency is
 * test-scope only.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobCoordinatorWithSysDaoLeaderElector extends JunitBaseTestCase {

    private static final String JOB_ID = "sys-dao-ha-job";
    private static final String COORDINATOR_ID = "sys-dao-coordinator-1";
    private static final String CLUSTER_ID = "stream-job-" + JOB_ID;
    private static final String HOST_ID = "stream-host-1";

    @TempDir
    Path tempDir;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private DefaultScheduledExecutor executor;
    private SysDaoLeaderElector leaderElector;
    private JobCoordinator coordinator;
    private CapturingTaskRpcService rpcService;

    @BeforeEach
    public void setUp() {
        // Real production elector — same bean configuration a deploy-time
        // beans.xml would assemble. Short lease / poll interval to keep the
        // smoke check fast.
        executor = DefaultScheduledExecutor.newSingleThreadTimer("stage38-smoke");
        leaderElector = new SysDaoLeaderElector();
        leaderElector.setDaoProvider(daoProvider);
        leaderElector.setOrmTemplate(ormTemplate);
        leaderElector.setScheduledExecutor(executor);
        leaderElector.setHostId(HOST_ID);
        leaderElector.setCheckIntervalMs(100);
        leaderElector.setAddr("localhost");
        leaderElector.setPort(8011);
        leaderElector.setClusterId(CLUSTER_ID);
        leaderElector.setLeaseSafeGap(100);
        leaderElector.setLeaseMs(2000);
        // Note: leaderElector.start() is NOT called here — each test starts
        // the elector at a deterministic point relative to coordinator.start(),
        // so the listener-vs-self-activation path under test is unambiguous.

        // nop-stream coordinator pieces — kept minimal (in-memory registry +
        // local-file checkpoint storage + capturing task RPC) so the smoke
        // check focuses on the elector integration, not on stream execution.
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator =
                new CheckpointCoordinator(JOB_ID, "pipeline-0", idCounter, storage, config);

        ClusterRegistry clusterRegistry = new InMemoryClusterRegistry();
        clusterRegistry.registerNode("node-1", "localhost:9001", 4);

        rpcService = new CapturingTaskRpcService();
        Map<String, IStreamTaskRpcService> taskRpcServices = new java.util.HashMap<>();
        taskRpcServices.put("node-1", rpcService);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink",
                io.nop.stream.core.execution.plan.PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        coordinator = new JobCoordinator(
                JOB_ID, COORDINATOR_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcServices);
        coordinator.setLeaderElector(leaderElector);
        coordinator.setTerminationCheckpointTimeoutMs(500L);
    }

    @AfterEach
    public void tearDown() {
        if (coordinator != null) {
            coordinator.stop();
        }
        if (leaderElector != null) {
            leaderElector.stop();
        }
        if (executor != null) {
            executor.destroy();
        }
    }

    /**
     * Listener path: coordinator registers its listener BEFORE the JDBC
     * elector grants leadership. The real JDBC grant fires
     * {@code becomeLeader(epoch)} into the coordinator, which transitions
     * ACTIVE and derives a composite fencing token from the granted epoch
     * (NOT a random UUID).
     */
    @Test
    public void testRealJdbcGrantActivatesCoordinatorWithLeaderEpochToken() {
        // Coordinator registers its listener FIRST, in STANDBY.
        coordinator.start();
        assertFalse(coordinator.isActive(),
                "coordinator must start in STANDBY before the elector grants leadership");

        // Now start the real JDBC elector and wait for it to elect this host.
        leaderElector.start();
        LeaderEpoch granted = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertNotNull(granted, "real JDBC elector must produce a LeaderEpoch");
        assertEquals(HOST_ID, granted.getLeaderId(),
                "single-node elector must elect itself");
        assertTrue(granted.getEpoch() >= 1,
                "epoch must be monotonically >= 1");

        // Give the becomeLeader callback (fired by the elector on the
        // registered listener) a moment to land.
        long deadline = System.currentTimeMillis() + 2000L;
        while (!coordinator.isActive() && System.currentTimeMillis() < deadline) {
            ThreadHelper.sleep(50);
        }

        assertTrue(coordinator.isActive(),
                "real JDBC elector must drive the coordinator to ACTIVE via becomeLeader");
        assertTrue(coordinator.isFailureDetectorAlive(),
                "ACTIVE coordinator must keep its failure detector alive");

        // Stage 39: the coordinator now carries a single monotonic long fencing epoch
        // (leaderEpochValue * EPOCH_SCALE + recoveryGen), derived from the granted
        // LeaderEpoch. NOT a random UUID, and no longer a composite String.
        long epoch = coordinator.getFencingEpoch();
        assertTrue(epoch > 0L, "HA fencing epoch must be initialized after activation");
        assertEquals(JobCoordinator.deriveHaFencingEpoch(granted.getEpoch(), 0L), epoch,
                "HA fencing epoch must be derived from the granted LeaderEpoch");

        // The granted leadership epoch matches what the elector reports.
        LeaderEpoch coordLeadership = coordinator.getCurrentLeadership();
        assertNotNull(coordLeadership);
        assertEquals(granted.getLeaderId(), coordLeadership.getLeaderId());
        assertEquals(granted.getEpoch(), coordLeadership.getEpoch());

        // Control plane bootstrapped on activation (assignments issued with the
        // leadership-derived fencing epoch).
        assertFalse(rpcService.assignments.isEmpty(),
                "ACTIVE coordinator must bootstrap by issuing task assignments");
        assertEquals(epoch, rpcService.assignments.get(0).getFencingEpoch(),
                "issued assignments must carry the leadership-derived fencing epoch");

        // The lease row in the JDBC-backed table reflects the leader.
        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);
        NopSysClusterLeader row = dao.getEntityById(CLUSTER_ID);
        assertNotNull(row, "lease row must exist after election");
        assertEquals(HOST_ID, row.getLeaderId());
        assertEquals(granted.getEpoch(), row.getLeaderEpoch());
    }

    /**
     * Stage 38 Phase 3 — platform contract reconciliation: when the JDBC
     * elector has already granted leadership to this host BEFORE the
     * coordinator registered its listener (the common case — elector bean
     * starts before the coordinator), the coordinator self-activates on
     * {@code start()} by reading the current elector state. This closes the
     * platform quirk where {@code SysDaoLeaderElector} does NOT replay the
     * current leadership to newly-registered listeners.
     */
    @Test
    public void testCoordinatorSelfActivatesWhenElectorAlreadyLeader() {
        // Wait for the real JDBC elector to elect this host.
        leaderElector.start();
        LeaderEpoch granted = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertEquals(HOST_ID, granted.getLeaderId());
        assertTrue(leaderElector.isLeader(),
                "precondition: elector must report self as leader before coordinator starts");

        // Now start the coordinator AFTER the election has completed.
        coordinator.start();

        // Self-activation: the coordinator queried isLeader() on start and
        // synchronously transitioned ACTIVE with the existing LeaderEpoch.
        assertTrue(coordinator.isActive(),
                "coordinator must self-activate when the elector already reports self as leader");
        assertTrue(coordinator.isFailureDetectorAlive());

        LeaderEpoch coordLeadership = coordinator.getCurrentLeadership();
        assertNotNull(coordLeadership);
        assertEquals(granted.getLeaderId(), coordLeadership.getLeaderId());
        assertEquals(granted.getEpoch(), coordLeadership.getEpoch());

        // Stage 39: single monotonic long fencing epoch derived from the existing
        // leadership epoch (recoveryGen 0 on first activation).
        long epoch = coordinator.getFencingEpoch();
        assertTrue(epoch > 0L, "HA fencing epoch must be initialized after self-activation");
        assertEquals(JobCoordinator.deriveHaFencingEpoch(granted.getEpoch(), 0L), epoch,
                "HA fencing epoch must be derived from the granted LeaderEpoch");
        assertFalse(rpcService.assignments.isEmpty(),
                "self-activated coordinator must bootstrap task assignments");
    }

    /**
     * Capture the integration findings observed via the real JDBC elector.
     * Each assertion documents a platform contract that nop-stream now
     * depends on; changes to SysDaoLeaderElector semantics must keep these
     * true.
     */
    @Test
    public void testPlatformIntegrationContractObserved() {
        leaderElector.start();
        LeaderEpoch granted = FutureHelper.syncGet(leaderElector.whenElectionCompleted());

        // Finding F1: epoch is monotonically >= 1 and increments on
        // restartElection (the production failover path).
        long initialEpoch = granted.getEpoch();
        leaderElector.restartElection();
        ThreadHelper.sleep(800);
        LeaderEpoch afterRestart = FutureHelper.syncGet(leaderElector.whenElectionCompleted());
        assertTrue(afterRestart.getEpoch() > initialEpoch,
                "restartElection must produce a strictly greater epoch (got " + initialEpoch
                        + " -> " + afterRestart.getEpoch() + ")");

        // Finding F2: the lease row's expireAt is in the future relative to
        // the grant (leaseMs window). Verified on the row snapshot after the
        // coordinator has been activated.
        coordinator.start();
        ThreadHelper.sleep(500);
        IEntityDao<NopSysClusterLeader> dao = daoProvider.daoFor(NopSysClusterLeader.class);
        NopSysClusterLeader row = dao.getEntityById(CLUSTER_ID);
        assertNotNull(row);
        assertTrue(row.getExpireAt().getTime() > row.getRefreshTime().getTime(),
                "lease expireAt must be later than refreshTime (leaseMs window)");
    }

    // ==================== Mocks ====================

    static final class CapturingTaskRpcService implements IStreamTaskRpcService {
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
