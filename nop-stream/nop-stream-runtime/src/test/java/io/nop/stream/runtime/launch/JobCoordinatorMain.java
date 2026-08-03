/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcLeaderElector;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;
import io.nop.stream.runtime.rpc.StreamControlRpcServer;
import io.nop.stream.runtime.rpc.StreamControlRpcTopics;

/**
 * Stage 42 Phase 1: standalone JVM entry point for a {@link JobCoordinator}.
 *
 * <p>Launched by the {@code MiniStreamCluster} test harness (Phase 2) as:
 * <pre>
 *   java -cp &lt;test-classpath&gt; io.nop.stream.runtime.launch.JobCoordinatorMain
 *        jobId=job-1
 *        jdbcUrl=jdbc:h2:file:/tmp/cluster.db;AUTO_SERVER=TRUE;MODE=MySQL
 *        topicNamespace=run-2026-08-03-001
 *        checkpointBaseDir=/tmp/nop-stream-checkpoints
 *        expectedNodeIds=tm-0,tm-1
 * </pre>
 *
 * <p>The process connects to the shared H2 DB, waits for the expected
 * TaskManagers to register, builds RPC proxies to each, exposes its
 * {@link io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService} over the
 * control-plane RPC, starts in remote-deploy mode (Phase 0), and assigns the
 * trivial source→sink job via {@code deployTask} RPC. SIGTERM/SIGINT triggers
 * graceful shutdown and exits with code 0. Missing required config fails fast
 * with stderr message and non-zero exit code (plan guide #24).
 *
 * <p><strong>Trivial job contract</strong>: for Phase 1 the coordinator builds a
 * placeholder {@link JobGraph} + {@link DeploymentPlan} with the configured
 * node set, so the assignment / deploy RPC path is genuinely exercised even
 * before Phase 2 wires a real pipeline. Phase 3 wires a real source→keyBy→sink
 * pipeline.
 */
public final class JobCoordinatorMain {

    private static final Logger LOG = LoggerFactory.getLogger(JobCoordinatorMain.class);

    private final ClusterLaunchConfig config;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private SharedJdbcInfrastructure jdbc;
    private PollingJdbcMessageService messageService;
    private JdbcClusterRegistry clusterRegistry;
    private LocalFileCheckpointStorage checkpointStorage;
    private CheckpointCoordinator checkpointCoordinator;
    private JobCoordinator coordinator;
    private io.nop.stream.runtime.cluster.JdbcLeaderElector leaderElector;
    private StreamControlRpcServer coordinatorServer;
    private final Map<String, StreamControlRpcProxyFactory> taskProxies = new LinkedHashMap<>();

    public JobCoordinatorMain(ClusterLaunchConfig config) {
        this.config = config;
    }

