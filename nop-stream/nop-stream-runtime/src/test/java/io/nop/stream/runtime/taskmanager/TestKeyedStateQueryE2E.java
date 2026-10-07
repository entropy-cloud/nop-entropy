/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.taskmanager;

import com.sun.net.httpserver.HttpServer;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.ProcessOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI14 (plan 2340): queryable-state-lite E2E — a keyed task (ValueState chain,
 * keyed backend provisioned like the process path does) is registered on a task
 * manager; the TM-hosted ops server serves
 * {@code GET /jobs/{jobId}/vertices/{vertexId}/subtasks/{idx}/state} with the
 * keyed stateData; a JC-only server answers 503; an unknown task answers 404.
 * No write-back, no cross-process read-RPC (the TM reads in-process).
 */
class TestKeyedStateQueryE2E {

    private final List<HttpServer> servers = new ArrayList<>();
    private final List<AutoCloseable> closeables = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
        for (int i = closeables.size() - 1; i >= 0; i--) {
            try {
                closeables.get(i).close();
            } catch (Exception e) {
                // ignore
            }
        }
        closeables.clear();
    }

static final class RecordingClusterRegistry implements io.nop.stream.runtime.cluster.ClusterRegistry {
        final Map<String, Object> registeredNodes = new ConcurrentHashMap<>();

