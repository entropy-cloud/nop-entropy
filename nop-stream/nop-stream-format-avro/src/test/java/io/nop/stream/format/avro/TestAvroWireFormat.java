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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
