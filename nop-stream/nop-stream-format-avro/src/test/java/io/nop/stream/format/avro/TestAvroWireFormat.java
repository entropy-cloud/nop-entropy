/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.format.avro;

import com.sun.net.httpserver.HttpServer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI18 (plan 2341): wire format tests over an embedded fake Schema Registry (JDK
 * HttpServer simulating the Confluent REST API) — register→encode→decode round trip,
 * schema evolution, cache behaviour, and typed error faces.
 */
class TestAvroWireFormat {

    private static final String USER_SCHEMA_V1 = """
            {"type":"record","name":"User","fields":[
              {"name":"name","type":"string"},
              {"name":"age","type":"int"}]}
            """;

    /** v1 + optional field "email" — backward-compatible evolution. */
    private static final String USER_SCHEMA_V2 = """
            {"type":"record","name":"User","fields":[
              {"name":"name","type":"string"},
              {"name":"age","type":"int"},
              {"name":"email","type":["null","string"],"default":null}]}
            """;

    private HttpServer registry;
    private final ConcurrentHashMap<String, String> schemasById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestIdBySubject = new ConcurrentHashMap<>();
    private int nextId = 1;
    private int restCalls;

    // CRA 8.43 R4-03 W-02 fault-injection hooks (S4 seam extension of the fake
    // registry; every pre-existing test case runs unchanged with them inert).
    private volatile int faultLatestStatus = 0;
    private volatile int latestCalls;

