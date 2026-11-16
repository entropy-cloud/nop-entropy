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
            server.createContext("/v1/queue", ex -> safe(ex, err, () -> handleQueue(core, ex)));
            server.createContext("/v1/health", ex -> safe(ex, err, () -> handleHealth(core, ex)));
            server.createContext("/v1/job", ex -> safe(ex, err, () -> handleJob(core, ex)));
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
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : core.queueSnapshot())
            m.put(e.getKey(), e.getValue());
        reply(ex, 200, Map.of("lanes", m));
    }

    static void handleHealth(ServiceCore core, HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, Map.of("error", "GET only"));
            return;
        }
        reply(ex, 200, core.health());
    }

    // ---- helpers ---------------------------------------------------------

    /** payload parsed into a JSON object when possible (same contract as stdio serve). */
    static Map<String, Object> jobSnapshot(BuildJob job) {
        Map<String, Object> snap = job.snapshot();
        String payload = job.payload;
        if (payload != null) {
            Object parsed = JsonIn.silentParse(payload);
            snap.put("payload", parsed != null ? parsed : payload);
        }
        return snap;
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
