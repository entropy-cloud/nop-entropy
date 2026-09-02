/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import io.nop.stream.core.metrics.StreamMetricsRegistries;

/**
 * Item 16 (P-REQ-3/6): the ops HTTP server hosted in the coordinator process
 * (JDK built-in HttpServer — zero new framework dependency for the
 * independent-process runtime).
 *
 * <p>Endpoints (item 16 Phase 3; job lifecycle endpoints arrive with the ops
 * manager in Phase 4):
 * <ul>
 *   <li>{@code GET /metrics} — Prometheus scrape of all {@code nop.stream.*}
 *       meters. Default exposition is Prometheus TextFormat 0.0.4; an
 *       {@code Accept: application/openmetrics-text} header negotiates the
 *       OpenMetrics format.</li>
 *   <li>{@code GET /jobs/{jobId}/checkpoints} — checkpoint overview (counters,
 *       latest duration/size, failure cause) + bounded observation history
 *       (each FAILED/ABORTED entry carries its failure cause).</li>
 * </ul>
 *
 * <p>Explicit-off semantics: constructing with a disabled config and calling
 * {@link #start()} throws {@link IllegalStateException} — the launch path
 * fails fast instead of silently serving nothing. Unknown job ids answer 404
 * with a structured error body; malformed requests answer 400.
 *
 * <p>The Prometheus registry is attached to the process composite registry at
 * server start; meters incremented before attachment are not replayed into it
 * (standard Prometheus member semantics — enable the endpoint before starting
 * jobs to observe counters from zero).
 */
public class StreamOpsHttpServer {

    public static final String CONTENT_TYPE_TEXT_004 = "text/plain; version=0.0.4; charset=utf-8";
    public static final String CONTENT_TYPE_OPENMETRICS = "application/openmetrics-text; version=1.0.0; charset=utf-8";
    public static final String ACCEPT_OPENMETRICS = "application/openmetrics-text";

    private final StreamOpsConfig config;
    private final IOpsJobRegistry jobRegistry;
    /** Optional lifecycle manager — present in multi-job coordinator processes. */
    private final OpsJobManager jobManager;

    private HttpServer httpServer;
    private PrometheusMeterRegistry prometheusRegistry;

    public StreamOpsHttpServer(StreamOpsConfig config, IOpsJobRegistry jobRegistry) {
        this(config, jobRegistry, null);
    }

    public StreamOpsHttpServer(StreamOpsConfig config, IOpsJobRegistry jobRegistry,
                               OpsJobManager jobManager) {
        this.config = config;
        this.jobRegistry = jobRegistry;
        this.jobManager = jobManager;
    }