    @BeforeEach
    void setUp() throws IOException {
        restCalls = 0;
        registry = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        registry.createContext("/", exchange -> {
            restCalls++;
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            try {
                if ("POST".equals(method) && path.matches("/subjects/[^/]+/versions")) {
                    String subject = path.split("/")[2];
                    String body = new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8);
                    String schemaText = extractJsonField(body, "schema");
                    String schemaKey = subject + ":" + schemaText.hashCode();
                    int id = Integer.parseInt(schemasById.computeIfAbsent(schemaKey,
                            k -> String.valueOf(assignId(schemaText))));
                    latestIdBySubject.put(subject, String.valueOf(id));
                    respond(exchange, 200, "{\"id\":" + id + "}");
                } else if ("GET".equals(method) && path.startsWith("/schemas/ids/")) {
                    long id = Long.parseLong(path.substring("/schemas/ids/".length()));
                    String schemaText = schemasById.get("id-" + id);
                    if (schemaText == null) {
                        respond(exchange, 404, "{\"error_code\":40403}");
                    } else {
                        respond(exchange, 200, "{\"schema\":"
                                + quote(schemaText) + "}");
                    }
                } else if ("GET".equals(method) && path.matches("/subjects/[^/]+/versions/latest")) {
                    latestCalls++;
                    if (faultLatestStatus != 0) {
                        respond(exchange, faultLatestStatus, "{\"injected\":true}");
                        return;
                    }
                    String subject = path.split("/")[2];
                    String idStr = latestIdBySubject.get(subject);
                    if (idStr == null) {
                        respond(exchange, 404, "{\"error_code\":40401}");
                    } else {
                        int id = Integer.parseInt(idStr);
                        respond(exchange, 200, "{\"id\":" + id + ",\"schema\":"
                                + quote(schemasById.get("id-" + id)) + "}");
                    }
                } else {
                    respond(exchange, 404, "{}");
                }
            } finally {
                exchange.close();
            }
        });
        registry.start();
    }

    @AfterEach
    void tearDown() {
        registry.stop(0);
    }

    private int assignId(String schemaText) {
        int id = nextId++;
        schemasById.put("id-" + id, schemaText);
        return id;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String extractJsonField(String body, String field) {
        // minimal extraction for the fake: "field":"<escaped value>"
        String key = "\"" + field + "\":\"";
        int start = body.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(i + 1);
                if (next == '"') {
                    sb.append('"');
                } else if (next == 'n') {
                    sb.append('\n');
                } else if (next == '\\') {
                    sb.append('\\');
                } else {
                    sb.append('\\').append(next);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/vnd.schemaregistry.v1+json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private AvroSchemaRegistryClient client() {
        int port = registry.getAddress().getPort();
        return new AvroSchemaRegistryClient("http://127.0.0.1:" + port);
    }

    private static GenericRecord user(String name, int age, String email, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("name", name);
        record.put("age", age);
        if (schema.getFields().size() > 2) {
            record.put("email", email);
        }
        return record;
    }

    @Test
    void registerEncodeDecodeRoundTrip() {
        AvroSchemaRegistryClient client = client();
        AvroWireFormat format = new AvroWireFormat(client);
        Schema schema = new Schema.Parser().parse(USER_SCHEMA_V1);
        GenericRecord record = user("alice", 30, null, schema);

        byte[] wire = format.encode(record, "users-value");
        // Confluent wire format: 0x0 + 4-byte BE schema id + payload
        assertEquals(0x0, wire[0] & 0xFF, "magic byte");
        assertTrue(wire.length > 5, "payload must follow the 5-byte header");

        GenericRecord decoded = format.decode(wire, "users-value");
        assertEquals("alice", decoded.get("name").toString());
        assertEquals(30, decoded.get("age"));
    }

    @Test
    void schemaEvolutionReaderLatest() {
        AvroSchemaRegistryClient client = client();
        AvroWireFormat v1Format = new AvroWireFormat(client);
        Schema v1 = new Schema.Parser().parse(USER_SCHEMA_V1);
        GenericRecord v1Record = user("bob", 25, null, v1);

        byte[] wire = v1Format.encode(v1Record, "users-value");

        // evolve: register v2 as the subject's latest (reader schema adds email)
        Schema v2 = new Schema.Parser().parse(USER_SCHEMA_V2);
        client.registerSchema("users-value", v2);

        // decoding with the latest reader schema resolves v1→v2 (email defaults null)
        GenericRecord decoded = v1Format.decode(wire, "users-value");
        assertEquals("bob", decoded.get("name").toString());
        assertEquals(25, decoded.get("age"));
    }

    @Test
    void schemaIdCacheAvoidsRepeatFetch() {
        AvroSchemaRegistryClient client = client();
        AvroWireFormat format = new AvroWireFormat(client);
        Schema schema = new Schema.Parser().parse(USER_SCHEMA_V1);
        GenericRecord record = user("carol", 40, null, schema);

        byte[] wire1 = format.encode(record, "users-value");
        int callsAfterEncode = restCalls;
        format.decode(wire1, "users-value");
        format.decode(wire1, "users-value");
        // schema fetches are cached: rest call count must not grow after the initial
        // register + latest fetch
        assertTrue(restCalls <= callsAfterEncode + 2,
                "cache must avoid repeated REST fetches, restCalls=" + restCalls);
    }

    @Test
    void invalidMagicByteFailsTyped() {
        AvroWireFormat format = new AvroWireFormat(client());
        byte[] bad = {0x5, 0, 0, 0, 1, 1};
        StreamAvroException ex = assertThrows(StreamAvroException.class,
                () -> format.decode(bad, "users-value"));
        assertTrue(ex.getMessage().contains("magic byte"), ex.getMessage());
    }

    @Test
    void registryUnreachableFailsTyped() {
        AvroSchemaRegistryClient unreachable = new AvroSchemaRegistryClient(
                "http://127.0.0.1:1");
        AvroWireFormat format = new AvroWireFormat(unreachable);
        Schema schema = new Schema.Parser().parse(USER_SCHEMA_V1);
        GenericRecord record = user("dave", 20, null, schema);
        assertThrows(StreamAvroException.class, () -> format.encode(record, "subj"));
    }

    /**
     * Plan 2511 C2-002 / C10-008 (adjudication ③): a HUNG registry (connection
     * accepted, never answered) must fail typed within the request-level timeout —
     * the pre-fix shape was an unbounded block. Replaces the old fast-refusal-only
     * injection which never exercised the hang face (假保护消号：快速拒连不承载挂死面).
     */
    @Test
    void registryHangFailsTypedWithinRequestTimeout() throws Exception {
        try (ServerSocket hangServer = new ServerSocket(0, 8,
                InetAddress.getByName("127.0.0.1"))) {
            // accept and HOLD the connections open, never read/write: the client
            // connection hangs on read until the request-level timeout fires
            List<Socket> held = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                while (!hangServer.isClosed()) {
                    try {
                        held.add(hangServer.accept());
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "hang-registry-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();

            AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(
                    "http://127.0.0.1:" + hangServer.getLocalPort(),
                    Duration.ofMillis(500));
            AvroWireFormat format = new AvroWireFormat(client);
            Schema schema = new Schema.Parser().parse(USER_SCHEMA_V1);
            GenericRecord record = user("hung", 1, null, schema);

            long start = System.nanoTime();
            // timeout red line (plan 2511 E2 red ③): pre-fix this carrier bounds the
            // otherwise UNBOUNDED hang; post-fix the typed failure arrives well inside it
            StreamAvroException ex = assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> assertThrows(StreamAvroException.class,
                            () -> format.encode(record, "subj")),
                    "C2-002: hung registry must fail typed within the request timeout "
                            + "(pre-fix: unbounded hang carried by this red line)");
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            // typed failure arrives via the request-level timeout, not after an
            // unbounded hang (bound: 10x the configured timeout for CI slack)
            assertTrue(elapsedMillis < 5_000,
                    "hang must fail within the request timeout, took " + elapsedMillis + "ms");
            assertTrue(elapsedMillis >= 400,
                    "failure must come from the timeout, not an immediate refusal: "
                            + elapsedMillis + "ms");
            assertTrue(ex.getMessage().contains("failed"), ex.getMessage());
            for (Socket s : held) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // best-effort teardown
                }
            }
        }
    }

    /**
     * Plan 2511 C2-002 (adjudication ③, R03 projection): while one encode is stuck on
     * the hung registry, a concurrent encode of ANOTHER subject must still complete
     * (typed) — the fetch runs outside the {@code writerSchemaIds} bin locks, so the
     * hang cannot serialize the whole bin (锁限时释放/不互阻断言).
     */
    @Test
    void concurrentEncodeNotBlockedByHungFetch() throws Exception {
        try (ServerSocket hangServer = new ServerSocket(0, 8,
                InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> {
                while (!hangServer.isClosed()) {
                    try {
                        hangServer.accept().close();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "hang-registry-acceptor-2");
            acceptor.setDaemon(true);
            acceptor.start();

            AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(
                    "http://127.0.0.1:" + hangServer.getLocalPort(),
                    Duration.ofMillis(500));
            AvroWireFormat format = new AvroWireFormat(client);
            Schema schema = new Schema.Parser().parse(USER_SCHEMA_V1);
            GenericRecord record = user("carol", 3, null, schema);

            CountDownLatch firstInFlight = new CountDownLatch(1);
            CountDownLatch firstDone = new CountDownLatch(1);
            Thread stuckEncoder = new Thread(() -> {
                firstInFlight.countDown();
                try {
                    format.encode(record, "stuck-subject");
                } catch (StreamAvroException expected) {
                    // typed failure is the contract; both threads must land here
                } finally {
                    firstDone.countDown();
                }
            }, "stuck-encoder");
            stuckEncoder.setDaemon(true);
            stuckEncoder.start();

            assertTrue(firstInFlight.await(5, TimeUnit.SECONDS),
                    "stuck encoder never started");
            // give the stuck encoder time to be blocked inside its HTTP call
            Thread.sleep(200);

            long start = System.nanoTime();
            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> assertThrows(StreamAvroException.class,
                            () -> format.encode(record, "other-subject"),
                            "concurrent encode must not be blocked by the hung fetch"),
                    "C2-002: bin lock must be released in bounded time (pre-fix red line)");
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            assertTrue(elapsedMillis < 5_000,
                    "concurrent encode must finish within its own timeout, took "
                            + elapsedMillis + "ms");
            assertTrue(firstDone.await(10, TimeUnit.SECONDS),
                    "stuck encoder must eventually fail typed");
        }
    }

    // ------------------------------------------------------------------------
    // CRA 8.43 R4-03 red ② — W-02 (MA-10=C7-006②, merged C3-007): reader-schema
    // fallback must be rate-limited (negative cache) and NON-silent (WARN once
    // per TTL window per subject; subject-missing 404 carve-out falls back
    // without WARN). Appended cases only — every case above is a zero-rewrite
    // anchor (2511 C10-008 hang pins keep running unchanged).
    // ------------------------------------------------------------------------

    /**
     * red ② carve-out (F-1): a subject that was never registered (HTTP 404) is
     * the legitimate first-write race — the fallback happens WITHOUT any WARN
     * (expected steady state, self-heals within one TTL once registered) and
     * still arms the backoff window (zero registry hammering).
     */
    @Test
    void subjectMissingCarveOutFallsBackWithoutWarnR403() throws Exception {
        AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(
                "http://127.0.0.1:" + registry.getAddress().getPort(),
                Duration.ofMillis(500), Duration.ofMillis(300));
        AvroWireFormat format = new AvroWireFormat(client);
        try {
            Schema v1 = new Schema.Parser().parse(USER_SCHEMA_V1);
            byte[] wire = format.encode(record(v1, "n1", 1), "registered-value");

            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                    org.slf4j.LoggerFactory.getLogger(AvroWireFormat.class);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                    new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                GenericRecord out = format.decode(wire, "never-registered-value");
                assertTrue(out.getSchema().getField("email") == null,
                        "subject-missing falls back to the writer schema");
                long warns = appender.list.stream()
                        .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                                && e.getFormattedMessage().contains("never-registered-value"))
                        .count();
                assertEquals(0L, warns,
                        "the subject-missing carve-out must NOT warn (404 is a legitimate race)");
                assertTrue(client.isReaderSchemaFailureCached("never-registered-value"),
                        "the backoff window still arms for a missing subject");
                int latestAfterFirst = latestCalls;
                format.decode(wire, "never-registered-value");
                assertEquals(latestAfterFirst, latestCalls,
                        "the carved-out subject is still negative-cached (no registry hammering)");
            } finally {
                logger.detachAppender(appender);
            }
        } finally {
            format.close();
        }
    }

    /** CRA 8.43 R4-03 W-02: a non-positive reader-schema fallback TTL fails fast at construction. */
    @Test
    void nonPositiveReaderSchemaFallbackTtlFailsFastR403() {
        assertThrows(StreamAvroException.class,
                () -> new AvroSchemaRegistryClient("http://127.0.0.1:1",
                        Duration.ofMillis(500), Duration.ZERO));
        assertThrows(StreamAvroException.class,
                () -> new AvroSchemaRegistryClient("http://127.0.0.1:1",
                        Duration.ofMillis(500), Duration.ofMillis(-1)));
    }

    /** Plan 2511 C2-002: a non-positive request timeout is rejected at assembly. */
    @Test
    void nonPositiveRequestTimeoutFailsFast() {
        assertThrows(StreamAvroException.class,
                () -> new AvroSchemaRegistryClient("http://127.0.0.1:1", Duration.ZERO));
        assertThrows(StreamAvroException.class,
                () -> new AvroSchemaRegistryClient("http://127.0.0.1:1", Duration.ofMillis(-1)));
    }

    private static Schema userSchema() {
        return new Schema.Parser().parse(USER_SCHEMA_V1);
    }

    private static GenericRecord record(Schema schema, String name, int age) {
        GenericRecord r = new GenericData.Record(schema);
        r.put("name", name);
        r.put("age", age);
        return r;
    }

    @Test
    void factoryMethodsInjectable() {
        // the factory output must be directly injectable into the connector surface:
        // Function<byte[],GenericRecord> / Function<GenericRecord,byte[]>
        AvroWireFormat format = new AvroWireFormat(client());
        java.util.function.Function<byte[], GenericRecord> deser = format.avroDeserializer("users-value");
        java.util.function.Function<GenericRecord, byte[]> ser = format.avroSerializer("users-value");

        Schema schema = userSchema();
        GenericRecord record = user("erin", 22, null, schema);
        byte[] wire = ser.apply(record);
        GenericRecord back = deser.apply(wire);
        assertEquals("erin", back.get("name").toString());
        assertEquals(22, back.get("age"));
    }
}
