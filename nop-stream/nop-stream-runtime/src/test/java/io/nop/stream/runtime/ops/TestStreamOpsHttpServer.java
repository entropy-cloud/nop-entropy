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
        IllegalStateException ex = assertThrows(IllegalStateException.class, refused::start);
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
}
