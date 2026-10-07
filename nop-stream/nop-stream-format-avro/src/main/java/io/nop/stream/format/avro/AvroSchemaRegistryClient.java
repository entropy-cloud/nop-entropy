/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.format.avro;

import io.nop.core.lang.json.JsonTool;

import org.apache.avro.Schema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WI18 (plan 2341): minimal REST client for an external Confluent-compatible Schema
 * Registry — the registry service itself is NOT bundled (roadmap ruling: 不内置
 * registry 服务). Uses only the JDK HttpClient; zero Confluent dependency.
 *
 * <p>Endpoints used (Confluent Registry REST API):
 * <ul>
 *   <li>{@code POST /subjects/{subject}/versions} — register schema, returns {"id": N}</li>
 *   <li>{@code GET /subjects/{subject}/versions/latest} — {"id": N, "schema": "..."}</li>
 *   <li>{@code GET /schemas/ids/{id}} — {"schema": "..."}</li>
 * </ul>
 *
 * <p>Thread-safe; per-id and per-subject latest schema lookups are cached.
 */
public class AvroSchemaRegistryClient {

    private final HttpClient http;
    private final String baseUrl;
    private final Map<Integer, Schema> schemaByIdCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSchema> latestBySubject = new ConcurrentHashMap<>();

    /** id + parsed schema for a subject's latest version. */
    record CachedSchema(int id, Schema schema) {
    }

    public AvroSchemaRegistryClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new StreamAvroException("registry baseUrl must not be empty");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Registers the schema (idempotent server-side); returns the assigned schema id. */
    public int registerSchema(String subject, Schema schema) {
        String body = "{\"schema\":"
                + JsonTool.stringify(schema.toString()) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/subjects/" + subject + "/versions"))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        Map<?, ?> resp = postJson(request);
        Object id = resp.get("id");
        if (id instanceof Number n) {
            return n.intValue();
        }
        throw new StreamAvroException("registerSchema: unexpected response body: " + resp);
    }

    /** Cached schema lookup by registry id. */
    public Schema getSchemaById(int id) {
        return schemaByIdCache.computeIfAbsent(id, this::fetchSchemaById);
    }

    /** Cached latest schema + id for a subject. */
    public CachedSchema getLatestSchema(String subject) {
        CachedSchema cached = latestBySubject.get(subject);
        if (cached != null) {
            return cached;
        }
        Map<?, ?> resp = getJson("/subjects/" + subject + "/versions/latest");
        int id = ((Number) resp.get("id")).intValue();
        String schemaText = String.valueOf(resp.get("schema"));
        Schema schema = parseSchema(schemaText);
        CachedSchema cachedSchema = new CachedSchema(id, schema);
        latestBySubject.put(subject, cachedSchema);
        schemaByIdCache.put(id, schema);
        return cachedSchema;
    }

    private Schema fetchSchemaById(int id) {
        Map<?, ?> resp = getJson("/schemas/ids/" + id);
        return parseSchema(String.valueOf(resp.get("schema")));
    }

    private Schema parseSchema(String schemaText) {
        try {
            return new Schema.Parser().parse(schemaText);
        } catch (Exception e) {
            throw new StreamAvroException("invalid Avro schema: " + e.getMessage(), e);
        }
    }

    private Map<?, ?> getJson(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/vnd.schemaregistry.v1+json")
                .GET().build();
        return readJsonResponse(request);
    }

    private Map<?, ?> postJson(HttpRequest request) {
        return readJsonResponse(request);
    }

    private Map<?, ?> readJsonResponse(HttpRequest request) {
        HttpResponse<String> resp;
        try {
            resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new StreamAvroException("Schema Registry request to " + request.uri()
                    + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamAvroException("Schema Registry request interrupted", e);
        }
        if (resp.statusCode() != 200) {
            throw new StreamAvroException("Schema Registry request to " + request.uri()
                    + " failed: HTTP " + resp.statusCode() + " " + resp.body());
        }
        return JsonTool.parseBeanFromText(resp.body(), Map.class);
    }
}
