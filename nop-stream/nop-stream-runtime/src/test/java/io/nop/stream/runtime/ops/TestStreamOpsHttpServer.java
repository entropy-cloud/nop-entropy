/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.core.exceptions.StreamException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.runtime.metrics.EngineMetrics;
import io.nop.stream.runtime.metrics.TaskNodeMetrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-3/6): ops HTTP server — Prometheus TextFormat 004 +
 * OpenMetrics negotiation, job/cluster/node metric families, explicit error
 * semantics (disabled server / unknown job / wrong method), checkpoint query
 * endpoint.
 */
class TestStreamOpsHttpServer {

    @TempDir
    Path tempDir;

    private StreamOpsHttpServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private void startServer(int port, IOpsJobRegistry registry) throws Exception {
        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(port);
        server = new StreamOpsHttpServer(config, registry);
        server.start();
    }

    @Test
    void testDisabledServerRefusesToStart() {
        StreamOpsConfig disabled = new StreamOpsConfig(); // default enabled=false
        StreamOpsHttpServer refused = new StreamOpsHttpServer(disabled, null);
        StreamException ex = assertThrows(StreamException.class, refused::start);
        assertTrue(ex.getMessage().contains("disabled"));
        assertFalse(refused.isRunning());
    }

    @Test
    void testMetricsTextFormat004WithThreeLevelFamilies() throws Exception {
        startServer(0, null);
        int port = server.getBoundPort();

        // cluster level: engine nodes.active gauge
        EngineMetrics.registerNodesActiveGauge(
                StreamMetricsRegistries.registry(), () -> 2);
        // job level: engine checkpoint counter
        EngineMetrics.create(StreamMetricsRegistries.registry(), "ops-http-job").checkpointCompleted(1024L, 30L);
        // node level: task deployed counter
        TaskNodeMetrics.create(StreamMetricsRegistries.registry(), "ops-tm-0").taskDeployed();

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("text/plain") && contentType.contains("version=0.0.4"),
                "default exposition is Prometheus TextFormat 0.0.4, got: " + contentType);

