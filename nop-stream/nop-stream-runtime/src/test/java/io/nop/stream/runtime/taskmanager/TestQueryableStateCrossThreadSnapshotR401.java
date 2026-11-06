/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.taskmanager;

import com.sun.net.httpserver.HttpServer;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.operators.ProcessOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.ops.StreamOpsConfig;
import io.nop.stream.runtime.ops.StreamOpsHttpServer;
import io.nop.stream.runtime.testsupport.TestAwait;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01 red⑤ driver (finding 1.1-C3-009 / C10-014; plan 2534 ruling D.2,
 * endpoint-level per review R1 F-5 — runtime placement, core cannot back-reference):
 * a keyed task keeps writing state on its task thread while another thread hammers
 * {@code GET /jobs/{id}/vertices/{v}/subtasks/{i}/state}. Every answer must be
 * self-consistent (200 with keyed state) or typed rejection (503 state-query timeout /
 * 404 finished task) — <b>never a 500</b>. Pre-fix the REST read snapshotted the keyed
 * backend directly on the HTTP worker thread: the concurrent write hammer escaped
 * {@code ConcurrentModificationException} as 500 INTERNAL_ERROR.
 *
 * <p>Post-fix the snapshot is handed off to the task thread via the mailbox
 * (Mail.control → TaskMailbox.put → drained at the SOURCE collect emission point) and
 * the HTTP thread bounded-waits on a future: same-thread-as-writes snapshot semantics,
 * torn snapshots impossible.
 */
class TestQueryableStateCrossThreadSnapshotR401 {

    private static final String NODE_ID = "qs-node";
    private static final String ENDPOINT = "embedded:qs";

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