    public synchronized void start() throws IOException {
        if (httpServer != null) {
            throw new IllegalStateException("StreamOpsHttpServer already started");
        }
        if (!config.isEnabled()) {
            // Explicit-off: never a silent no-op listener (plan guide #24).
            throw new IllegalStateException(
                    "StreamOpsHttpServer is disabled by config (" + StreamOpsConfig.KEY_ENABLED + "=false); "
                            + "refusing to start an empty server. Enable it or do not construct it.");
        }
        if (config.isMetricsEnabled()) {
            prometheusRegistry = new PrometheusMeterRegistry(
                    io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
            StreamMetricsRegistries.addMember(prometheusRegistry);
        }

        httpServer = HttpServer.create(new InetSocketAddress(config.getBindAddress(), config.getPort()), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "stream-ops-http");
            t.setDaemon(true);
            return t;
        }));
        httpServer.createContext("/metrics", this::handleMetrics);
        httpServer.createContext("/jobs", this::handleJobs);
        // catch-all: unknown paths answer a structured 404 (the JDK server's
        // built-in 404 is opaque plain text)
        httpServer.createContext("/", ex -> sendError(ex, 404, "NOT_FOUND",
                "Unknown ops path: " + ex.getRequestURI().getPath()
                        + " (served paths: /metrics, /jobs, /jobs/{jobId}, "
                        + "/jobs/{jobId}/stop, /jobs/{jobId}/checkpoints, /jobs/{jobId}/threaddump)"));
        httpServer.start();
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (prometheusRegistry != null) {
            StreamMetricsRegistries.removeMember(prometheusRegistry);
            prometheusRegistry.close();
            prometheusRegistry = null;
        }
    }

    public boolean isRunning() {
        return httpServer != null;
    }

    /** Actual bound port (useful when configured with port 0 for tests). */
    public synchronized int getBoundPort() {
        return httpServer != null ? httpServer.getAddress().getPort() : -1;
    }

    // ==================== /metrics ====================

    private void handleMetrics(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED",
                        "Only GET is supported on /metrics");
                return;
            }
            if (prometheusRegistry == null) {
                // Explicit-off, not silent empty output.
                sendError(exchange, 404, "METRICS_DISABLED",
                        "Metrics exposure is disabled by config ("
                                + StreamOpsConfig.KEY_METRICS_ENABLED + "=false)");
                return;
            }
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            boolean openMetrics = accept != null && accept.contains(ACCEPT_OPENMETRICS);
            String body = openMetrics ? prometheusRegistry.scrape(accept) : prometheusRegistry.scrape();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    openMetrics ? CONTENT_TYPE_OPENMETRICS : CONTENT_TYPE_TEXT_004);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "INTERNAL_ERROR", String.valueOf(e));
        } finally {
            exchange.close();
        }
    }

    // ==================== /jobs ====================

    private void handleJobs(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            // /jobs — list (GET) or submit (POST)
            if ("/jobs".equals(path) || "/jobs/".equals(path)) {
                if ("GET".equals(method)) {
                    handleJobList(exchange);
                } else if ("POST".equals(method)) {
                    handleSubmit(exchange);
                } else {
                    sendError(exchange, 405, "METHOD_NOT_ALLOWED",
                            "/jobs supports GET (list) and POST (submit)");
                }
                return;
            }

            if (path.startsWith("/jobs/")) {
                String rest = path.substring("/jobs/".length());

                // POST /jobs/{jobId}/stop
                if (rest.endsWith("/stop")) {
                    if (!"POST".equals(method)) {
                        sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only POST is supported on stop");
                        return;
                    }
                    String jobId = rest.substring(0, rest.length() - "/stop".length());
                    handleStop(exchange, jobId);
                    return;
                }

                // GET /jobs/{jobId}/threaddump
                if (rest.endsWith("/threaddump")) {
                    if (!"GET".equals(method)) {
                        sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET is supported on threaddump");
                        return;
                    }
                    handleThreadDump(exchange);
                    return;
                }

                // GET /jobs/{jobId}/checkpoints
                if (rest.endsWith("/checkpoints")) {
                    if (!"GET".equals(method)) {
                        sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET is supported here");
                        return;
                    }
                    handleCheckpointQuery(exchange, rest.substring(0, rest.length() - "/checkpoints".length()));
                    return;
                }

                // GET /jobs/{jobId} — job detail
                if (!rest.contains("/") && "GET".equals(method)) {
                    handleJobDetail(exchange, rest);
                    return;
                }
            }

            sendError(exchange, 404, "NOT_FOUND", "Unknown ops path: " + path);
        } catch (Exception e) {
            sendError(exchange, 500, "INTERNAL_ERROR", String.valueOf(e));
        } finally {
            exchange.close();
        }
    }

    // ==================== lifecycle endpoints (P-REQ-5) ====================

    private void handleJobList(HttpExchange exchange) throws IOException {
        if (jobRegistry == null) {
            sendError(exchange, 503, "REGISTRY_UNAVAILABLE", "No job registry hosted in this process");
            return;
        }
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (String jobId : new java.util.TreeSet<>(jobRegistry.jobIds())) {
            io.nop.stream.runtime.coordinator.JobCoordinator c = jobRegistry.coordinator(jobId);
            if (c != null) {
                list.add(jobSummary(c));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobs", list);
        sendJson(exchange, 200, body);
    }

    private Map<String, Object> jobSummary(io.nop.stream.runtime.coordinator.JobCoordinator c) {
        io.nop.stream.runtime.coordinator.JobStatusResponse status = c.getJobStatus();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("jobId", c.getJobId());
        summary.put("jobStatus", status.getJobStatus() == null ? null : status.getJobStatus().name());
        summary.put("health", c.getHealth().name());
        summary.put("running", c.isRunning());
        summary.put("restartCount", c.getRestartCount());
        summary.put("fencingEpoch", c.getFencingEpoch());
        return summary;
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        if (jobManager == null) {
            sendError(exchange, 503, "SUBMIT_UNAVAILABLE",
                    "Job submission is not enabled on this endpoint (single-job launch mode)");
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JobSubmissionSpec spec;
        try {
            spec = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                    requestBody, JobSubmissionSpec.class);
        } catch (Exception e) {
            sendError(exchange, 400, "BAD_SUBMISSION_BODY",
                    "Failed to parse submission body as JobSubmissionSpec: " + e);
            return;
        }
        if (spec == null || spec.getJobId() == null || spec.getJobId().isBlank()) {
            sendError(exchange, 400, "BAD_SUBMISSION_BODY", "jobId is required");
            return;
        }
        try {
            io.nop.stream.runtime.coordinator.JobCoordinator coordinator = jobManager.submit(spec);
            Map<String, Object> body = jobSummary(coordinator);
            sendJson(exchange, 201, body);
        } catch (IllegalArgumentException | IllegalStateException e) {
            boolean duplicateJobId = e instanceof IllegalStateException
                    && String.valueOf(e).contains("already hosted");
            sendError(exchange, duplicateJobId ? 409 : 400, "SUBMIT_REJECTED", String.valueOf(e));
        }
    }

    private void handleStop(HttpExchange exchange, String jobId) throws IOException {
        if (jobManager == null) {
            sendError(exchange, 503, "STOP_UNAVAILABLE",
                    "Job lifecycle management is not enabled on this endpoint");
            return;
        }
        java.util.Map<String, java.util.List<String>> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String modeStr = query.getOrDefault("mode", java.util.List.of("CANCEL")).get(0);
        io.nop.stream.core.checkpoint.JobTerminationMode mode;
        try {
            mode = io.nop.stream.core.checkpoint.JobTerminationMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "BAD_STOP_MODE",
                    "Unknown stop mode '" + modeStr + "' (supported: CANCEL, DRAIN)");
            return;
        }
        try {
            io.nop.stream.runtime.coordinator.JobCoordinator coordinator = jobManager.stop(jobId, mode);
            if (coordinator == null) {
                sendError(exchange, 404, "JOB_NOT_FOUND", "Unknown jobId '" + jobId + "'");
                return;
            }
            sendJson(exchange, 200, jobSummary(coordinator));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "BAD_STOP_MODE", String.valueOf(e));
        } catch (IllegalStateException e) {
            // Item 16 (P-REQ-7): stop during an in-flight RECOVERING window —
            // the health machine rejects the terminal transition; retry after
            // the recovery completes (millisecond-scale window).
            sendError(exchange, 409, "JOB_STATE_CONFLICT", String.valueOf(e));
        }
    }

    private void handleJobDetail(HttpExchange exchange, String jobId) throws IOException {
        io.nop.stream.runtime.coordinator.JobCoordinator coordinator =
                jobRegistry != null ? jobRegistry.coordinator(jobId) : null;
        if (coordinator == null) {
            sendError(exchange, 404, "JOB_NOT_FOUND", "Unknown jobId '" + jobId + "'");
            return;
        }
        Map<String, Object> body = jobSummary(coordinator);
        io.nop.stream.runtime.coordinator.JobStatusResponse status = coordinator.getJobStatus();
        body.put("failureCause", status.getFailureCause());
        var snapshot = coordinator.getCheckpointCoordinator().getMetrics().snapshot();
        body.put("checkpointOverview", Map.of(
                "numCompletedCheckpoints", snapshot.getNumCompletedCheckpoints(),
                "numFailedCheckpoints", snapshot.getNumFailedCheckpoints(),
                "numAbortedCheckpoints", snapshot.getNumAbortedCheckpoints(),
                "latestCheckpointDuration", snapshot.getLatestCheckpointDuration()));
        sendJson(exchange, 200, body);
    }

    private void handleThreadDump(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (java.lang.management.ThreadInfo info :
                java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            sb.append(info.toString());
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static java.util.Map<String, java.util.List<String>> parseQuery(String rawQuery) {
        java.util.Map<String, java.util.List<String>> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(value);
        }
        return result;
    }

    private void handleCheckpointQuery(HttpExchange exchange, String jobId) throws IOException {
        io.nop.stream.runtime.coordinator.JobCoordinator coordinator =
                jobRegistry != null ? jobRegistry.coordinator(jobId) : null;
        if (coordinator == null) {
            sendError(exchange, 404, "JOB_NOT_FOUND",
                    "Unknown jobId '" + jobId + "' hosted in this coordinator process");
            return;
        }
        io.nop.stream.runtime.checkpoint.metrics.CheckpointMetricsSnapshot overview =
                coordinator.getCheckpointCoordinator().getMetrics().snapshot();
        var history = coordinator.getCheckpointCoordinator().getCheckpointHistory();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        Map<String, Object> overviewMap = new LinkedHashMap<>();
        overviewMap.put("numCompletedCheckpoints", overview.getNumCompletedCheckpoints());
        overviewMap.put("numFailedCheckpoints", overview.getNumFailedCheckpoints());
        overviewMap.put("numAbortedCheckpoints", overview.getNumAbortedCheckpoints());
        overviewMap.put("latestCheckpointSize", overview.getLatestCheckpointSize());
        overviewMap.put("latestCheckpointDuration", overview.getLatestCheckpointDuration());
        overviewMap.put("lastCheckpointTimestamp", overview.getLastCheckpointTimestamp());
        overviewMap.put("failureCause", overview.getFailureCause());
        body.put("overview", overviewMap);
        body.put("historySize", history.size());
        body.put("history", history);
        sendJson(exchange, 200, body);
    }

    // ==================== helpers ====================

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        String json = io.nop.core.lang.json.JsonTool.stringify(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String errorCode, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorCode);
        body.put("message", message);
        body.put("status", status);
        sendJson(exchange, status, body);
    }
}
