package io.nop.ib.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;
import io.nop.ib.service.BuildJob;
import io.nop.ib.service.ServiceCore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wave 10 HTTP access (design 07 IB-S2/S5): loopback-only JDK HttpServer.
 * Callers POST {@code /v1/run {"projectDir","cmd","args","wait"}} and get the
 * same JSON contract the CLI prints — the service core funnels everything
 * through {@code NopIbMain.run}, so there is no second execution semantics.
 * {@code wait=true} (default) awaits the job inside the handler; the handler
 * pool is fixed with a floor above the queue face so long builds never starve
 * the read endpoints (/v1/queue, /v1/health).
 */
public final class ServeHttp {
    public static final int DEFAULT_PORT = 8188;
    public static final int HANDLER_THREADS = 16;
    /** plan 2588: dedicated long-poll pool; read endpoints stay on the main pool. */
    public static final int LONG_POLL_THREADS = 4;
    /**
     * plan 2588 (user adjudication 2026-11-21): waitMs is minute-level by
     * default — absent = block up to 1 minute (AI tool calls default to a
     * 120s read timeout, so the server must return well inside it); the upper
     * clamp is 5 minutes, covering a typical check/compile in one blocking
     * call. waitMs=0 stays a pure snapshot. logTail clamp.
     */
    public static final long MAX_WAIT_MS = 300_000L;
    public static final long DEFAULT_WAIT_MS = 60_000L;
    public static final int MAX_LOG_TAIL = 500;

    private ServeHttp() {
    }

    public static int serve(ServiceCore core, int port, java.io.PrintStream out, java.io.PrintStream err) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
            ExecutorService pool = Executors.newFixedThreadPool(HANDLER_THREADS, r -> {
                Thread t = new Thread(r, "nop-ib-http");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(pool);
            // plan 2588: long polls wait OUTSIDE the main handler pool — a fixed
            // 4-thread pool bounded by a permit, so a full long-poll pool makes
            // /v1/job return an immediate snapshot instead of queueing, and the
            // read endpoints (health/jobs/queue) can never be starved
            ExecutorService longPoll = Executors.newFixedThreadPool(LONG_POLL_THREADS, r -> {
                Thread t = new Thread(r, "nop-ib-longpoll");
                t.setDaemon(true);
                return t;
            });
            java.util.concurrent.Semaphore longPollPermits = new java.util.concurrent.Semaphore(LONG_POLL_THREADS);
            server.createContext("/v1/run", ex -> safe(ex, err, () -> handleRun(core, ex)));
            server.createContext("/v1/jobs", ex -> safe(ex, err, () -> handleJobs(core, ex)));
            server.createContext("/v1/queue", ex -> safe(ex, err, () -> handleQueue(core, ex)));
            server.createContext("/v1/health", ex -> safe(ex, err, () -> handleHealth(core, ex)));
            server.createContext("/v1/job", ex -> {
                // long-poll hands the exchange to its own pool: this handler
                // returns before the response is written, so closing stays with
                // whoever actually replied
                try {
                    handleJob(core, ex, longPoll, longPollPermits);
                } catch (Exception e) {
                    err.println("http handler error: " + e);
                    try {
                        reply(ex, 500, Map.of("error", String.valueOf(e)));
                    } catch (IOException ignored) {
                        // headers already sent — nothing left to reply with
                    }
                    ex.close();
                }
            });
            server.createContext("/v1/help", ex -> safe(ex, err, () -> handleHelp(ex)));
            server.start();
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("event", "hello");
            hello.put("mode", "http");
            hello.put("port", port);
            hello.put("bind", "127.0.0.1");
            hello.put("health", "http://127.0.0.1:" + port + "/v1/health");
            out.println(JsonOut.write(hello));
            return Commands.EXIT_OK; // blocked in server.start() daemon threads
        } catch (IOException e) {
            err.println("serve --http failed to bind 127.0.0.1:" + port + ": " + e);
            return Commands.EXIT_FAILED;
        }
    }

    private interface Thunk {
        void run() throws IOException;
    }

    private static void safe(HttpExchange ex, java.io.PrintStream err, Thunk t) throws IOException {
        try {
            t.run();
        } catch (Exception e) {
            err.println("http handler error: " + e);
            reply(ex, 500, Map.of("error", String.valueOf(e)));
        } finally {
            ex.close();
        }
    }

    // ---- endpoints -------------------------------------------------------

