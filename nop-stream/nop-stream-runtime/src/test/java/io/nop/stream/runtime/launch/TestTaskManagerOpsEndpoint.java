/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
import io.nop.stream.runtime.taskmanager.TaskManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 32 (Phase 1 接线验证): the TM-form ops HTTP endpoint wired into
 * {@link TaskManagerMain} via the {@code opsHttpPort}/{@code opsHttpBind} launch
 * args (same raw-arg pattern as the JC). Proves, against the real
 * {@code TaskManagerMain.start()} path:
 *
 * <ul>
 *   <li>endpoint default OFF (no listener without an explicit port — explicit-off
 *       semantics identical to the JC);</li>
 *   <li>with a port: {@code GET /metrics} answers 200 Prometheus text, and the
 *       {@code /jobs} family keeps its EXISTING structured-error semantics on a
 *       registry-less process (list/submit/stop → 503, detail/checkpoints → 404 —
 *       not a uniform 404 blanket); Accept negotiation works;</li>
 *   <li>shutdown stops the endpoint with the process lifecycle;</li>
 *   <li><strong>wiring proof</strong>: with a REAL task executing on the
 *       TaskManager (real deployTask path), the scraped output contains the
 *       io/operator/task layer meters AND the channel queue gauge under their
 *       Prometheus WIRE names ({@code nop_stream_io_emit_time_seconds_count},
 *       {@code nop_stream_io_records_emitted_total},
 *       {@code nop_stream_io_channel_queue_size}, ...) — the endpoint is
 *       connected to the registry the real task path registers into, not an
 *       empty registry.</li>
 * </ul>
 */
class TestTaskManagerOpsEndpoint {

    @TempDir
    Path tempDir;

