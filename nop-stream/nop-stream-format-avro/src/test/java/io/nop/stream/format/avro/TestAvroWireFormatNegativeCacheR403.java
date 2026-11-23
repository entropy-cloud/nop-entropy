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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ② behavioral carrier — W-02 (MA-10=C7-006②,
 * merged C3-007; adjudication B.1): a registry outage must NOT turn every
 * decode into a failed registry probe with zero signal. Pre-fix shape
 * (deterministic red): N decodes during an injected 500-outage hit the
 * registry N times (per-record call amplification) and emit ZERO WARN.
 * Post-fix shape: the negative cache suppresses probes inside the TTL window
 * and the window boundary WARNs exactly once (rate-limited, non-silent).
 *
 * <p>This class only uses the PRE-fix public API (encode/decode + a
 * fault-injecting fake registry + a log appender), so the red trial compiles
 * and runs against the pre-fix product. The TTL-expiry/retry and carve-out
 * seams (3-arg constructor, negative-cache accessors, close) are exercised in
 * {@code TestAvroWireFormat} (appended R403 cases) and
 * {@code TestAvroSchemaRegistryClientLifecycleR403} — their pre-fix red is the
 * compile face ({@code _tmp/red-2536-GC-compile.log}). Every pre-existing
 * TestAvroWireFormat case is a zero-rewrite anchor (2511 C10-008 hang pins).
 */
class TestAvroWireFormatNegativeCacheR403 {

    private static final String USER_SCHEMA_V1 = """
            {"type":"record","name":"User","fields":[
              {"name":"name","type":"string"},
              {"name":"age","type":"int"}]}
            """;

    private HttpServer registry;
    private final ConcurrentHashMap<String, String> schemasById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestIdBySubject = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicInteger latestCalls = new AtomicInteger();
    private volatile int faultLatestStatus = 0;

    @BeforeEach
    void setUp() throws IOException {
        registry = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        registry.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            try {
                if ("POST".equals(method) && path.matches("/subjects/[^/]+/versions")) {
                    String subject = path.split("/")[2];
                    String body = new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8);
                    String schemaText = extractJsonField(body, "schema");
                    String key = subject + ":" + schemaText.hashCode();
                    String id = schemasById.computeIfAbsent(key,
                            k -> String.valueOf(nextId.incrementAndGet()));
                    schemasById.put("id-" + id, schemaText);
                    latestIdBySubject.put(subject, id);
                    respond(exchange, 200, "{\"id\":" + id + "}");
                } else if ("GET".equals(method) && path.startsWith("/schemas/ids/")) {
                    String id = path.substring("/schemas/ids/".length());
                    String schemaText = schemasById.get("id-" + id);
                    if (schemaText == null) {
                        respond(exchange, 404, "{\"error_code\":40403}");
                    } else {
                        respond(exchange, 200, "{\"schema\":" + quote(schemaText) + "}");
                    }
                } else if ("GET".equals(method)
                        && path.matches("/subjects/[^/]+/versions/latest")) {
                    latestCalls.incrementAndGet();
                    if (faultLatestStatus != 0) {
                        respond(exchange, faultLatestStatus, "{\"injected\":true}");
                        return;
                    }
                    String subject = path.split("/")[2];
                    String id = latestIdBySubject.get(subject);
                    if (id == null) {
                        respond(exchange, 404, "{\"error_code\":40401}");
                    } else {
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

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String extractJsonField(String body, String field) {
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
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void outageFallsBackWarnsOnceAndSuppressesProbesInsideWindow() throws Exception {
        AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(
                "http://127.0.0.1:" + registry.getAddress().getPort(),
                Duration.ofMillis(500));
        AvroWireFormat format = new AvroWireFormat(client);
        Schema v1 = new Schema.Parser().parse(USER_SCHEMA_V1);
        GenericData.Record r = new GenericData.Record(v1);
        r.put("name", "n1");
        r.put("age", 1);
        // Full -T 1C reactor runs starve the ephemeral local HttpServer dispatcher and
        // the JDK HttpClient selector long enough that the very first loopback connect
        // can miss the client's 10s connect window (observed at ~9.5s in a 462-module
        // build; the same test finishes in <1s in isolation). The registry setup below
        // is idempotent, so one bounded retry keeps the behavioral assertions intact
        // instead of failing the whole reactor on a scheduler hiccup.
        byte[] wire;
        try {
            wire = format.encode(r, "r403b-value");
        } catch (StreamAvroException e) {
            wire = format.encode(r, "r403b-value");
        }

        faultLatestStatus = 500;
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(AvroWireFormat.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // (a) first decode during the outage: fallback to the writer schema.
            GenericRecord out = format.decode(wire, "r403b-value");
            assertTrue(out.getSchema().getField("email") == null,
                    "during the outage the decode must fall back to the WRITER schema "
                            + "(v1, no email field)");

            // (a) window boundary signal: exactly one WARN naming the subject.
            long warnAfterFirst = appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains("r403b-value"))
                    .count();
            assertEquals(1L, warnAfterFirst,
                    "the fallback must be NON-silent: exactly one WARN at the window "
                            + "boundary (pre-fix: zero signal while every decode fails)");

            // (b) inside the TTL window: zero further registry probes (pre-fix:
            // every decode is another failed probe — call amplification).
            int latestAfterFirst = latestCalls.get();
            for (int i = 0; i < 5; i++) {
                format.decode(wire, "r403b-value");
            }
            assertEquals(latestAfterFirst, latestCalls.get(),
                    "the negative cache must suppress registry probes inside the window "
                            + "(pre-fix: " + (latestCalls.get() - latestAfterFirst)
                            + " extra failed probes for 5 decodes)");

            long warnAfterWindow = appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("r403b-value"))
                    .count();
            assertEquals(1L, warnAfterWindow, "no additional WARN inside the window");
        } finally {
            logger.detachAppender(appender);
            // no close here on purpose: this carrier stays pre-fix compilable
            // (close() is a W-03 seam); the single unclosed client's daemon
            // selector thread is fork-local and harmless.
        }
    }
}