    public JobCoordinator start() throws InterruptedException {
        // Fail-fast on missing required config (plan guide #24).
        String jobId = config.require(ClusterLaunchConfig.KEY_JOB_ID);
        String jdbcUrl = config.require(ClusterLaunchConfig.KEY_JDBC_URL);
        String topicNamespace = config.require(ClusterLaunchConfig.KEY_TOPIC_NAMESPACE);
        String checkpointBaseDir = config.require(ClusterLaunchConfig.KEY_CHECKPOINT_BASE_DIR);
        String expectedNodeIdsRaw = config.get("expectedNodeIds", "");
        LOG.info("JobCoordinatorMain starting: jobId={} jdbcUrl={} topicNamespace={}",
                jobId, jdbcUrl, topicNamespace);

        long fencingEpoch = config.getLong(ClusterLaunchConfig.KEY_FENCING_EPOCH,
                JobCoordinator.deriveHaFencingEpoch(0L, 1L));
        long pollIntervalMs = config.getLong(ClusterLaunchConfig.KEY_POLL_INTERVAL_MS, 50L);
        long nodeRegistrationTimeoutMs = config.getLong("nodeRegistrationTimeoutMs", 30_000L);

        // Stage 46: HA mode. When enabled, the coordinator runs in leader-gated mode
        // (STANDBY until granted leadership via the shared JDBC lease table). When
        // disabled (default), the legacy single-instance behaviour is preserved so
        // existing Stage 42 tests are unaffected.
        boolean haEnabled = config.getBoolean(ClusterLaunchConfig.KEY_LEADER_ELECTOR_ENABLED, false);

        List<String> expectedNodeIds = Arrays.stream(expectedNodeIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        jdbc = new SharedJdbcInfrastructure(config);
        messageService = new PollingJdbcMessageService(jdbc.getJdbcTemplate(), pollIntervalMs);
        messageService.initialize();
        clusterRegistry = new JdbcClusterRegistry(jdbc.getJdbcTemplate());
        checkpointStorage = new LocalFileCheckpointStorage(checkpointBaseDir);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig checkpointConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(
                jobId, "pipeline-0", idCounter, checkpointStorage, checkpointConfig);

        // Wait for the expected TaskManagers to register.
        if (!expectedNodeIds.isEmpty()) {
            waitForNodeRegistration(expectedNodeIds, nodeRegistrationTimeoutMs);
        }

        // Build per-node RPC proxies (one per expected TaskManager).
        Map<String, IStreamTaskRpcService> taskRpcProxies = new LinkedHashMap<>();
        for (String nodeId : expectedNodeIds) {
            StreamControlRpcProxyFactory proxy = new StreamControlRpcProxyFactory(
                    "streamTaskRpc@" + nodeId,
                    IStreamTaskRpcService.class,
                    messageService,
                    TaskManagerMain.taskRpcTopic(topicNamespace, nodeId));
            proxy.start();
            taskProxies.put(nodeId, proxy);
            taskRpcProxies.put(nodeId, proxy.getProxy());
        }

        // Build a trivial placeholder JobGraph + DeploymentPlan. Phase 3 wires
        // a real source→keyBy→sink pipeline; for Phase 1 verification we only
        // need the assignment/deploy RPC path to fire.
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                jobId, "pipeline-0", vertexPlans, edgePlans, null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                jobId, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);
        JobGraph jobGraph = new JobGraph(jobId);

        coordinator = new JobCoordinator(
                jobId, "coordinator-" + jobId, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcProxies);

        // Stage 46: HA wiring. In HA mode the coordinator gets a JdbcLeaderElector
        // (shared JDBC lease table) and does NOT hardcode a fencing epoch or call
        // assignTasks() directly — activation + assignment happen only on the
        // becomeLeader callback (activateAsLeader -> rotateFencingEpochAndRestore
        // -> assignTasks). In non-HA mode the legacy path is preserved.
        JdbcLeaderElector leaderElector = null;
        if (haEnabled) {
            String leaderClusterId = config.get(ClusterLaunchConfig.KEY_LEADER_CLUSTER_ID, jobId);
            String leaderHostId = config.get(ClusterLaunchConfig.KEY_LEADER_HOST_ID, "coordinator-" + jobId);
            int leaderLeaseMs = config.getInt(ClusterLaunchConfig.KEY_LEADER_LEASE_MS, 5000);
            int leaderCheckIntervalMs = config.getInt(ClusterLaunchConfig.KEY_LEADER_CHECK_INTERVAL_MS, 500);

            leaderElector = new JdbcLeaderElector(jdbc.getJdbcTemplate());
            leaderElector.setClusterId(leaderClusterId);
            leaderElector.setHostId(leaderHostId);
            leaderElector.setLeaseMs(leaderLeaseMs);
            leaderElector.setCheckIntervalMs(leaderCheckIntervalMs);
            leaderElector.setLeaseSafeGap(Math.min(1000, leaderLeaseMs / 4));
            leaderElector.setAddr("localhost");
            leaderElector.setPort(0);
            coordinator.setLeaderElector(leaderElector);
            this.leaderElector = leaderElector;
            LOG.info("JobCoordinatorMain HA mode enabled (clusterId={}, hostId={}, leaseMs={}, checkIntervalMs={})",
                    leaderClusterId, leaderHostId, leaderLeaseMs, leaderCheckIntervalMs);
        } else {
            coordinator.setFencingEpoch(fencingEpoch);
        }

        // Stage 42 Phase 0: remote-deploy mode — assignTasks() builds
        // TaskDeploymentDescriptors and calls deployTask RPC (each TaskManager
        // rebuilds its own invokable locally).
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(jobGraph);
        coordinator.setCheckpointStoragePath(checkpointBaseDir);

        // Expose IStreamCoordinatorRpcService over the control-plane RPC.
        coordinatorServer = new StreamControlRpcServer(
                "streamCoordinatorRpc@" + topicNamespace,
                io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService.class,
                coordinator,
                messageService,
                StreamControlRpcTopics.coordinatorTopic(topicNamespace));
        coordinatorServer.start();

        coordinator.start();
        // Stage 46: in HA mode, assignTasks() must NOT be called directly here —
        // it is driven by activateAsLeader on the becomeLeader callback. In non-HA
        // mode the coordinator is already active, so assignTasks runs directly.
        if (!haEnabled) {
            coordinator.assignTasks();
        } else if (leaderElector != null) {
            // Start the elector AFTER coordinator.start() registered its listener,
            // so the becomeLeader callback is not missed.
            leaderElector.start();
        }

        LOG.info("JobCoordinatorMain started (jobId={}, rpcTopic={}, ha={}, deployed subtasks via remote-deploy)",
                jobId, StreamControlRpcTopics.coordinatorTopic(topicNamespace), haEnabled);
        return coordinator;
    }

