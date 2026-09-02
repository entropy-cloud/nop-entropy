/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;
import io.nop.stream.runtime.taskmanager.TaskManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (Phase 3 端到端验证): the complete path — real coordinator start →
 * real task deploy on a real TaskManager → real checkpoint trigger/ACK/
 * completion → metric production → HTTP /metrics exposure, plus the
 * /jobs/{jobId}/checkpoints query face. No mocks on the observed paths.
 */
class TestMetricsExposureE2E {

    private static final String JOB_ID = "metrics-exposure-e2e";

    @TempDir
    Path tempDir;

    private StreamOpsHttpServer opsServer;
    private TaskManager taskManager;
    private JobCoordinator coordinator;
    private ClusterRegistry clusterRegistry;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (opsServer != null) {
            opsServer.stop();
        }
        if (coordinator != null) {
            coordinator.stop();
        }
        if (taskManager != null) {
            taskManager.stop();
        }
    }

    @Test
    void fullPathFromJobStartToMetricsExposure() throws Exception {
        // 1. Ops server FIRST (prometheus member attached before any metric increment)
        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        opsServer = new StreamOpsHttpServer(config, new IOpsJobRegistry() {
            @Override
            public java.util.Set<String> jobIds() {
                return java.util.Set.of(JOB_ID);
            }

            @Override
            public JobCoordinator coordinator(String jobId) {
                return JOB_ID.equals(jobId) ? TestMetricsExposureE2E.this.coordinator : null;
            }
        });
        opsServer.start();
        int port = opsServer.getBoundPort();

        // 2. Real cluster: registry + real TaskManager node
        clusterRegistry = new InMemoryClusterRegistry();
        IMessageService messageService = new LocalMessageService();
        taskManager = new TaskManager("node-1", "localhost:0", 4, messageService,
                clusterRegistry, "control-topic");
        taskManager.start();

        // 3. Real coordinator hosting the job (receiveAssignment deploy path)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig checkpointConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage, checkpointConfig);

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                JOB_ID, "pipeline-0", vertexPlans,
                java.util.List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                JOB_ID, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);

        Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> rpcServices = new LinkedHashMap<>();
        rpcServices.put("node-1", taskManager);
        coordinator = new JobCoordinator(JOB_ID, "coordinator-1", deploymentPlan,
                clusterRegistry, checkpointCoordinator, rpcServices);
        coordinator.start();
        coordinator.assignTasks();

        // 4. Real checkpoint completion: trigger + ACKs from both task locations
        PendingCheckpoint pending = coordinator.triggerCheckpoint();
        assertNotNull(pending, "checkpoint must trigger");
        TaskLocation sourceLoc = new TaskLocation(JOB_ID, "pipeline-0", "source", 0);
        TaskLocation sinkLoc = new TaskLocation(JOB_ID, "pipeline-0", "sink", 0);
        coordinator.receiveCheckpointAck(new CheckpointAckMessage(sourceLoc,
                pending.getCheckpointId(), TaskStateSnapshot.empty(sourceLoc), coordinator.getFencingEpoch()));
        coordinator.receiveCheckpointAck(new CheckpointAckMessage(sinkLoc,
                pending.getCheckpointId(), TaskStateSnapshot.empty(sinkLoc), coordinator.getFencingEpoch()));
        pending.getCompletableFuture().get(10, TimeUnit.SECONDS);

        // 5. GET /metrics: cluster + job + node families all exposed over HTTP
        HttpResponse<String> metrics = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metrics.statusCode());
        String body = metrics.body();
        assertTrue(body.contains("nop_stream_engine_nodes_active"),
                "cluster-level family: " + body);
        assertTrue(body.contains("nop_stream_engine_checkpoints_completed")
                        && body.contains(JOB_ID),
                "job-level family with completed checkpoint");
        assertTrue(body.contains("nop_stream_task_deployed_total")
                        && body.contains("node-1"),
                "node-level family with real deployments");

        // 6. /jobs/{jobId}/checkpoints: overview + history with the completed entry
        HttpResponse<String> checkpoints = client.send(
                HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/jobs/" + JOB_ID + "/checkpoints")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, checkpoints.statusCode(), "checkpoint query body: " + checkpoints.body());
        String cpBody = checkpoints.body();
        assertTrue(cpBody.contains("\"numCompletedCheckpoints\":1"),
                "overview counts the completed checkpoint: " + cpBody);
        assertTrue(cpBody.contains("\"status\":\"COMPLETED\""),
                "history carries the completed entry: " + cpBody);
        assertTrue(cpBody.contains("\"failureCause\""),
                "failureCause field present in the query structure (P-REQ-6)");
    }

    @Test
    void metricsReporterFileSinkWritesSnapshot() throws Exception {
        // register a meter, run the file reporter for one tick, assert the
        // snapshot file contains the nop-stream meter (engine family)
        io.nop.stream.runtime.metrics.EngineMetrics.create(
                io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                "reporter-job-" + System.nanoTime()).checkpointCompleted(11L, 2L);

        Path file = tempDir.resolve("metrics-snapshot.txt");
        StreamMetricsReporter reporter = new StreamMetricsReporter(50, "file", file);
        reporter.start();
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline && !Files.exists(file)) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(file), "snapshot file written after the first tick");
            String text = Files.readString(file);
            assertTrue(text.contains("nop_stream_engine_checkpoints_completed"),
                    "snapshot contains engine family meters: " + text);
        } finally {
            reporter.stop();
        }
    }
}