    /**
     * GET /v1/help — machine-readable self-description so any AI/program can
     * integrate with zero prior knowledge (complements the packaged README).
     */
    static void handleHelp(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", "nop-ib");
        m.put("desc", "stateless incremental build & test service for Maven projects; "
                + "per-project serial lane + cross-project CPU-budgeted parallelism");
        m.put("run", Map.of(
                "method", "POST /v1/run",
                "body", Map.of("projectDir", "repo root | module dir | symlink (normalized to one lane)",
                        "cmd", "status|plan|compile|test|check|model|clean|mvn",
                        "args", "CLI arg list, e.g. [\"--paths\",\"src/Foo.java\",\"--json\"]",
                        "wait", "true (blocking) | false (returns job id)",
                        "clientRequestId", "optional idempotency key; a retry with the same "
                                + "(projectDir, clientRequestId) returns the existing job "
                                + "with replayed:true instead of queueing again")));
        m.put("endpoints", Map.of(
                "GET /v1/job/{id}", "job result: {state, exit, payload, stderr, logPath}; "
                        + "optional waitMs=N (absent=60s default; 0=pure snapshot; clamp 300000; "
                        + "long poll: returns HTTP 200 intermediate "
                        + "{id,state,phase:queued|running,queueAhead,elapsedMs,progress?,logPath,"
                        + "waitedMs} — never an error; loop until terminal) and logTail=N (0..500 "
                        + "lines of the job log)",
                "GET /v1/jobs?limit=N&state=S", "recent job summaries (exit/durationMs); "
                        + "state filters queued|running|done|failed|cancelled",
                "GET /v1/queue", "per-project lanes: {root, depth, runningCmd, pending}",
                "GET /v1/health", "service vitals",
                "DELETE /v1/job/{id}", "cancel a QUEUED or RUNNING job — running cancel kills "
                        + "the registered child process tree (descendants first) and marks the "
                        + "job cancelled (exit=-1); terminal jobs return 409 already-terminal"));
        m.put("exitCodes", Map.of("0", "ok", "1", "build/test failed", "3", "usage/config error",
                "6", "partial", "75", "fallback Maven process timeout"));
        m.put("semantics", List.of(
                "fallback.reason non-null = this round ran real Maven (and why)",
                "cached:true = test cache hit (tests did NOT execute); --force escapes",
                "compiled:true|false|\"skipped\" = test pre-compile state (--no-compile disables)",
                "tasks:[{name,kind:skipped|executed|failed}] = declared codegen task outcomes"));
        m.put("tasksDeclaration", Map.of(
                "file", "nop-ib.tasks.json (repo root or any module basedir; module-level same-name overrides root-level)",
                "shape", "[{name, trigger: before-compile|after-compile, inputs:[glob], outputs:[glob], "
                        + "exec:{type: java|xscript, mainClass, script?, arguments?, classpathScope: compile|runtime|test(default)}}]",
                "note", "codegen runs on the ib fast path; fingerprints live under target/nop-ib/tasks (mvn clean removes)"));
        m.put("onboarding", List.of(
                "cd <project root> && <dist>/bin/ib model refresh && <dist>/bin/ib model index",
                "then POST /v1/run with projectDir"));
        m.put("mvnPassthrough", "cmd=mvn, whitelist goals: install|package|clean|dependency:*|surefire:* "
                + "(same lane, exit code passthrough; others rejected with exit 3)");
        reply(ex, 200, m);
    }