    static final class RecordingClusterRegistry implements ClusterRegistry {
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
                    NODE_ID, ENDPOINT, 4, System.currentTimeMillis(), System.currentTimeMillis()));
        }
        @Override public void assignTask(String jobId, String vertexId, int subtaskIndex,
                                          String nodeId, String attemptId, long fencingEpoch,
                                          int attemptNumber) {}
        @Override public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) { return null; }
        @Override public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return List.of();
        }
        @Override public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    /**
     * Full-speed bounded churn source: emits {@code records} NEW keys back to back
     * (no sleep) — every collect() is a task-thread emission point where control
     * mails (the snapshot handoff) drain, and every new key structurally grows the
     * keyed-value storage map (the pre-fix concurrent-snapshot corruption surface).
     * After the burst the source PARKS (task stays alive and unfinished) until the
     * test releases it, so the whole query window observes a RUNNING task.
     */
    static final class KeyChurnSource implements SourceFunction<String> {
        private final int records;
        private final CountDownLatch park;
        private volatile boolean canceled;

        KeyChurnSource(int records, CountDownLatch park) {
            this.records = records;
            this.park = park;
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            for (int i = 0; i < records && !canceled; i++) {
                ctx.collect("k" + i);
            }
            // park INSIDE user code (not at an emission point): the task stays
            // alive-but-stuck — post-fix state queries against it answer typed 503
            park.await(30, TimeUnit.SECONDS);
        }

        @Override
        public void cancel() {
            canceled = true;
            park.countDown();
        }
    }

    /** Keyed process function: one ValueState entry per arriving key (structural churn). */
    static class KeyedWriter extends io.nop.stream.core.common.functions.ProcessFunction<String, String> {
        transient ValueState<String> seen;

        @Override
        public void processElement(String value, Context ctx,
                                   io.nop.stream.core.util.Collector<String> out) throws Exception {
            if (seen == null) {
                seen = getRuntimeContext().getKeyedStateStore()
                        .getState(new ValueStateDescriptor<>("ks", String.class));
            }
            seen.update(value);
            out.collect(value);
        }
    }

    /** Task stuck inside user code (NOT at a collect emission point): no mail drain. */
    static final class BlockedSource implements SourceFunction<String> {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean canceled;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            ctx.collect("only");
            started.countDown();
            // blocks off the emission point: the mailbox is NOT drained while parked here
            release.await(60, TimeUnit.SECONDS);
        }

        @Override
        public void cancel() {
            canceled = true;
            release.countDown();
        }
    }

    /**
     * ProcessOperator variant that sets the CURRENT KEY from the record value — the
     * direct-built chain has no keyBy upstream (KeyExtractingOutput), so without this
     * every write lands on one null-key storage entry (no structural churn).
     */
    static final class KeyedChurnProcessOperator extends ProcessOperator<String, String> {
        KeyedChurnProcessOperator(KeyedWriter function) {
            super(function);
        }

        @Override
        public void processElement(io.nop.stream.core.streamrecord.StreamRecord<String> element) throws Exception {
            setCurrentKey(element.getValue());
            super.processElement(element);
        }
    }

    private static StreamTaskInvokable keyedInvokable(SourceFunction<String> source) {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        ProcessOperator<String, String> process = new KeyedChurnProcessOperator(new KeyedWriter());
        StreamSinkOperator<String> sink = new StreamSinkOperator<>(
                (SinkFunction<String>) v -> { });
        OperatorChain chain = new OperatorChain(java.util.Arrays.asList(sourceOp, process, sink));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        // provision the keyed backend exactly as open() does: the backend must be set
        // on the operator BEFORE open() creates the keyed backend from it
        for (StreamOperator<?> op : chain.getOperators()) {
            if (op instanceof ProcessOperator) {
                @SuppressWarnings("unchecked")
                ProcessOperator<String, String> proc = (ProcessOperator<String, String>) op;
                proc.setStateBackend(new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend());
            }
        }
        return invokable;
    }

    private static StreamTaskInvokable finiteKeyedInvokable() {
        return keyedInvokable(new io.nop.stream.runtime.source.CollectionReplayableSource<>(
                java.util.Arrays.asList("a", "b")));
    }

    /**
     * red⑤ main driver: concurrent keyed writes × REST query hammer against a
     * RUNNING task. Every answer must be a consistent 200 or a typed 503 — a 404
     * TASK_NOT_FOUND for the live task is a WRONG answer. Pre-fix the concurrent
     * snapshot corruption (CME inside the storage-map iteration) escaped
     * {@code invokable.keyedStateSnapshot()} as a wrapped StreamException and the
     * pre-fix handler misclassified EVERY StreamException as TASK_NOT_FOUND →
     * 404s for a live, running task (execution-stage correction of the audit's
     * "escapes as 500": the live wrap layer turns it into a false 404).
     * Post-fix: snapshot runs on the task thread (mailbox handoff) — 200s while
     * writing, typed 503 while the task parks off an emission point, zero false
     * 404s, zero 500s.
     */
    @Test
    void concurrentKeyedWritesAndRestQueriesNeverYield500() throws Exception {
        TaskManager tm = new TaskManager(NODE_ID, ENDPOINT, 2,
                new LocalMessageService(), new RecordingClusterRegistry(), "qs-control-topic");
        tm.start();
        closeables.add(tm::stop);

        CountDownLatch park = new CountDownLatch(1);
        tm.updateFencingToken(1L);
        tm.receiveAssignment(new TaskAssignment(
                "job-r5", "v1", 0, NODE_ID, "attempt-1", 1L, System.currentTimeMillis()));
        KeyChurnSource source = new KeyChurnSource(300_000, park);
        StreamTaskInvokable invokable = keyedInvokable(source);
        tm.installInvokable("job-r5", "v1", 0, invokable, 1L);

        // the task thread must actually be running and writing before the hammer starts
        TestAwait.until("r401 keyed task is deployed and running",
                () -> tm.getRunningTaskCount() == 1);

        StreamOpsConfig opsConfig = new StreamOpsConfig();
        opsConfig.setEnabled(true);
        opsConfig.setPort(0);
        opsConfig.setBindAddress("127.0.0.1");
        StreamOpsHttpServer opsServer = new StreamOpsHttpServer(opsConfig, null, null, tm);
        opsServer.start();
        int port = opsServer.getBoundPort();
        closeables.add(opsServer::stop);

        String url = "http://127.0.0.1:" + port + "/jobs/job-r5/vertices/v1/subtasks/0/state";

        AtomicInteger ok200 = new AtomicInteger();
        AtomicInteger notFound404 = new AtomicInteger();
        AtomicInteger timeout503 = new AtomicInteger();
        AtomicInteger server500 = new AtomicInteger();
        AtomicInteger transportErrors = new AtomicInteger();
        CopyOnWriteArrayList<String> failureBodies = new CopyOnWriteArrayList<>();
        AtomicBoolean hammerStop = new AtomicBoolean(false);

        Thread hammer = new Thread(() -> {
            while (!hammerStop.get()) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(30_000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        ok200.incrementAndGet();
                    } else if (code == 404) {
                        if (notFound404.get() == 0) {
                            String body = readBody(conn);
                            System.out.println("[r401-hammer] first404head="
                                    + body.substring(0, Math.min(400, body.length())));
                        }
                        notFound404.incrementAndGet();
                    } else if (code == 503) {
                        timeout503.incrementAndGet();
                    } else {
                        server500.incrementAndGet();
                        failureBodies.add(readBody(conn));
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    transportErrors.incrementAndGet();
                    failureBodies.add("transport: " + e);
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "r401-state-query-hammer");
        hammer.start();

        // overlap window: ~1.5s of concurrent writes × queries (hundreds of queries)
        Thread.sleep(1500);
        hammerStop.set(true);
        hammer.join(30_000);
        System.out.println("[r401-hammer] ok200=" + ok200.get() + " notFound404=" + notFound404.get()
                + " timeout503=" + timeout503.get() + " server500=" + server500.get()
                + " transport=" + transportErrors.get()
                + " firstFailureBody=" + (failureBodies.isEmpty() ? "<none>" : failureBodies.get(0)));

        // the task must STILL be alive (parked) at window end — every in-window
        // answer was observed against a RUNNING task
        RunningTask taskAtWindowEnd = tm.runningTasks.get("job-r5/v1/0");
        assertNotNull(taskAtWindowEnd, "the parked task must still be registered");
        assertTrue(!taskAtWindowEnd.isFinished(),
                "precondition: the task must be unfinished throughout the query window");

        assertTrue(ok200.get() > 0,
                "a running keyed task must serve live snapshots (200s): ok=" + ok200.get()
                        + " 404=" + notFound404.get() + " 503=" + timeout503.get()
                        + " 500=" + server500.get() + " transport=" + transportErrors.get()
                        + " failures=" + failureBodies);
        assertEquals(0, notFound404.get(),
                "a RUNNING task must never answer TASK_NOT_FOUND (the pre-fix defect surface: "
                        + "concurrent-snapshot failures misclassified as task-not-found): "
                        + "ok=" + ok200.get() + " 404=" + notFound404.get()
                        + " 500=" + server500.get() + " transport=" + transportErrors.get()
                        + " failures=" + failureBodies);
        assertEquals(0, server500.get(),
                "the state endpoint must never answer 500 under concurrent writes; failures=" + failureBodies);
        assertEquals(0, transportErrors.get(),
                "no transport-level failures allowed; failures=" + failureBodies);
    }

    /**
     * Finished-task pre-filter (ruling D.2.4): a NATURALLY COMPLETED bounded task
     * retains its registry entry (post-finish commit notifications) — but its
     * snapshot must answer typed 404 (TASK_NOT_FOUND), not a stale direct read.
     * Pre-fix the retained entry answered 200 from the HTTP worker thread.
     */
    @Test
    void finishedTaskAnswersTyped404() throws Exception {
        TaskManager tm = new TaskManager(NODE_ID, ENDPOINT, 2,
                new LocalMessageService(), new RecordingClusterRegistry(), "qs-control-topic");
        tm.start();
        closeables.add(tm::stop);

        tm.updateFencingToken(1L);
        tm.receiveAssignment(new TaskAssignment(
                "job-r5f", "v1", 0, NODE_ID, "attempt-1", 1L, System.currentTimeMillis()));
        StreamTaskInvokable invokable = finiteKeyedInvokable();
        tm.installInvokable("job-r5f", "v1", 0, invokable, 1L);

        // wait for natural completion: the bounded task retains its registry entry
        TestAwait.until("r401 bounded keyed task finished (retained entry)",
                () -> {
                    RunningTask t = tm.runningTasks.get("job-r5f/v1/0");
                    return t != null && t.isFinished();
                });

        StreamOpsConfig opsConfig = new StreamOpsConfig();
        opsConfig.setEnabled(true);
        opsConfig.setPort(0);
        opsConfig.setBindAddress("127.0.0.1");
        StreamOpsHttpServer opsServer = new StreamOpsHttpServer(opsConfig, null, null, tm);
        opsServer.start();
        int port = opsServer.getBoundPort();
        closeables.add(opsServer::stop);

        int code = statusOnly(
                "http://127.0.0.1:" + port + "/jobs/job-r5f/vertices/v1/subtasks/0/state");
        assertEquals(404, code,
                "a finished (retained-entry) task must answer typed TASK_NOT_FOUND "
                        + "(ruling D.2.4 steady-state pre-filter)");
    }

    /**
     * Timeout branch (ruling D.2.3/4): a task stuck INSIDE user code cannot drain the
     * handoff mail — the bounded wait answers typed 503 (STATE_QUERY_TIMEOUT), not a
     * torn cross-thread snapshot and not a hang.
     */
    @Test
    void stateQueryTimeoutAnswersTyped503() throws Exception {
        TaskManager tm = new TaskManager(NODE_ID, ENDPOINT, 2,
                new LocalMessageService(), new RecordingClusterRegistry(), "qs-control-topic");
        tm.start();
        closeables.add(tm::stop);

        long savedTimeout = TaskManager.stateQueryTimeoutMs;
        TaskManager.stateQueryTimeoutMs = 300L;
        try {
            tm.updateFencingToken(1L);
            tm.receiveAssignment(new TaskAssignment(
                    "job-r5t", "v1", 0, NODE_ID, "attempt-1", 1L, System.currentTimeMillis()));
            BlockedSource source = new BlockedSource();
            StreamTaskInvokable invokable = keyedInvokable(source);
            tm.installInvokable("job-r5t", "v1", 0, invokable, 1L);
            assertTrue(source.started.await(10, TimeUnit.SECONDS),
                    "the task thread must be parked inside user code");

            StreamOpsConfig opsConfig = new StreamOpsConfig();
            opsConfig.setEnabled(true);
            opsConfig.setPort(0);
            opsConfig.setBindAddress("127.0.0.1");
            StreamOpsHttpServer opsServer = new StreamOpsHttpServer(opsConfig, null, null, tm);
            opsServer.start();
            int port = opsServer.getBoundPort();
            closeables.add(opsServer::stop);

            long start = System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/jobs/job-r5t/vertices/v1/subtasks/0/state")
                    .toURL().openConnection();
            conn.setReadTimeout(30_000);
            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;
            String body = readBody(conn);
            conn.disconnect();

            assertEquals(503, code, "stuck-task query must answer typed 503, body=" + body);
            assertTrue(body.contains("STATE_QUERY_TIMEOUT"),
                    "the 503 must carry the typed state-query-timeout code, body=" + body);
            assertTrue(elapsed >= 250 && elapsed < 15_000,
                    "the bounded wait must actually bound the wait: elapsed=" + elapsed + "ms");
        } finally {
            TaskManager.stateQueryTimeoutMs = savedTimeout;
        }
    }

    private static String readBody(HttpURLConnection conn) {
        try {
            InputStream in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return "";
            }
            try (InputStream stream = in) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "<unreadable: " + e + ">";
        }
    }

    private static int statusOnly(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setReadTimeout(30_000);
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }
}