        String body = resp.body();
        assertTrue(body.contains("# TYPE"), "TextFormat 004 exposition carries # TYPE lines");
        assertTrue(body.contains("nop_stream_engine_nodes_active"),
                "cluster-level family present: " + body);
        assertTrue(body.contains("nop_stream_engine_checkpoints_completed")
                        && body.contains("ops-http-job"),
                "job-level family present with jobId tag");
        assertTrue(body.contains("nop_stream_task_deployed_total")
                        && body.contains("ops-tm-0"),
                "node-level family present with nodeId tag");
    }

    @Test
    void testMetricsOpenMetricsNegotiation() throws Exception {
        startServer(0, null);
        int port = server.getBoundPort();
        EngineMetrics.create(StreamMetricsRegistries.registry(), "om-job").checkpointCompleted(1L, 1L);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                        .header("Accept", "application/openmetrics-text; version=1.0.0")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/openmetrics-text"),
                "OpenMetrics negotiated content type, got: " + contentType);
        assertTrue(resp.body().trim().endsWith("# EOF"),
                "OpenMetrics exposition ends with the # EOF sentinel");
    }

    @Test
    void testMetricsRejectsNonGet() throws Exception {
        startServer(0, null);
        int port = server.getBoundPort();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                        .POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
        assertTrue(resp.body().contains("METHOD_NOT_ALLOWED"));
    }

    @Test
    void testUnknownOpsPathIsExplicit404() throws Exception {
        startServer(0, null);
        int port = server.getBoundPort();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/nope")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("NOT_FOUND"));
    }

    @Test
    void testCheckpointQueryUnknownJobIsExplicit404() throws Exception {
        startServer(0, emptyRegistry());
        int port = server.getBoundPort();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/jobs/ghost/checkpoints")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("JOB_NOT_FOUND"),
                "unknown job is an explicit structured error, body=" + resp.body());
    }

    static IOpsJobRegistry emptyRegistry() {
        return new IOpsJobRegistry() {
            @Override
            public java.util.Set<String> jobIds() {
                return java.util.Set.of();
            }

            @Override
            public io.nop.stream.runtime.coordinator.JobCoordinator coordinator(String jobId) {
                return null;
            }
        };
    }

    // ==================== CRA 8.42 R4-02 W-01 (C10-017, 红①) ====================
    // Submit-body size gate: the POST /jobs endpoint must reject an oversized
    // request body with a typed 413 BEFORE materializing/parsing it (pre-fix the
    // readAllBytes path answered 400 parse-failure — or 201 for valid JSON — with
    // no size bound at all). Reverting the size gate turns both oversized-body
    // tests red (413 expected, 400/201 observed).

    /** Real submit path fixture (mirrors TestOpsRestLifecycleE2E's setup shape). */
    @TempDir
    Path submitTempDir;

    private OpsJobManager submitJobManager;
    private io.nop.stream.runtime.taskmanager.TaskManager submitTaskManager;

    private void startServerWithManager(int maxBodyBytes) throws Exception {
        client = HttpClient.newHttpClient();
        io.nop.stream.runtime.cluster.ClusterRegistry clusterRegistry =
                new io.nop.stream.runtime.cluster.InMemoryClusterRegistry();
        io.nop.api.core.message.IMessageService messageService =
                new io.nop.message.core.local.LocalMessageService();
        submitTaskManager = new io.nop.stream.runtime.taskmanager.TaskManager(
                "w01-node", "localhost:0", 4, messageService, clusterRegistry, "w01-control");
        submitTaskManager.start();
        submitJobManager = new OpsJobManager(messageService, clusterRegistry,
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(
                        submitTempDir.resolve("w01-ckpt").toString()),
                java.util.Map.of("w01-node", submitTaskManager));

        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        // S1 seam: a tiny configured limit turns the gate deterministically
        // observable without allocating megabytes (adjudication A.1 sizing:
        // JobSubmissionSpec is three fields — 1KB already exceeds legal payloads).
        config.setMaxBodyBytes(maxBodyBytes);
        server = new StreamOpsHttpServer(config, submitJobManager, submitJobManager);
        server.start();
    }

    private void stopServerWithManager() {
        if (server != null) {
            server.stop();
        }
        if (submitJobManager != null) {
            submitJobManager.close();
        }
        if (submitTaskManager != null) {
            submitTaskManager.stop();
        }
    }

    private static final String SUBMIT_FACTORY_FQCN =
            "io.nop.stream.runtime.ops.TestOpsRestLifecycleE2E$SlowPipelineFactory";

    @Test
    void oversizedSubmitBodyWithContentLengthAnswersTyped413() throws Exception {
        startServerWithManager(1024);
        try {
            // 2KB invalid-JSON body with an explicit Content-Length: the typed 413
            // (not the 400 BAD_SUBMISSION_BODY parse failure) proves the request was
            // rejected BEFORE any body read / JSON parse.
            String oversized = "x".repeat(2048);
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getBoundPort() + "/jobs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(oversized)).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(413, resp.statusCode(),
                    "oversized declared body must be a typed 413, got: " + resp.statusCode()
                            + " body=" + resp.body());
            assertTrue(resp.body().contains("SUBMIT_BODY_TOO_LARGE"),
                    "rejection carries the typed error code, body=" + resp.body());
            assertFalse(resp.body().contains("BAD_SUBMISSION_BODY"),
                    "the parse-failure path must not be reached, body=" + resp.body());
        } finally {
            stopServerWithManager();
        }
    }

    @Test
    void oversizedChunkedSubmitBodyAnswersTyped413() throws Exception {
        startServerWithManager(1024);
        try {
            // Chunked variant (no Content-Length header — ofByteArrays has unknown
            // length so the JDK client emits Transfer-Encoding: chunked): the
            // bounded-read leg of the double check must still reject with 413
            // (a CL-only pre-check would be structurally bypassed here).
            byte[] chunk1 = new byte[1024];
            byte[] chunk2 = new byte[1024];
            java.util.Arrays.fill(chunk1, (byte) 'x');
            java.util.Arrays.fill(chunk2, (byte) 'x');
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getBoundPort() + "/jobs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArrays(
                                    java.util.List.of(chunk1, chunk2))).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(413, resp.statusCode(),
                    "oversized chunked body must be a typed 413, got: " + resp.statusCode()
                            + " body=" + resp.body());
            assertTrue(resp.body().contains("SUBMIT_BODY_TOO_LARGE"),
                    "rejection carries the typed error code, body=" + resp.body());
        } finally {
            stopServerWithManager();
        }
    }

    @Test
    void legalSubmitBodyUnderLimitStillSubmits() throws Exception {
        startServerWithManager(1024);
        try {
            // Zero-regression face: a legal body under the configured limit passes
            // the gate and reaches the real submit path (201).
            String legal = "{\"jobId\":\"w01-legal-job\",\"pipelineFactoryClass\":\""
                    + SUBMIT_FACTORY_FQCN + "\"}";
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getBoundPort() + "/jobs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(legal)).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(201, resp.statusCode(),
                    "legal body under the limit must still submit, got: " + resp.statusCode()
                            + " body=" + resp.body());
        } finally {
            stopServerWithManager();
        }
    }
}
