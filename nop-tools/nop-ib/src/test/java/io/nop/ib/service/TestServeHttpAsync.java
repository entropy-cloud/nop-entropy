package io.nop.ib.service;

import io.nop.ib.cli.ServeHttp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2588 end-to-end HTTP pins (design 09 §2): async submit returns a taskId
 * immediately, the long-poll waitMs path answers the explicit intermediate
 * state on a queued job and the terminal payload after completion, the job log
 * is reachable via logTail, the idempotency key survives the HTTP surface,
 * state= filters /v1/jobs, and a saturated long-poll pool never starves
 * /v1/health (read endpoints stay on the main handler pool). Every test
 * manages the lane gate itself so method order cannot deadlock the suite.
 */
class TestServeHttpAsync {
    @org.junit.jupiter.api.BeforeAll
    static void isolateRootsLedger() throws Exception {
        System.setProperty("nop.ib.rootsLedger",
                java.nio.file.Files.createTempDirectory("ib-roots-ledger").resolve("roots.txt").toString());
    }

    private static Path repo;
    private static ServiceCore core;
    private static int port;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    /** lane gate: while held, every cli invocation parks (jobs stay queued/running). */
    private static final Object GATE = new Object();
    private static volatile boolean holdLane = false;

    private static void holdLane(boolean hold) {
        synchronized (GATE) {
            holdLane = hold;
            GATE.notifyAll();
        }
    }

