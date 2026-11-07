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
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.functions.sink.PrintSinkFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.testsupport.TestAwait;
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
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;
import io.nop.stream.runtime.taskmanager.TaskManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (Phase 4 端到端验证): REST submit → run → stop → list/detail — the
 * complete lifecycle over a REAL TaskManager + remote-deploy path, plus the
 * explicit error semantics of every endpoint and the governance sweep.
 */
class TestOpsRestLifecycleE2E {

    /**
     * Slow bounded source (stays RUNNING until the test stops the job).
     * 时间确定化（plan 2510）NAMED RESIDUAL：源是「被测系统主动取消才结束」的
     * 长驻模拟——stop 路径（OpsJobManager.stop → coordinator abort → TM cancel →
     * future.cancel(true) 线程中断）是它的唯一结束信号，发射间隔 sleep 即可中断
     * 阻塞点本身（testing.md「由测试主动中断结束的模拟耗时」允许形态）。发射
     * 频率不承载任何断言语义。
     */
    public static final class SlowSourceFunction
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private static final long serialVersionUID = 1L;
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            long i = 0;
            while (running) {
                ctx.collect(i++);
                Thread.sleep(100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Submit-body factory: slow source → sink trivial pipeline (Stage 42 shape). */
    public static final class SlowPipelineFactory implements ClusterPipelineFactory {
        @Override
        public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) {
            StreamSourceOperator<Long> sourceOp =
                    new StreamSourceOperator<>(new SlowSourceFunction());
            StreamSinkOperator<Long> sinkOp = new StreamSinkOperator<>(new PrintSinkFunction<>());

            OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
            OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

            StreamTaskInvokable sourceInv = new StreamTaskInvokable(sourceChain);
            StreamTaskInvokable sinkInv = new StreamTaskInvokable(sinkChain);

            JobVertex sourceVertex = new JobVertex("source", "Source", 1,
                    Collections.singletonList(sourceChain), sourceInv);
            JobVertex sinkVertex = new JobVertex("sink", "Sink", 1,
                    Collections.singletonList(sinkChain), sinkInv);

            JobGraph jobGraph = new JobGraph(jobId);
            jobGraph.addVertex(sourceVertex);
            jobGraph.addVertex(sinkVertex);
            jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));

            Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
            vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
            vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
            PartitionedPlan partitionedPlan = new PartitionedPlan(jobId, "pipeline-0",
                    vertexPlans,
                    List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                    null, null);
            DeploymentPlan deploymentPlan = new DeploymentPlan(
                    jobId, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);
            return new PipelineArtifacts(jobGraph, deploymentPlan, 500L, 10_000L, 3);
        }
    }

    private static final String FACTORY_FQCN =
            "io.nop.stream.runtime.ops.TestOpsRestLifecycleE2E$SlowPipelineFactory";

    @TempDir
    Path tempDir;

    private StreamOpsHttpServer server;
    private OpsJobManager jobManager;
    private TaskManager taskManager;
    private ClusterRegistry clusterRegistry;
    private HttpClient client;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        client = HttpClient.newHttpClient();

        clusterRegistry = new InMemoryClusterRegistry();
        IMessageService messageService = new LocalMessageService();
        taskManager = new TaskManager("node-1", "localhost:0", 4, messageService,
                clusterRegistry, "control-topic");
        taskManager.start();

        jobManager = new OpsJobManager(messageService, clusterRegistry,
                new LocalFileCheckpointStorage(tempDir.toString()),
                Map.of("node-1", taskManager));

        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        server = new StreamOpsHttpServer(config, jobManager, jobManager);
        server.start();
        port = server.getBoundPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        jobManager.close();
        taskManager.stop();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String submitBody(String jobId, String factoryClass) {
        return "{\"jobId\":\"" + jobId + "\",\"pipelineFactoryClass\":\"" + factoryClass + "\"}";
    }

    @Test
    void fullLifecycleSubmitRunStopListDetail() throws Exception {
        String jobId = "rest-lifecycle-job";

        // submit → 201 RUNNING
        HttpResponse<String> submitted = post("/jobs", submitBody(jobId, FACTORY_FQCN));
        assertEquals(201, submitted.statusCode(), "submit body: " + submitted.body());
        assertTrue(submitted.body().contains("\"jobStatus\":\"RUNNING\""),
                "submitted job is RUNNING: " + submitted.body());

        // the job is really deployed on the real TaskManager (deployTask path)
        assertTrue(taskManager.getRunningTaskCount() >= 1,
                "remote-deploy must place tasks on the TaskManager");

        // list contains the job
        HttpResponse<String> list = get("/jobs");
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains(jobId), "job list contains submitted job: " + list.body());

        // detail
        HttpResponse<String> detail = get("/jobs/" + jobId);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("\"jobStatus\":\"RUNNING\""), detail.body());
        assertTrue(detail.body().contains("\"checkpointOverview\""), detail.body());

        // metrics carry the deployed tasks for this job's node (wiring proof)
        HttpResponse<String> metrics = get("/metrics");
        assertTrue(metrics.body().contains("nop_stream_task_deployed_total")
                && metrics.body().contains("node-1"));

        // stop CANCEL → 200 CANCELED
        HttpResponse<String> stop = post("/jobs/" + jobId + "/stop?mode=CANCEL", "");
        assertEquals(200, stop.statusCode(), "stop body: " + stop.body());
        assertTrue(stop.body().contains("\"jobStatus\":\"CANCELED\""), stop.body());

        // detail now shows CANCELED
        HttpResponse<String> after = get("/jobs/" + jobId);
        assertTrue(after.body().contains("\"jobStatus\":\"CANCELED\""), after.body());
    }

    @Test
    void endpointErrorSemantics() throws Exception {
        // unknown job stop → 404
        assertEquals(404, post("/jobs/ghost-job/stop", "").statusCode());

        // unsupported stop mode → 400
        HttpResponse<String> submitted = post("/jobs", submitBody("err-job", FACTORY_FQCN));
        assertEquals(201, submitted.statusCode());
        // Deployment-level TM→coordinator control wiring: every production path
        // (TaskManagerMain / EmbeddedDistributedExecutor / RpcDistributedExecutor)
        // sets the coordinator RPC on the TaskManager. Without it the remotely
        // deployed tasks cannot ACK checkpoints and the SUSPEND terminal
        // savepoint below would time out (job FAILED).
        taskManager.setCoordinatorRpcService(jobManager.coordinator("err-job"));

        // WI9 (plan 2333): SUSPEND is now ALLOWED over REST — terminal savepoint
        // then a recoverable finish (health FINISHED, record kept, running=false)
        HttpResponse<String> suspend = post("/jobs/err-job/stop?mode=SUSPEND", "");
        assertEquals(200, suspend.statusCode(), "stop body: " + suspend.body());
        assertTrue(suspend.body().contains("\"health\":\"FINISHED\""), suspend.body());
        assertTrue(suspend.body().contains("\"running\":false"), suspend.body());

        // EXPORT_SAVEPOINT remains rejected (keeps the job running; coordinator-RPC only)
        assertEquals(400, post("/jobs/err-job/stop?mode=EXPORT_SAVEPOINT", "").statusCode());

        // duplicate submit → 409 (the suspended job record is still hosted)
        assertEquals(409, post("/jobs", submitBody("err-job", FACTORY_FQCN)).statusCode());

        // malformed submit body → 400
        assertEquals(400, post("/jobs", "{not-json").statusCode());
        // missing factory class → 400
        assertEquals(400, post("/jobs", "{\"jobId\":\"no-factory\"}").statusCode());
        // unknown factory class → 400 (explicit, no silent trivial fallback)
        HttpResponse<String> badFactory = post("/jobs",
                submitBody("bad-factory", "com.example.DoesNotExist"));
        assertEquals(400, badFactory.statusCode());
        assertTrue(badFactory.body().contains("SUBMIT_REJECTED"), badFactory.body());

        // unknown job detail → 404
        assertEquals(404, get("/jobs/ghost-job").statusCode());
    }

    /**
     * WI9 (plan 2333): manager-level stop whitelist — SUSPEND passes (returns
     * the coordinator, no unsupported-mode rejection), EXPORT_SAVEPOINT is
     * still rejected typed with the updated message naming the supported set.
     */
    @Test
    void stopWhitelistAllowsSuspendRejectsExportSavepoint() {
        submitViaManager(jobManager, "wl-job");
        // TM→coordinator control wiring (see endpointErrorSemantics) so the
        // SUSPEND terminal savepoint can be ACKed by the deployed tasks.
        taskManager.setCoordinatorRpcService(jobManager.coordinator("wl-job"));

        io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> jobManager.stop("wl-job",
                        io.nop.stream.core.checkpoint.JobTerminationMode.EXPORT_SAVEPOINT));
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(),
                ex.getErrorCode(), "EXPORT_SAVEPOINT rejection must stay typed");
        assertTrue(String.valueOf(ex).contains("CANCEL, DRAIN and SUSPEND"),
                "rejection message must name the supported set: " + ex);

        io.nop.stream.runtime.coordinator.JobCoordinator coordinator =
                jobManager.stop("wl-job", io.nop.stream.core.checkpoint.JobTerminationMode.SUSPEND);
        assertNotNull(coordinator, "SUSPEND must pass the whitelist and terminate the job");
    }

    @Test
    void threadDumpEndpointReturnsLiveStacks() throws Exception {
        assertEquals(201, post("/jobs", submitBody("td-job", FACTORY_FQCN)).statusCode());
        HttpResponse<String> dump = get("/jobs/td-job/threaddump");
        assertEquals(200, dump.statusCode());
        String contentType = dump.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("text/plain"));
        assertTrue(dump.body().contains("\""), "thread dump carries stack frames");
        assertTrue(dump.body().toLowerCase().contains("thread"), "thread dump names threads");
    }

    @Test
    void governanceSweepPrunesExpiredHistoryAndTerminalRecords() throws Exception {
        // 时间确定化（plan 2510）：terminalAt 老化经 CoreMetrics（OpsJobManager 侧
        // 判定读取），测试注册 ManualClock 显式推进虚拟时间越过 0 分钟 retention
        // 截止——不再用真实 10ms 观察窗。时钟逐用例注册、finally 恢复默认时钟。
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            // tight governance: history cap 2, zero retention, real deploy path so the
            // checkpoint coordinator has registered tasks (trigger gate)
            StreamGovernanceConfig gov = new StreamGovernanceConfig();
            gov.setCheckpointHistoryMaxEntries(2);
            gov.setCheckpointHistoryRetentionMinutes(0);
            gov.setJobRecordRetentionMinutes(0);
            ClusterRegistry govRegistry = new InMemoryClusterRegistry();
            TaskManager govTm = new TaskManager("gov-node", "localhost:0", 4,
                    new LocalMessageService(), govRegistry, "gov-control");
            govTm.start();
            OpsJobManager manager = new OpsJobManager(new LocalMessageService(), govRegistry,
                    new LocalFileCheckpointStorage(tempDir.resolve("gov").toString()),
                    Map.of("gov-node", govTm), gov);
            try {
                // submit → record history entries via the real abort path → stop → sweep
                submitViaManager(manager, "gov-job");

                io.nop.stream.runtime.coordinator.JobCoordinator coordinator =
                        manager.coordinator("gov-job");
                // 条件驱动触发循环：每次条件求值尝试一次触发（生产侧门控拒绝即
                // 下一轮重试），abort 记入历史；本地计数满 3 次即止（历史条目受
                // checkpointHistoryMaxEntries=2 封顶，不能以历史尺寸为条件）。
                // 上限仅挂死保护。
                java.util.concurrent.atomic.AtomicInteger recorded =
                        new java.util.concurrent.atomic.AtomicInteger();
                TestAwait.until("3 aborted checkpoint epochs recorded via the real abort path",
                        () -> {
                            if (recorded.get() < 3) {
                                var pending = coordinator.getCheckpointCoordinator()
                                        .tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                                if (pending != null) {
                                    coordinator.getCheckpointCoordinator()
                                            .abortPendingCheckpoint(pending, "governance-test");
                                    recorded.incrementAndGet();
                                }
                            }
                            return recorded.get() >= 3;
                        });
                assertTrue(coordinator.getCheckpointCoordinator().getCheckpointHistory().size() >= 2,
                        "abort path recorded >=2 history entries");

                manager.stop("gov-job", io.nop.stream.core.checkpoint.JobTerminationMode.CANCEL);
                // 虚拟时钟推进越过 retention 截止：terminalAt（CoreMetrics 读取）
                // 相对 sweep 的 now 已老化过期。
                clock.advance(60_000L);

                int pruned = manager.governanceSweep();
                assertTrue(pruned > 0, "sweep must prune expired history entries / records");
                assertTrue(manager.coordinator("gov-job") == null,
                        "terminal job record past retention is pruned");
            } finally {
                manager.close();
                govTm.stop();
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    private int submitViaManager(OpsJobManager manager, String jobId) {
        JobSubmissionSpec spec = new JobSubmissionSpec();
        spec.setJobId(jobId);
        spec.setPipelineFactoryClass(FACTORY_FQCN);
        manager.submit(spec);
        return 201;
    }
}