    private void waitForNodeRegistration(List<String> expectedNodeIds, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            List<NodeInfo> active = clusterRegistry.getActiveNodes();
            Set<String> activeIds = new java.util.HashSet<>();
            for (NodeInfo n : active) {
                activeIds.add(n.getNodeId());
            }
            boolean allPresent = activeIds.containsAll(expectedNodeIds);
            if (allPresent) {
                LOG.info("All expected TaskManagers registered: {}", expectedNodeIds);
                return;
            }
            Set<String> missing = new java.util.LinkedHashSet<>(expectedNodeIds);
            missing.removeAll(activeIds);
            LOG.info("Waiting for TaskManagers to register; missing={} (active={})",
                    missing, activeIds);
            Thread.sleep(Math.min(500L, Math.max(50L, timeoutMs / 20)));
        }
        throw new IllegalStateException(
                "Timed out (" + timeoutMs + "ms) waiting for TaskManagers to register. Expected: "
                        + expectedNodeIds);
    }

    /**
     * Blocks the calling thread until shutdown is triggered via {@link #shutdown()}
     * or the JVM SIGTERM hook.
     */
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public synchronized void shutdown() {
        LOG.info("JobCoordinatorMain shutting down");
        try {
            if (leaderElector != null) {
                leaderElector.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop leader elector", e);
        }
        try {
            if (coordinator != null) {
                coordinator.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop coordinator", e);
        }
        try {
            if (coordinatorServer != null) {
                coordinatorServer.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop coordinator RPC server", e);
        }
        for (StreamControlRpcProxyFactory proxy : taskProxies.values()) {
            try {
                proxy.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop task RPC proxy", e);
            }
        }
        taskProxies.clear();
        try {
            if (messageService != null) {
                messageService.close();
            }
        } catch (Exception e) {
            LOG.warn("Failed to close message service", e);
        }
        try {
            if (jdbc != null) {
                jdbc.close();
            }
        } catch (Exception e) {
            LOG.warn("Failed to close JDBC infrastructure", e);
        }
        shutdownLatch.countDown();
    }

    public JobCoordinator getCoordinator() {
        return coordinator;
    }

    public IMessageService getMessageService() {
        return messageService;
    }

    public ClusterRegistry getClusterRegistry() {
        return clusterRegistry;
    }

    // ==================== main entry point ====================

    public static void main(String[] args) {
        // Initialize nop-dao (registers the H2 dialect manager) so the spawned
        // child process can resolve a dialect for its H2 connection.
        io.nop.core.initialize.CoreInitialization.initialize();

        ClusterLaunchConfig config;
        try {
            config = ClusterLaunchConfig.parse(args);
        } catch (IllegalArgumentException e) {
            LOG.error("JobCoordinatorMain config error", e);
            System.err.println(usage());
            System.exit(2);
            return;
        }

        JobCoordinatorMain main = new JobCoordinatorMain(config);
        Thread shutdownHook = new Thread(() -> {
            try {
                main.shutdown();
            } catch (Exception ignored) {
                // Best-effort.
            }
        }, "jc-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            main.start();
            main.awaitShutdown();
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("JobCoordinatorMain fatal error: " + t);
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static String usage() {
        return "Usage: JobCoordinatorMain jobId=<id> jdbcUrl=<h2-url>"
                + " topicNamespace=<ns> checkpointBaseDir=<path>"
                + " [expectedNodeIds=tm-0,tm-1] [fencingEpoch=<n>] [pollIntervalMs=<n>]";
    }
}
