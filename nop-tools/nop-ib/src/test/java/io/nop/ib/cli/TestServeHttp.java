package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.service.ServiceCore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 10 phase-2 pins: the HTTP surface (design 07 §2.1) — loopback JSON in /
 * JSON out, payload isomorphic with the CLI {@code --json} output, explicit
 * error codes (400 invalid input, 404 unknown job, 409 cancel-running), and
 * the wait=true path riding the same ServiceCore submission path as async.
 */

@SuppressWarnings("unchecked")
class TestServeHttp {
    private static Path repo;
    private static ServiceCore core;
    private static int port;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        repo = Files.createTempDirectory("ib-http");
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
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        ServeHttp.serve(core, port, System.out, System.err);
    }

    @AfterAll
    static void stop() {
        // daemon threads + ephemeral fixture: nothing to join, JVM exits clean
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** wait=true run: 200 + exit field + payload parsed as the CLI JSON object. */
    @Test
    void syncRunReturnsCliIsomorphicPayload() throws Exception {
        HttpResponse<String> res = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":true}");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        assertEquals(0, ((Number) body.get("exit")).intValue(), "status job exits OK");
        assertEquals("done", body.get("state"));
        // payload isomorphic with the CLI --json output for the same command
        ByteArrayOutputStream cli = new ByteArrayOutputStream();
        java.io.PrintStream cliOut = new java.io.PrintStream(cli, true, StandardCharsets.UTF_8);
        int cliExit = Commands.status(new IbEngine(repo), cliOut, true);
        assertEquals(0, cliExit);
        Map<String, Object> cliJson = io.nop.ib.json.JsonIn.readAsMap(cli.toString());
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        assertNotNull(payload, "payload parses into the CLI JSON object");
        assertEquals(cliJson.keySet(), payload.keySet(),
                "HTTP payload fields == CLI --json fields (design 07 §2.1)");
    }

    /** invalid inputs are explicit 4xx, never silent accepts. */
    @Test
    void invalidInputsRejectedExplicitly() throws Exception {
        assertEquals(400, post("/v1/run", "not-json").statusCode());
        assertEquals(400, post("/v1/run", "{\"cmd\":\"status\"}").statusCode(),
                "missing projectDir is a 400");
        assertEquals(400, post("/v1/run",
                "{\"projectDir\":\"/tmp\",\"cmd\":\"deploy\"}").statusCode(),
                "non-Maven dir AND unsupported cmd is a 400");
    }

    /** async submit + job polling + 404 + DELETE of a finished job = 409. */
    @Test
    void asyncJobLifecycleObservable() throws Exception {
        HttpResponse<String> res = post("/v1/run",
                "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":false}");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        String id = (String) body.get("id");
        assertNotNull(id, "async run returns a job id");
        // poll to terminal state
        String state = "";
        for (int i = 0; i < 50; i++) {
            Map<String, Object> snap = io.nop.ib.json.JsonIn.readAsMap(get("/v1/job/" + id).body());
            state = (String) snap.get("state");
            if ("done".equals(state) || "failed".equals(state))
                break;
            Thread.sleep(50);
        }
        assertEquals("done", state, "async job reaches done through the same core");
        assertEquals(409, HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v1/job/" + id))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode(),
                "DELETE on a finished job is already-terminal (409)");
        assertEquals(404, get("/v1/job/nope").statusCode(), "unknown job is 404");
    }

    /** plan 2583 pin: an over-limit request body is rejected 413 at READ time. */
    @Test
    void oversizedBodyRejectedWith413() throws Exception {
        byte[] big = new byte[ServiceCore.MAX_PAYLOAD_BYTES + 1];
        java.util.Arrays.fill(big, (byte) 'x');
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v1/run"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(big)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(413, res.statusCode(),
                "body over the payload cap must be rejected before any processing");
        assertTrue(res.body().contains("payload limit"), res.body());
    }

    /** health exposes version/cores/workerBudget/activeJobs (design 07 §2.1). */
    @Test
    void healthExposesBudgetFacts() throws Exception {
        HttpResponse<String> res = get("/v1/health");
        assertEquals(200, res.statusCode());
        Map<String, Object> health = io.nop.ib.json.JsonIn.readAsMap(res.body());
        assertEquals(4, ((Number) health.get("cores")).intValue());
        assertEquals(2, ((Number) health.get("workerBudget")).intValue(), "max(2, cores/2)");
        assertNotNull(health.get("activeJobs"));
        assertTrue(((Number) health.get("pendingTotal")).intValue() >= 0);
    }

    /** wave 11: GET /v1/jobs returns recent job summaries, limit honored, 400 on bad limit. */
    @Test
    @SuppressWarnings("unchecked")
    void jobsEndpointReturnsSummariesWithLimit() throws Exception {
        // ensure at least one finished job exists (earlier tests submitted some)
        post("/v1/run", "{\"projectDir\":\"" + repo + "\",\"cmd\":\"status\",\"wait\":true}");
        HttpResponse<String> res = get("/v1/jobs");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) body.get("jobs");
        assertNotNull(jobs, "jobs array present");
        assertFalse(jobs.isEmpty(), "at least the just-finished job is listed");
        Map<String, Object> s = jobs.get(0);
        for (String field : List.of("id", "cmd", "projectDir", "state", "exit",
                "queuedAt", "startedAt", "finishedAt", "durationMs"))
            assertTrue(s.containsKey(field), "summary carries " + field);
        assertEquals("status", s.get("cmd"));
        assertEquals("done", s.get("state"));
        assertTrue(((Number) s.get("durationMs")).longValue() >= 0);
        // limit truncates; most recent first
        List<Map<String, Object>> capped =
                (List<Map<String, Object>>) io.nop.ib.json.JsonIn.readAsMap(get("/v1/jobs?limit=1").body()).get("jobs");
        assertEquals(1, capped.size());
        assertEquals(s.get("id"), capped.get(0).get("id"), "limit keeps the newest job");
        assertEquals(400, get("/v1/jobs?limit=abc").statusCode(), "bad limit is a 400");
        assertEquals(405, HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v1/jobs"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode(), "POST /v1/jobs is 405");
    }

    /** wave 11 doc surface: GET /v1/help self-describes the contract for zero-prior integrators. */
    @Test
    void helpEndpointSelfDescribesContract() throws Exception {
        HttpResponse<String> res = get("/v1/help");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        assertEquals("nop-ib", body.get("service"));
        Map<String, Object> run = (Map<String, Object>) body.get("run");
        assertNotNull(run.get("body"), "run contract present");
        Map<String, Object> endpoints = (Map<String, Object>) body.get("endpoints");
        for (String ep : List.of("GET /v1/job/{id}", "GET /v1/jobs?limit=N&state=S", "GET /v1/queue",
                "GET /v1/health", "DELETE /v1/job/{id}"))
            assertTrue(endpoints.containsKey(ep), "endpoint documented: " + ep);
        Map<String, Object> tasks = (Map<String, Object>) body.get("tasksDeclaration");
        assertTrue(String.valueOf(tasks.get("file")).startsWith("nop-ib.tasks.json"),
                "task declaration contract carries the branded file name");
        assertEquals(405, HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v1/help"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode(), "POST /v1/help is 405");
    }

    /** wave 11: /v1/queue lanes expose root, depth, runningCmd (null when idle). */
    @Test
    @SuppressWarnings("unchecked")
    void queueEndpointExposesLaneDepthAndRunningCmd() throws Exception {
        HttpResponse<String> res = get("/v1/queue");
        assertEquals(200, res.statusCode());
        Map<String, Object> body = io.nop.ib.json.JsonIn.readAsMap(res.body());
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) body.get("lanes");
        assertNotNull(lanes, "lanes list present");
        // after prior sync runs the fixture lane exists and must be idle
        assertFalse(lanes.isEmpty(), "fixture root appears as a lane");
        boolean fixtureLaneSeen = false;
        for (Map<String, Object> lane : lanes) {
            assertTrue(lane.containsKey("root"));
            assertTrue(lane.containsKey("depth"), "lane exposes depth");
            assertTrue(lane.containsKey("runningCmd"), "lane exposes runningCmd");
            assertTrue(lane.containsKey("pending"));
            assertTrue(((Number) lane.get("depth")).intValue() >= 0);
            if (((String) lane.get("root")).equals(repo.toRealPath().toString())) {
                fixtureLaneSeen = true;
                assertEquals(0, ((Number) lane.get("depth")).intValue(),
                        "idle lane depth is 0");
                assertNull(lane.get("runningCmd"), "idle lane runningCmd is null");
            }
        }
        assertTrue(fixtureLaneSeen, "fixture root lane present");
    }
}
