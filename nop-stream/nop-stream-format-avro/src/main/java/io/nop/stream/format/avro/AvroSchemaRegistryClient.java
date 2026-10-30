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
 *
 * <p>Plan 2511 C2-002 (adjudication ③, R03 lock-heavy-work-out-of-lock projection):
 * every {@link HttpRequest} carries a request-level {@code .timeout()} (default 10s,
 * constructor-injected {@link Duration} — the client never reads global config), so a
 * hung registry fails typed instead of blocking the task thread forever; and schema
 * fetches run OUTSIDE the ConcurrentHashMap bin locks (get → miss → fetch →
 * {@code putIfAbsent} idempotent backfill) — HTTP I/O must never stay inside a CHM
 * mapping function.
 */
public class AvroSchemaRegistryClient {

    /** Default request-level timeout when not injected (same order as connectTimeout). */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Map<Integer, Schema> schemaByIdCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSchema> latestBySubject = new ConcurrentHashMap<>();

    /** id + parsed schema for a subject's latest version. */
    record CachedSchema(int id, Schema schema) {
    }

    public AvroSchemaRegistryClient(String baseUrl) {
        this(baseUrl, DEFAULT_REQUEST_TIMEOUT);
    }

    public AvroSchemaRegistryClient(String baseUrl, Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new StreamAvroException("registry baseUrl must not be empty");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new StreamAvroException("registry request timeout must be positive, got: "
                    + requestTimeout);
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Registers the schema (idempotent server-side); returns the assigned schema id. */
    public int registerSchema(String subject, Schema schema) {
        String body = "{\"schema\":"
                + JsonTool.stringify(schema.toString()) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/subjects/" + subject + "/versions"))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        Map<?, ?> resp = postJson(request);
        Object id = resp.get("id");
        if (id instanceof Number n) {
            return n.intValue();
        }
        throw new StreamAvroException("registerSchema: unexpected response body: " + resp);
    }

    /**
     * Cached schema lookup by registry id. The HTTP fetch runs OUTSIDE any CHM bin
     * lock (get → miss → fetch → putIfAbsent idempotent backfill): a hung registry
     * delays only the fetching thread up to the request timeout, never poisons the
     * cache's bin locks for concurrent lookups of other ids.
     */
    public Schema getSchemaById(int id) {
        Schema cached = schemaByIdCache.get(id);
        if (cached != null) {
            return cached;
        }
        Schema fetched = fetchSchemaById(id);
        Schema race = schemaByIdCache.putIfAbsent(id, fetched);
        return race != null ? race : fetched;
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
                .timeout(requestTimeout)
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