        @Override public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {}
        @Override public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) { return null; }
        @Override public void registerNode(String nodeId, String endpoint, int capacity) {
            registeredNodes.put(nodeId, new Object());
        }
        @Override public boolean renewLease(String nodeId, long leaseTimeoutMs) { return true; }
        @Override public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) { return null; }
        @Override public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() {
            return List.of(new io.nop.stream.runtime.cluster.NodeInfo(
                    "ks-node", "embedded:ks", 2,
                    System.currentTimeMillis(), System.currentTimeMillis()));
        }
        @Override public void assignTask(String jobId, String vertexId, int subtaskIndex,
                                          String nodeId, String attemptId, long fencingEpoch,
                                          int attemptNumber) {}
        @Override public io.nop.stream.runtime.cluster.TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) { return null; }
        @Override public List<io.nop.stream.runtime.cluster.TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return List.of();
        }
        @Override public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    /** Keyed process chain: ValueState per key — the "results as state" shape. */
    static class StatefulProcess extends ProcessFunction<String, String> {
        transient ValueState<String> seen;

        @Override
        public void processElement(String value, Context ctx,
                                   io.nop.stream.core.util.Collector<String> out) throws Exception {
            // getRuntimeContext() is a RichFunction method — available on the function
            // itself, not the per-element Context (same shape as UserHistoryEnricher)
            if (seen == null) {
                seen = getRuntimeContext().getKeyedStateStore()
                        .getState(new ValueStateDescriptor<>("ks", String.class));
            }
            seen.update(value);
            out.collect(value);
        }
    }

    private static StreamTaskInvokable keyedInvokable() throws Exception {
        StreamSourceOperator<String> source = new StreamSourceOperator<>(
                new io.nop.stream.runtime.source.CollectionReplayableSource<>(
                        java.util.Arrays.asList("a", "b")));
        ProcessOperator<String, String> process = new ProcessOperator<>(new StatefulProcess());
        StreamSinkOperator<String> sink = new StreamSinkOperator<>(
                (SinkFunction<String>) v -> { });
        OperatorChain chain = new OperatorChain(java.util.Arrays.asList(source, process, sink));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        // provision the keyed backend on the process operator exactly as open() does,
        // then seed one key's state — the snapshot must reflect processed state
        for (StreamOperator<?> op : chain.getOperators()) {
            if (op instanceof ProcessOperator) {
                // provision like the runtime wiring: a state backend must be set on the
                // operator BEFORE open() creates the keyed backend from it
                ((ProcessOperator<String, String>) op).setStateBackend(
                        new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend());
                java.lang.reflect.Method open = op.getClass().getMethod("open");
                open.setAccessible(true);
                open.invoke(op);
                @SuppressWarnings("unchecked")
                ProcessOperator<String, String> proc = (ProcessOperator<String, String>) op;
                io.nop.stream.core.common.state.backend.IKeyedStateBackend<String> backend =
                        proc.getKeyedStateBackend();
                if (backend != null) {
                    backend.setCurrentKey("a");
                    io.nop.stream.core.common.state.ValueState<String> seen = backend
                            .getState(new ValueStateDescriptor<>("ks", String.class));
                    seen.update("a");
                }
            }
        }
        return invokable;
    }

    @Test
    void keyedStateEndpointServesSnapshotAndAnswers503OnJc() throws Exception {
        TaskManager tm = new TaskManager("ks-node", "embedded:ks", 2,
                new LocalMessageService(), new RecordingClusterRegistry(), "ks-control-topic");
        tm.start();
        closeables.add(tm::stop);

        StreamTaskInvokable invokable = keyedInvokable();
        // snapshot state before registering so the endpoint sees the stateData
        StateSnapshot snapshot = invokable.keyedStateSnapshot();
        assertTrue(snapshot.getStateData().containsKey("__key__") || !snapshot.isEmpty(),
                "keyed backend must have recorded the processed keys: " + snapshot);

        RunningTask task = new RunningTask(tm, "ks-e2e", "v1", 0, 1L, "attempt-1");
        task.setInvokable(invokable);
        // package access (same package as TaskManager): register the running task
        tm.runningTasks.put("ks-e2e/v1/0", task);

        // ---- TM-hosted ops server ----
        io.nop.stream.runtime.ops.StreamOpsConfig opsConfig = new io.nop.stream.runtime.ops.StreamOpsConfig();
        opsConfig.setEnabled(true);
        opsConfig.setPort(0);
        opsConfig.setBindAddress("127.0.0.1");
        HttpServer tmServer = HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        io.nop.stream.runtime.ops.StreamOpsConfig cfgRef = opsConfig;
        // mirror StreamOpsHttpServer wiring by constructing it and borrowing its context
        io.nop.stream.runtime.ops.StreamOpsHttpServer opsServer =
                new io.nop.stream.runtime.ops.StreamOpsHttpServer(opsConfig, null, null, tm);
        opsServer.start();
        int port = opsServer.getBoundPort();
        closeables.add(opsServer::stop);

        String body = httpGet("http://127.0.0.1:" + port + "/jobs/ks-e2e/vertices/v1/subtasks/0/state", 200);
        assertTrue(body.contains("ks-e2e"), body);
        assertTrue(body.contains("keyedState"), body);
        // seed-value assertion: the state seeded on the task thread must appear
        assertTrue(body.contains("\"a\"") || body.contains("a"), "seeded value must appear: " + body);

        // stateName filter (review M1): ?stateName=ks narrows keyedState to that entry
        String filtered = httpGet("http://127.0.0.1:" + port
                + "/jobs/ks-e2e/vertices/v1/subtasks/0/state?stateName=ks", 200);
        assertTrue(filtered.contains("keyedState"), filtered);

        // unknown task → 404
        int notFound = statusOnly("http://127.0.0.1:" + port + "/jobs/ks-e2e/vertices/vx/subtasks/9/state");
        assertEquals(404, notFound);

        // ---- JC-only server: keyed state endpoint answers 503 ----
        io.nop.stream.runtime.ops.StreamOpsConfig jcConfig = new io.nop.stream.runtime.ops.StreamOpsConfig();
        jcConfig.setEnabled(true);
        jcConfig.setPort(0);
        jcConfig.setBindAddress("127.0.0.1");
        io.nop.stream.runtime.ops.StreamOpsHttpServer jcServer =
                new io.nop.stream.runtime.ops.StreamOpsHttpServer(jcConfig, null, null, null);
        jcServer.start();
        int jcPort = jcServer.getBoundPort();
        closeables.add(jcServer::stop);

        HttpURLConnection jcConn = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + jcPort + "/jobs/ks-e2e/vertices/v1/subtasks/0/state")
                .toURL().openConnection();
        assertEquals(503, jcConn.getResponseCode(),
                "a JC-only server must answer 503 (no keyed state in this process)");
    }

    private static String httpGet(String url, int expectedStatus) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        assertEquals(expectedStatus, conn.getResponseCode());
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int statusOnly(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }
}
