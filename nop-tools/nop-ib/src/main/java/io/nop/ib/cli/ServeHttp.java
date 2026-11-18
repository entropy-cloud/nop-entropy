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
            server.createContext("/v1/run", ex -> safe(ex, err, () -> handleRun(core, ex)));
            server.createContext("/v1/jobs", ex -> safe(ex, err, () -> handleJobs(core, ex)));
            server.createContext("/v1/queue", ex -> safe(ex, err, () -> handleQueue(core, ex)));
            server.createContext("/v1/health", ex -> safe(ex, err, () -> handleHealth(core, ex)));
            server.createContext("/v1/job", ex -> safe(ex, err, () -> handleJob(core, ex)));
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
                        "wait", "true (blocking) | false (returns job id)")));
        m.put("endpoints", Map.of(
                "GET /v1/job/{id}", "job result: {state, exit, payload, stderr}",
                "GET /v1/jobs?limit=N", "recent job summaries (exit/durationMs)",
                "GET /v1/queue", "per-project lanes: {root, depth, runningCmd, pending}",
                "GET /v1/health", "service vitals",
                "DELETE /v1/job/{id}", "cancel a QUEUED job (running jobs cannot cancel)"));
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
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

        BuildJob job;
        try {
            job = core.submit(projectDir, cmd, args, wait);
        } catch (IllegalArgumentException e) {
            reply(ex, 400, Map.of("error", String.valueOf(e)));
            return;
        } catch (IllegalStateException e) {
            reply(ex, 429, Map.of("error", String.valueOf(e)));
            return;
        }
        if (wait)
            core.await(job);
        reply(ex, 200, jobSnapshot(job));
    }

    static void handleJob(ServiceCore core, HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(); // /v1/job/{id}
        String id = path.substring(path.lastIndexOf('/') + 1);
        BuildJob job = core.job(id);
        if (job == null) {
            reply(ex, 404, Map.of("error", "unknown job: " + id));
            return;
        }
        switch (ex.getRequestMethod().toUpperCase(java.util.Locale.ROOT)) {
            case "GET" -> reply(ex, 200, jobSnapshot(job));
            case "DELETE" -> {
                String result = core.cancel(id);
                if (!"cancelled".equals(result))
                    reply(ex, 409, Map.of("result", result));
                else
                    reply(ex, 200, Map.of("result", "cancelled", "id", id));
            }
            default -> reply(ex, 405, Map.of("error", "GET or DELETE only"));
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

    /** wave 11 observability: GET /v1/jobs?limit=N — recent job summaries. */
    static void handleJobs(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        int limit = ServiceCore.DEFAULT_JOBS_LIMIT;
        String query = ex.getRequestURI().getRawQuery();
        if (query != null)
            for (String pair : query.split("&"))
                if (pair.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(pair.substring("limit=".length()));
                    } catch (NumberFormatException e) {
                        reply(ex, 400, Map.of("error", "limit must be an integer"));
                        return;
                    }
                }
        reply(ex, 200, Map.of("jobs", core.recentJobs(limit)));
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
}