    @BeforeAll
    static void start() throws Exception {
        repo = Files.createTempDirectory("ib-http-async");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of());
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        Process p = new ProcessBuilder("git", "init", "-q").directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "config", "user.email", "t@t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "add", "-A").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-qm", "init").directory(repo.toFile()).start().waitFor();

        core = new ServiceCore(4);
        core.cli = (args, engine, root, out, err, budget) -> {
            // park while the lane is held (the running job keeps the lane busy)
            synchronized (GATE) {
                while (holdLane) {
                    try {
                        GATE.wait(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return 130;
                    }
                }
            }
            out.println("[nop-ib] cli stdout marker for the job log");
            return 0;
        };
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        ServeHttp.serve(core, port, System.out, System.err);
    }

    @AfterAll
    static void stop() {
        holdLane(false);
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60)).GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** submit(wait=false) returns a taskId immediately; long poll on a queued job returns the intermediate state. */
    @Test
    void longPollOnQueuedJobReturnsIntermediateState() throws Exception {
        holdLane(true); // first submitted job parks RUNNING, the next two queue
        HttpResponse<String> head = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":false,\"clientRequestId\":\"lp-head\"}");
        assertEquals(200, head.statusCode());
        long t0 = System.currentTimeMillis();
        HttpResponse<String> res = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":false,\"clientRequestId\":\"lp-1\"}");
        assertTrue(System.currentTimeMillis() - t0 < 1000, "async submit returns in <1s");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        String id = (String) body.get("id");
        assertNotNull(id);
        // distinct args: identical submissions coalesce onto the QUEUED job
        // (coalescing contract), and this test needs two real queue entries
        HttpResponse<String> res2 = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"args\":[\"--json\"],\"wait\":false,\"clientRequestId\":\"lp-2\"}");
        String id2 = (String) io.nop.ib.json.JsonIn.readAsMap(res2.body()).get("id");

        long pollStart = System.currentTimeMillis();
        HttpResponse<String> poll = get("/v1/job/" + id2 + "?waitMs=1500");
        long waited = System.currentTimeMillis() - pollStart;
        assertEquals(200, poll.statusCode(), "the intermediate state is HTTP 200, not an error");
        assertTrue(waited >= 1400, "the server honored waitMs, waited " + waited + "ms");
        Map<String, Object> mid = io.nop.ib.json.JsonIn.readAsMap(poll.body());
        assertEquals(id2, mid.get("id"));
        assertEquals("queued", mid.get("state"));
        assertEquals("queued", mid.get("phase"));
        assertEquals(1, ((Number) mid.get("queueAhead")).intValue(), "lp-1 is queued ahead");
        assertTrue(((Number) mid.get("waitedMs")).longValue() >= 1400);
        assertNotNull(mid.get("logPath"), "the intermediate state names the job log");

        // release the lane; a long poll on the finished job returns the terminal payload
        holdLane(false);
        Map<String, Object> done = Map.of();
        for (int i = 0; i < 200; i++) {
            HttpResponse<String> r = get("/v1/job/" + id + "?waitMs=1000");
            done = io.nop.ib.json.JsonIn.readAsMap(r.body());
            if ("done".equals(done.get("state")))
                break;
            Thread.sleep(50);
        }
        assertEquals("done", done.get("state"), "looping long polls reach the terminal state");
        assertNotNull(done.get("payload"), "terminal payload retrievable after disconnect+poll");
        // idempotency across the HTTP surface
        HttpResponse<String> again = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":false,\"clientRequestId\":\"lp-1\"}");
        Map<String, Object> replay = io.nop.ib.json.JsonIn.readAsMap(again.body());
        assertEquals(id, replay.get("id"), "same clientRequestId returns the same job id");
        assertEquals(true, replay.get("replayed"));
    }

    /** the job log exists after a run and logTail returns its tail. */
    @Test
    void jobLogReachableViaLogTail() throws Exception {
        holdLane(false); // self-sufficient: jobs must be able to finish
        HttpResponse<String> res = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":true}");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        String id = (String) body.get("id");
        Path logFile = Path.of((String) body.get("logPath"));
        assertTrue(Files.exists(logFile), "the finished job left its log on disk");
        String log = Files.readString(logFile);
        assertTrue(log.contains("[out]"), "stdout lines carry the [out] marker: " + log);
        assertTrue(log.contains("cli stdout marker"), "the cli output is in the job log");

        HttpResponse<String> tail = get("/v1/job/" + id + "?logTail=20");
        Map<String, Object> tailBody = io.nop.ib.json.JsonIn.readAsMap(tail.body());
        assertNotNull(tailBody.get("logTail"), "logTail rides the snapshot");
        assertTrue(String.valueOf(tailBody.get("logTail")).contains("cli stdout marker"));
        assertEquals(400, get("/v1/job/" + id + "?bogus=1").statusCode(),
                "unknown query parameters are an explicit 400");
    }

    /** state= filters /v1/jobs; health stays fast while the long-poll pool is saturated. */
    @Test
    @SuppressWarnings("unchecked")
    void stateFilterAndHealthNeverStarved() throws Exception {
        holdLane(true); // saturation jobs stay RUNNING/QUEUED, long polls stay parked
        CountDownLatch pollsDone = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            final int n = i;
            Thread t = new Thread(() -> {
                try {
                    HttpResponse<String> r = post("/v1/run",
                            "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":false,"
                                    + "\"clientRequestId\":\"sat-" + n + "\"}");
                    String id = String.valueOf(io.nop.ib.json.JsonIn.readAsMap(r.body()).get("id"));
                    get("/v1/job/" + id + "?waitMs=3000");
                } catch (Exception e) {
                    // intentionally ignored: poller threads race the job to
                    // terminal state; a lost poll is not a failure signal
                } finally {
                    pollsDone.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        Thread.sleep(1000); // let the 4 long polls take the dedicated pool
        long t0 = System.currentTimeMillis();
        HttpResponse<String> health = get("/v1/health");
        long healthMs = System.currentTimeMillis() - t0;
        assertEquals(200, health.statusCode());
        assertTrue(healthMs < 1000, "health answered in " + healthMs
                + "ms while the long-poll pool was saturated");
        HttpResponse<String> queued = get("/v1/jobs?state=queued");
        assertEquals(200, queued.statusCode());
        List<Map<String, Object>> jobs =
                (List<Map<String, Object>>) io.nop.ib.json.JsonIn.readAsMap(queued.body()).get("jobs");
        for (Map<String, Object> j : jobs)
            assertEquals("queued", j.get("state"), "state filter only returns queued jobs");
        assertEquals(400, get("/v1/jobs?state=bogus").statusCode(), "bad state is a 400");
        holdLane(false); // drain the saturation jobs
        assertTrue(pollsDone.await(60, TimeUnit.SECONDS), "long polls finished");
    }
}
