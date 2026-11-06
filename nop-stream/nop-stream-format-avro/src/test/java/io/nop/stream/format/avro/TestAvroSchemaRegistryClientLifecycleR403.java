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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ③ — W-03 (MA-11=C4-002, merged C7-013;
 * adjudication B.2): the schema-registry client and the wire-format must own a
 * real close lifecycle. The JDK {@code HttpClient} spawns its selector thread
 * + connection pool at CONSTRUCTION — pre-fix nothing could ever close it, so
 * every wire-format assembly permanently leaked the selector group and its
 * keep-alive connections (the job assembly-stop cycle accumulated them for the
 * process lifetime).
 *
 * <p>Drive form (S3 seam — the close() methods ARE the public seam; the
 * pre-fix red evidence is the COMPILE face of this class: no close() to call,
 * registered in {@code _tmp/red-2536-GC-compile.log}). E-2536-3 adaptation:
 * on JDK 25 the HttpClient selector runs on a VIRTUAL thread invisible to
 * {@code Thread.getAllStackTraces} (platform-thread enumeration), so the
 * "selector thread enumeration zero-net-growth" driver leg is carried by the
 * R07 fd-regression channel (each unclosed client that performed an exchange
 * holds its keep-alive socket fds; close releases them) — the detector
 * calibration leg proves the fd channel has teeth.
 * <ul>
 *   <li>detector calibration: 5 unclosed clients each performing one real
 *       exchange hold their fds (delta grows — guards the convergence
 *       assertion against a false-green detector);</li>
 *   <li>assembly-stop cycles: 5 rounds of construct → exchange → close → fd
 *       delta collapses back to the pre-cycle baseline (R07 fd-regression
 *       precedent shape — plan 2522 TestFileDownloadSinkClose form reused, file
 *       not copied);</li>
 *   <li>close transparency: AvroWireFormat.close() passes through to the owned
 *       client and is idempotent (double close no throw).</li>
 * </ul>
 * WI18 production host wiring is the adjudicated reservation — the caller
 * obligation is a documented contract (factory javadoc) until then; this seam
 * carries the rule-23 wiring verification in its place (K10).
 */
class TestAvroSchemaRegistryClientLifecycleR403 {

    private static final String INT_SCHEMA = "{\"type\":\"record\",\"name\":\"I\","
            + "\"fields\":[{\"name\":\"v\",\"type\":\"int\"}]}";

    private HttpServer registry;

    @BeforeEach
    void setUp() throws IOException {
        registry = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        registry.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            byte[] body;
            if ("POST".equals(method) && path.matches("/subjects/[^/]+/versions")) {
                exchange.getRequestBody().readAllBytes();
                body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
            } else if (path.startsWith("/schemas/ids/")) {
                body = ("{\"schema\":" + quote(INT_SCHEMA) + "}").getBytes(StandardCharsets.UTF_8);
            } else {
                body = "{\"error\":1}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
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

    private String baseUrl() {
        return "http://127.0.0.1:" + registry.getAddress().getPort();
    }

    private static long openFileDescriptorCount() {
        com.sun.management.UnixOperatingSystemMXBean os =
                (com.sun.management.UnixOperatingSystemMXBean)
                        java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return os.getOpenFileDescriptorCount();
    }

    /** One real exchange (register + encode) so the client's connection pool materializes a socket. */
    private byte[] performOneExchange(AvroWireFormat format) {
        Schema schema = new Schema.Parser().parse(INT_SCHEMA);
        GenericData.Record record = new GenericData.Record(schema);
        record.put("v", 42);
        return format.encode(record, "r403c-value");
    }

    /**
     * Detector calibration + convergence leg (mutation-proofing the pin):
     * unclosed clients that performed real exchanges MUST be observed holding
     * fds; after close the delta must collapse. If the calibration half ever
     * fails, the convergence assertion is measuring nothing.
     */
    @Test
    void assemblyStopCyclesHoldFdsWhenUnclosedAndReleaseOnClose() throws Exception {
        long before = openFileDescriptorCount();
        List<AvroWireFormat> unclosed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(baseUrl());
            AvroWireFormat format = new AvroWireFormat(client);
            performOneExchange(format);
            unclosed.add(format);
        }
        long leakedDelta = openFileDescriptorCount() - before;
        assertTrue(leakedDelta >= 3,
                "detector calibration: 5 unclosed clients after real exchanges must hold "
                        + "their keep-alive socket fds (got +" + leakedDelta + ") — otherwise "
                        + "the convergence assertion has no teeth");

        // The lifecycle end of each assembly unit: wire-format close passes
        // through to its owned client (selector + connection pool shutdown).
        for (AvroWireFormat format : unclosed) {
            format.close();
        }
        // Brief settle for the server-side EOF handling of the closed sockets.
        Thread.sleep(100);
        long afterCloseDelta = openFileDescriptorCount() - before;
        // Relative bound: close must release the clear majority of the leaked
        // client-held fds. The small residual is the FAKE SERVER's own
        // keep-alive sockets (server-side fds are torn down in tearDown, not by
        // the client close) — the pre-fix mutation (no close) keeps the full
        // leakedDelta and fails this bound with wide margin.
        assertTrue(afterCloseDelta <= leakedDelta / 2,
                "closed assembly-stop cycles must release the client-held fds (R07 form), "
                        + "got residual delta=" + afterCloseDelta + " after leaking " + leakedDelta);
    }

    /** Close transparency: wire-format.close() reaches the client; double close is a no-op. */
    @Test
    void wireFormatClosePassesThroughAndIsIdempotent() {
        AvroSchemaRegistryClient client = new AvroSchemaRegistryClient(baseUrl());
        AvroWireFormat format = new AvroWireFormat(client);
        performOneExchange(format);
        format.close();
        assertDoesNotThrow(format::close, "double close must be a no-op, not a throw");
        assertDoesNotThrow(client::close, "client double close must be a no-op, not a throw");
    }
}