    static void handleRun(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "POST only"));
            return;
        }
        String body;
        try {
            // plan 2583: read-side cap — readAllBytes on an unbounded body let a
            // single request OOM the service before any size check ran
            body = readBodyLimited(ex, ServiceCore.MAX_PAYLOAD_BYTES);
        } catch (IOException e) {
            // the exception text (message + class) IS the 413 diagnostic
            // payload — this handler has no logger parameter; the safe()
            // wrapper prints full traces for handler-level errors
            reply(ex, 413, Map.of("error", String.valueOf(e)));
            return;
        }
        Map<String, Object> req;
        try {
            req = JsonIn.readAsMap(body);
        } catch (Exception e) {
            reply(ex, 400, Map.of("error", "unparseable JSON body"));
            return;
        }
        String projectDir = str(req.get("projectDir"));
        String cmd = str(req.get("cmd"));
        if (projectDir == null || cmd == null) {
            reply(ex, 400, Map.of("error", "projectDir and cmd are required"));
            return;
        }
        List<String> args = new ArrayList<>();
        Object rawArgs = req.get("args");
        if (rawArgs instanceof List<?> l)
            for (Object o : l)
                args.add(String.valueOf(o));
        boolean wait = !Boolean.FALSE.equals(req.get("wait"));
        // plan 2588: optional idempotency key — AI-side timeout retries with the
        // same key hit the existing job instead of queueing a duplicate build
        String clientRequestId = str(req.get("clientRequestId"));

        BuildJob job;
        try {
            job = core.submit(projectDir, cmd, args, wait, clientRequestId);
        } catch (IllegalArgumentException e) {
            reply(ex, 400, Map.of("error", String.valueOf(e)));
            return;
        } catch (IllegalStateException e) {
            reply(ex, 429, Map.of("error", String.valueOf(e)));
            return;
        }
        if (wait)
            core.await(job);
        Map<String, Object> snap = jobSnapshot(job);
        if (job.replayed)
            snap.put("replayed", true);
        reply(ex, 200, snap);
    }

    /**
     * plan 2583: bounded body read — reads at most {@code limit + 1} bytes and
     * rejects with an explicit error when the limit is exceeded (HTTP caller
     * gets 413 from the handleRun guard).
     */
    static String readBodyLimited(HttpExchange ex, int limit) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(limit + 1);
        if (bytes.length > limit)
            throw new IOException("request body exceeds the "
                    + limit + " byte payload limit");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Overload kept for the stdio/test surface: no long polling. */
    static void handleJob(ServiceCore core, HttpExchange ex) throws IOException {
        handleJob(core, ex, null, null);
    }

    /**
     * plan 2588: GET /v1/job/{id}?waitMs=N&logTail=N — long poll with an
     * explicit intermediate state (design 09 §2) or a pure snapshot when
     * waitMs=0 is a pure snapshot; absent waits 60s; DELETE cancels QUEUED and RUNNING jobs.
     */
    static void handleJob(ServiceCore core, HttpExchange ex,
                          java.util.concurrent.ExecutorService longPoll,
                          java.util.concurrent.Semaphore longPollPermits) throws IOException {
        String path = ex.getRequestURI().getPath(); // /v1/job/{id}
        String id = path.substring(path.lastIndexOf('/') + 1);
        BuildJob job = core.job(id);
        if (job == null) {
            replyAndClose(ex, 404, Map.of("error", "unknown job: " + id));
            return;
        }
        String method = ex.getRequestMethod().toUpperCase(java.util.Locale.ROOT);
        if ("DELETE".equals(method)) {
            String result = core.cancel(id);
            if (!"cancelled".equals(result))
                replyAndClose(ex, 409, Map.of("result", result));
            else
                replyAndClose(ex, 200, Map.of("result", "cancelled", "state", "cancelled", "id", id));
            return;
        }
        if (!"GET".equals(method)) {
            replyAndClose(ex, 405, Map.of("error", "GET or DELETE only"));
            return;
        }
        // plan 2588 (user adjudication): absent waitMs = DEFAULT_WAIT_MS (1 min);
        // explicit waitMs=0 stays a pure snapshot; upper clamp 5 minutes
        long waitMs = DEFAULT_WAIT_MS;
        int logTail = 0;
        String query = ex.getRequestURI().getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("waitMs=")) {
                    try {
                        waitMs = Long.parseLong(pair.substring("waitMs=".length()));
                    } catch (NumberFormatException e) {
                        replyAndClose(ex, 400, Map.of("error", "waitMs must be an integer"));
                        return;
                    }
                } else if (pair.startsWith("logTail=")) {
                    try {
                        logTail = Integer.parseInt(pair.substring("logTail=".length()));
                    } catch (NumberFormatException e) {
                        replyAndClose(ex, 400, Map.of("error", "logTail must be an integer"));
                        return;
                    }
                } else if (!pair.isEmpty()) {
                    // closure gate: unknown query params are explicit 400s, never ignored
                    replyAndClose(ex, 400, Map.of("error", "unsupported query parameter: "
                            + pair + " (supported: waitMs, logTail)"));
                    return;
                }
            }
        }
        waitMs = Math.max(0, Math.min(waitMs, MAX_WAIT_MS));
        logTail = Math.max(0, Math.min(logTail, MAX_LOG_TAIL));
        final long fWaitMs = waitMs;
        final int fLogTail = logTail;

        if (fWaitMs <= 0 || job.isTerminal()) {
            // pure snapshot (waitMs=0 or a finished job)
            replyAndClose(ex, 200, snapshotWithTail(job, fLogTail));
            return;
        }
        // long poll: needs the dedicated pool; without it fall back to snapshot
        if (longPoll == null || longPollPermits == null) {
            replyAndClose(ex, 200, core.pendingSnapshot(job, 0));
            return;
        }
        if (!longPollPermits.tryAcquire()) {
            // pool saturated: return the immediate snapshot, never queue
            replyAndClose(ex, 200, core.pendingSnapshot(job, 0));
            return;
        }
        longPoll.execute(() -> {
            long t0 = System.currentTimeMillis();
            try {
                core.waitForChange(job, fWaitMs);
                long waited = System.currentTimeMillis() - t0;
                if (job.isTerminal())
                    replyAndClose(ex, 200, snapshotWithTail(job, fLogTail));
                else
                    replyAndClose(ex, 200, core.pendingSnapshot(job, waited));
            } catch (Exception e) {
                try {
                    reply(ex, 500, Map.of("error", String.valueOf(e)));
                } catch (IOException ignored) {
                    // client gone
                }
                ex.close();
            } finally {
                longPollPermits.release();
            }
        });
    }

    /** terminal snapshot plus the requested log tail (≤ {@link #MAX_LOG_TAIL}). */
    private static Map<String, Object> snapshotWithTail(BuildJob job, int logTail) {
        Map<String, Object> snap = jobSnapshot(job);
        if (logTail > 0 && job.logFile != null) {
            String tail = logTail(job.logFile, logTail);
            if (tail != null)
                snap.put("logTail", tail);
        }
        return snap;
    }

    /** last N lines of the job log, or null when the file is absent/unreadable. */
    static String logTail(java.nio.file.Path file, int lines) {
        try {
            List<String> all = java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            return String.join("\n", all.subList(from, all.size()));
        } catch (IOException e) {
            return null;
        }
    }

    static void handleQueue(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        // wave 11: each lane is an object {root, depth, runningCmd, pending}
        reply(ex, 200, Map.of("lanes", core.queueLanes()));
    }

    /** wave 11 observability: GET /v1/jobs?limit=N&state=S — recent job summaries. */
    static void handleJobs(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        int limit = ServiceCore.DEFAULT_JOBS_LIMIT;
        String state = null;
        java.util.Set<String> allowed = java.util.Set.of("queued", "running", "done", "failed", "cancelled");
        String query = ex.getRequestURI().getRawQuery();
        if (query != null)
            for (String pair : query.split("&")) {
                if (pair.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(pair.substring("limit=".length()));
                    } catch (NumberFormatException e) {
                        reply(ex, 400, Map.of("error", "limit must be an integer"));
                        return;
                    }
                } else if (pair.startsWith("state=")) {
                    state = pair.substring("state=".length());
                    if (!allowed.contains(state)) {
                        reply(ex, 400, Map.of("error",
                                "state must be one of " + allowed));
                        return;
                    }
                } else if (!pair.isEmpty()) {
                    reply(ex, 400, Map.of("error", "unsupported query parameter: "
                            + pair + " (supported: limit, state)"));
                    return;
                }
            }
        reply(ex, 200, Map.of("jobs", core.recentJobs(limit, state)));
    }

    static void handleHealth(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        reply(ex, 200, core.health());
    }

    // ---- helpers ---------------------------------------------------------

    /**
     * payload parsed into JSON when possible (same contract as stdio serve).
     * CLI output is NDJSON (plan line + per-module report lines): single line
     * -> object, multiple lines -> array of objects, unparseable -> raw string.
     */
    static Map<String, Object> jobSnapshot(BuildJob job) {
        Map<String, Object> snap = job.snapshot();
        String payload = job.payload;
        if (payload != null) {
            Object parsed = parseCliOutput(payload);
            snap.put("payload", parsed != null ? parsed : payload);
        }
        if (job.stderr != null)
            snap.put("stderr", job.stderr);
        return snap;
    }

    private static Object parseCliOutput(String payload) {
        // stdout carries non-JSON progress lines too ("[nop-ib] log: ..."):
        // drop bracketed progress lines before the sequence parse
        StringBuilder json = new StringBuilder(payload.length());
        for (String line : payload.split("\n")) {
            if (line.startsWith("[nop-ib]"))
                continue;
            json.append(line).append('\n');
        }
        return JsonIn.silentParseSequence(json.toString());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static void reply(HttpExchange ex, int code, Map<String, Object> body) throws IOException {
        byte[] bytes = JsonOut.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** reply + close for /v1/job, whose handler owns the exchange lifecycle. */
    private static void replyAndClose(HttpExchange ex, int code, Map<String, Object> body) throws IOException {
        reply(ex, code, body);
        ex.close();
    }
}