    private String jdbcUrl;
    private String topicNamespace;
    private TaskManagerMain taskMain;
    private HttpClient client;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        String dbFile = tempDir.resolve("tm-ops-test-" + StringHelper.generateUUID()).toString();
        jdbcUrl = "jdbc:h2:file:" + dbFile + ";MODE=MySQL";
        topicNamespace = "ns-" + StringHelper.generateUUID().substring(0, 8);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (taskMain != null) {
            taskMain.shutdown();
            taskMain = null;
        }
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot allocate a free port for the test", e);
        }
    }

    private HttpResponse<String> get(int port, String path, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).GET();
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ==================== default off ====================

    @Test
    void endpointStaysOffWithoutExplicitPort() throws Exception {
        taskMain = new TaskManagerMain(ClusterLaunchConfig.parse(new String[]{
                "nodeId=tm-ops-off", "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace, "pollIntervalMs=20"}));
        TaskManager tm = taskMain.start();
        assertTrue(tm.isRunning());
        assertNull(taskMain.getOpsHttpServer(),
                "no opsHttpPort arg → no endpoint (explicit-off, same semantics as the JC)");
    }

    // ==================== TM-form endpoint semantics ====================

    @Test
    void endpointServesMetricsAndStructuredJobErrors() throws Exception {
        int port = findFreePort();
        taskMain = new TaskManagerMain(ClusterLaunchConfig.parse(new String[]{
                "nodeId=tm-ops-on", "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace, "pollIntervalMs=20",
                "opsHttpPort=" + port}));
        taskMain.start();
        assertNotNull(taskMain.getOpsHttpServer());
        assertTrue(taskMain.getOpsHttpServer().isRunning());

        // /metrics: 200 + Prometheus TextFormat 0.0.4 by default.
        HttpResponse<String> metrics = get(port, "/metrics");
        assertEquals(200, metrics.statusCode());
        String contentType = metrics.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("text/plain") && contentType.contains("version=0.0.4"),
                "default exposition is TextFormat 0.0.4, got: " + contentType);
        assertTrue(metrics.body().contains("# TYPE"), "exposition carries # TYPE lines");

        // OpenMetrics negotiation.
        HttpResponse<String> openMetrics = get(port, "/metrics",
                "Accept", "application/openmetrics-text; version=1.0.0");
        assertEquals(200, openMetrics.statusCode());
        assertTrue(openMetrics.headers().firstValue("Content-Type").orElse("")
                .contains("application/openmetrics-text"), "Accept negotiation works");
        assertTrue(openMetrics.body().trim().endsWith("# EOF"));

        // /jobs family: EXISTING structured errors on a registry-less process —
        // list/submit/stop are 503 (no registry/manager hosted here), job detail
        // and checkpoints are 404 (unknown job) — never a silent blanket 404.
        HttpResponse<String> list = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/jobs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, list.statusCode());
        assertTrue(list.body().contains("REGISTRY_UNAVAILABLE"), list.body());

        HttpResponse<String> submit = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/jobs"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, submit.statusCode());
        assertTrue(submit.body().contains("SUBMIT_UNAVAILABLE"), submit.body());

        HttpResponse<String> stop = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/jobs/ghost/stop"))
                        .POST(HttpRequest.BodyPublishers.ofString("")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, stop.statusCode());
        assertTrue(stop.body().contains("STOP_UNAVAILABLE"), stop.body());

        assertEquals(404, get(port, "/jobs/ghost").statusCode());
        assertTrue(get(port, "/jobs/ghost").body().contains("JOB_NOT_FOUND"));
        assertEquals(404, get(port, "/jobs/ghost/checkpoints").statusCode());

        // Endpoint stops with the process lifecycle.
        taskMain.shutdown();
        taskMain = null;
        int finalPort = port;
        assertThrows(Exception.class, () -> get(finalPort, "/metrics"),
                "connection must be refused after TaskManagerMain.shutdown()");
    }

    // ==================== wiring proof: real task execution → wire-form meters ====================

    @Test
    void endpointExposesRealTaskExecutionMetricsUnderWireNames() throws Exception {
        int port = findFreePort();
        taskMain = new TaskManagerMain(ClusterLaunchConfig.parse(new String[]{
                "nodeId=tm-ops-wiring", "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace, "pollIntervalMs=20",
                "opsHttpPort=" + port}));
        TaskManager tm = taskMain.start();

        // Endpoint FIRST so counters are visible from zero (Prometheus member
        // semantics), then deploy a real source→sink pipeline through the REAL
        // deployTask path (SubtaskPlanBuilder + RemoteTaskDeploySupport + the
        // per-task MicrometerStreamTaskMetrics injection).
        long epoch = 1L;
        tm.updateFencingToken(epoch);

        int recordCount = 20;
        List<Integer> sinkReceived = new CopyOnWriteArrayList<>();
        String jobId = "tm-ops-wiring-" + StringHelper.generateUUID().substring(0, 8);
        JobGraph graph = buildSourceSinkGraph(jobId, recordCount, sinkReceived);

        // Sink first (its input channel subscribes), then the source (emits).
        tm.deployTask(new TaskDeploymentDescriptor(
                jobId, "sink", 0, "tm-ops-wiring", "attempt-sink", 1, epoch, graph, null, null), epoch);
        tm.deployTask(new TaskDeploymentDescriptor(
                jobId, "source", 0, "tm-ops-wiring", "attempt-src", 1, epoch, graph, null, null), epoch);

        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline && sinkReceived.size() < recordCount) {
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        assertEquals(recordCount, sinkReceived.size(),
                "the real data path must deliver every record before scraping");

        HttpResponse<String> metrics = get(port, "/metrics");
        assertEquals(200, metrics.statusCode());
        String body = metrics.body();
        // Assertions use Prometheus WIRE names (dot-forms do not exist on the
        // wire): timers carry _seconds_* suffixes, counters _total.
        assertTrue(body.contains("nop_stream_task_deployed_total"),
                "task layer: " + body);
        assertTrue(body.contains("nop_stream_io_records_emitted_total"),
                "io layer emitted (source writer): " + body);
        assertTrue(body.contains("nop_stream_io_emit_time_seconds_count")
                        && body.contains("nop_stream_io_emit_time_seconds_sum"),
                "io layer emit-time timer (backpressure proxy): " + body);
        assertTrue(body.contains("nop_stream_operator_records_in_total"),
                "operator layer (sink input dispatch): " + body);
        assertTrue(body.contains("nop_stream_io_channel_queue_size"),
                "item 32 channel queue gauge visible on the TM face: " + body);
    }

    // ==================== fixtures ====================

    private static JobGraph buildSourceSinkGraph(String jobId, int recordCount,
                                                 List<Integer> sinkReceived) {
        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                // Let the slower-deployed downstream subscriber settle first (the
                // in-process LocalMessageService drops sends to unsubscribed
                // topics — same ordering note as the deploy-wiring e2e).
                // 时间窗（部署顺序），循环形式等待
                io.nop.stream.runtime.testsupport.TestAwait.elapsed("downstream subscriber settle window", 750L);
                for (int i = 0; i < recordCount; i++) {
                    ctx.collect(i);
                    Thread.sleep(5L);
                }
                Thread.sleep(500L);
            }

            @Override
            public void cancel() {
            }
        };
        SinkFunction<Integer> sink = new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                sinkReceived.add(value);
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(sink);
        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        JobGraph graph = new JobGraph(jobId);
        graph.addVertex(new JobVertex("source", "Source", 1,
                Collections.singletonList(sourceChain), new StreamTaskInvokable(sourceChain)));
        graph.addVertex(new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), new StreamTaskInvokable(sinkChain)));
        graph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));
        return graph;
    }
}
