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
import java.util.Collections;
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
import io.nop.stream.core.common.functions.sink.PrintSinkFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
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
import io.nop.stream.runtime.source.CollectionReplayableSource;

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
 * <p><strong>Trivial job contract</strong>: by default the coordinator builds a
 * placeholder {@link JobGraph} + {@link DeploymentPlan} with the configured
 * node set (empty {@link CollectionReplayableSource} + discarding sink), so the
 * assignment / deploy RPC path is exercised without scenario data flow. Item 14
 * (composite-scenario distributed) added the {@code pipelineFactoryClass} seam:
 * a {@link ClusterPipelineFactory} supplies a REAL pipeline (scenario JobGraph
 * from the XDSL declaration + checkpoint tuning), the launch path drives
 * periodic checkpoints ({@code JobCoordinator.startPeriodicCheckpoints}) and
 * forwards durable-checkpoint commit notifications to the remote sinks
 * ({@code registerDistributedCommitForwarder}).
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
    private io.nop.stream.runtime.ops.StreamOpsHttpServer opsServer;
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

        // Item 14 (composite-scenario distributed): the pipeline is pluggable.
        // Default = the trivial empty-source graph (Stage 42 capability tests);
        // a factory installed via pipelineFactoryClass supplies a REAL scenario
        // pipeline (XDSL-declared topology + checkpoints + sink observables).
        ClusterPipelineFactory.PipelineArtifacts artifacts = buildPipelineArtifacts(jobId);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig checkpointConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(artifacts.getCheckpointIntervalMs() != null
                        ? artifacts.getCheckpointIntervalMs() : 1000L)
                .checkpointTimeout(artifacts.getCheckpointTimeoutMs() != null
                        && artifacts.getCheckpointTimeoutMs() > 0
                        ? artifacts.getCheckpointTimeoutMs() : 10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(artifacts.getMaxRetainedCheckpoints() != null
                        && artifacts.getMaxRetainedCheckpoints() > 0
                        ? artifacts.getMaxRetainedCheckpoints() : 3)
                .build();
        checkpointCoordinator = new CheckpointCoordinator(
                jobId, "pipeline-0", idCounter, checkpointStorage, checkpointConfig);

        // Item 14: fingerprint for EpochManifest persistence (mirrors the LOCAL
        // executeWithCheckpoint wiring coordinator.setCurrentFingerprint). A null
        // model (trivial graph) leaves the fingerprint unset — manifests then
        // carry none and the TM-side restore skips the compatibility check.
        if (artifacts.getJobGraph().getStreamModel() != null) {
            checkpointCoordinator.setCurrentFingerprint(
                    artifacts.getJobGraph().getStreamModel().computeFingerprint());
        }

        // Item 14: a fresh coordinator JVM resuming an existing job (e.g. the
        // restore-rescale drill stops run 1 and relaunches against the same
        // checkpoint dir) must not re-issue epoch ids below the durable epoch
        // (the shadow-window problem, P0-03). Restore the latest durable epoch
        // and advance the id counter past it.
        try {
            io.nop.stream.core.checkpoint.CompletedCheckpoint restored =
                    checkpointCoordinator.restoreFromCheckpoint();
            if (restored != null) {
                checkpointCoordinator.advanceCheckpointIdCounterAfterRestore(restored.getCheckpointId());
                LOG.info("JobCoordinatorMain restored durable checkpoint {} for job {} "
                                + "(id counter advanced past it)", restored.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to restore latest checkpoint view for job {} at {} — continuing fresh",
                    jobId, checkpointBaseDir, e);
        }

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

        // The JobGraph must be non-empty in remote-deploy mode: each TaskManager
        // rebuilds its own invokable locally via SubtaskPlanBuilder ->
        // RemoteGraphExecutionPlanBuilder, which iterates the JobGraph
        // vertices/edges. An empty graph yields zero reconstructed subtasks and
        // the TaskManager throws "Subtask N of vertex X not found", reporting
        // FAILED and triggering a rapid globalRecovery loop.
        //
        // Item 14: the JobGraph + DeploymentPlan come from the pipeline factory
        // (trivial empty-source graph by default; scenario pipeline when
        // pipelineFactoryClass is set). The artifacts carry real data flow,
        // checkpoint semantics and sink observables for scenario runs.
        DeploymentPlan deploymentPlan = artifacts.getDeploymentPlan();
        JobGraph jobGraph = artifacts.getJobGraph();

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
        // Item 14: XDSL-declared pipelines ship the declaration spec (TMs rebuild
        // identical graphs locally) instead of the non-serializable compiled graph.
        if (artifacts.getPipelineSpec() != null) {
            coordinator.setPipelineSpec(artifacts.getPipelineSpec());
        }

        // Item 14: distributed checkpoint completion (2PC sink commit) must cross
        // the RPC boundary, and a checkpoint abort must reach remote tasks over
        // its independent control channel (checkpoint-design §13.2).
        coordinator.registerDistributedCommitForwarder();
        coordinator.registerDistributedAbortHandler();

        // Item 16 (P-REQ-12): alert service — logging channel always (grep
        // anchor "nop-stream alert:" in the coordinator process log); webhook
        // channel when alertWebhookUrl is configured.
        java.util.List<io.nop.stream.runtime.alert.IAlertChannel> alertChannels =
                new java.util.ArrayList<>();
        alertChannels.add(new io.nop.stream.runtime.alert.LoggingAlertChannel());
        String alertWebhookUrl = config.get("alertWebhookUrl", "");
        if (!alertWebhookUrl.isEmpty()) {
            alertChannels.add(new io.nop.stream.runtime.alert.WebhookAlertChannel(alertWebhookUrl));
            LOG.info("JobCoordinatorMain webhook alert channel enabled (url={})", alertWebhookUrl);
        }
        coordinator.addJobEventListener(new io.nop.stream.runtime.alert.AlertService(alertChannels));

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

        // Item 14: launch-path periodic checkpoints ("startCheckpointScheduler
        // 或等价机制"). The JobCoordinator-level driver both triggers the
        // PendingCheckpoint and fans the barrier RPC out to all assigned nodes —
        // CheckpointCoordinator.startCheckpointScheduler() alone delivers no
        // barriers. Enabled when the factory supplied a positive interval
        // (scenario pipelines); the trivial default keeps the legacy
        // never-checkpoint behaviour.
        if (artifacts.getCheckpointIntervalMs() != null && artifacts.getCheckpointIntervalMs() > 0) {
            coordinator.startPeriodicCheckpoints(artifacts.getCheckpointIntervalMs());
        }

        // Item 16 (P-REQ-3/5): optional ops HTTP endpoint hosted in this
        // coordinator process (metrics + job query; single-job launch mode —
        // lifecycle submit/stop require the multi-job ops manager). Default off.
        int opsHttpPort = config.getInt("opsHttpPort", 0);
        if (opsHttpPort != 0) {
            try {
                io.nop.stream.runtime.ops.StreamOpsConfig opsConfig =
                        new io.nop.stream.runtime.ops.StreamOpsConfig();
                opsConfig.setEnabled(true);
                opsConfig.setPort(opsHttpPort);
                opsConfig.setBindAddress(config.get("opsHttpBind", "127.0.0.1"));
                opsServer = new io.nop.stream.runtime.ops.StreamOpsHttpServer(
                        opsConfig,
                        new io.nop.stream.runtime.ops.IOpsJobRegistry() {
                            @Override
                            public Set<String> jobIds() {
                                return Set.of(jobId);
                            }

                            @Override
                            public JobCoordinator coordinator(String id) {
                                return jobId.equals(id) ? coordinator : null;
                            }
                        });
                opsServer.start();
                LOG.info("JobCoordinatorMain ops HTTP server started on port {} (bind={})",
                        opsHttpPort, opsConfig.getBindAddress());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to start ops HTTP server on port "
                        + opsHttpPort + " for job " + jobId, e);
            }
        }

        LOG.info("JobCoordinatorMain started (jobId={}, rpcTopic={}, ha={}, deployed subtasks via remote-deploy)",
                jobId, StreamControlRpcTopics.coordinatorTopic(topicNamespace), haEnabled);
        return coordinator;
    }

    /**
     * Item 14: resolves the pipeline artifacts. When {@code pipelineFactoryClass}
     * is configured the named {@link ClusterPipelineFactory} is instantiated
     * (no-arg constructor, test classpath) and asked to build the pipeline;
     * otherwise the trivial empty-source graph (Stage 42 capability baseline)
     * is used.
     */
    private ClusterPipelineFactory.PipelineArtifacts buildPipelineArtifacts(String jobId) {
        String factoryClass = config.get(ClusterLaunchConfig.KEY_PIPELINE_FACTORY_CLASS, "");
        if (!factoryClass.isEmpty()) {
            Class<?> clazz;
            ClusterPipelineFactory factory;
            ClusterPipelineFactory.PipelineArtifacts artifacts;
            try {
                clazz = Class.forName(factoryClass);
                if (!ClusterPipelineFactory.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("pipelineFactoryClass " + factoryClass
                            + " does not implement " + ClusterPipelineFactory.class.getName());
                }
                factory = (ClusterPipelineFactory) clazz.getDeclaredConstructor().newInstance();
                artifacts = factory.buildPipeline(jobId, config);
            } catch (Exception e) {
                // Fail fast with the pipeline identity in the message: a scenario
                // launch that cannot build its pipeline must not fall back to the
                // trivial graph (that would silently deploy the wrong pipeline).
                throw new IllegalStateException("Failed to build pipeline from factory "
                        + factoryClass + " for job " + jobId, e);
            }
            LOG.info("JobCoordinatorMain pipeline from factory {} (vertices={}, checkpointIntervalMs={})",
                    factoryClass, artifacts.getJobGraph().getVertices().size(),
                    artifacts.getCheckpointIntervalMs());
            return artifacts;
        }

        // Trivial baseline (Stage 42): empty-source -> discarding sink.
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
        return new ClusterPipelineFactory.PipelineArtifacts(
                buildTrivialSourceSinkJobGraph(jobId), deploymentPlan, null, null, null);
    }

    /**
     * Stage 42 multi-JVM remediation: builds the minimal real source->sink
     * {@link JobGraph} used by the standalone coordinator to exercise the
     * cross-JVM {@code deployTask} RPC path. See {@code buildTrivialSourceSinkJobGraph}
     * in the start() method for why an empty placeholder JobGraph is incompatible
     * with {@code remoteDeployMode=true}.
     *
     * <p>The source is an empty {@link CollectionReplayableSource} (zero records) and
     * the sink is a discarding {@link PrintSinkFunction}. Vertex IDs ("source"/"sink")
     * match the {@link PartitionedPlan} so
     * {@code RemoteGraphExecutionPlanBuilder.resolveParallelism} can join them.
     */
    private static JobGraph buildTrivialSourceSinkJobGraph(String jobId) {
        StreamSourceOperator<String> sourceOp =
                new StreamSourceOperator<>(new CollectionReplayableSource<>(Collections.emptyList()));
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new PrintSinkFunction<>());

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("source", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));
        return jobGraph;
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
        try {
            if (opsServer != null) {
                opsServer.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop ops HTTP server", e);
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
