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
 *
 * <p>CRA 8.43 R4-03 W-02 (MA-10, adjudication B.1): reader-schema lookup failures
 * feed a NEGATIVE CACHE (subject → failure expiry). While the entry is fresh the
 * caller (AvroWireFormat) falls back to the writer schema WITHOUT hitting the
 * registry — the per-record failure-call amplification during a registry outage is
 * bounded to one probe per TTL window. Convention key
 * {@code nop.stream.avro.reader-schema-fallback-ttl} (default 30s; production wiring
 * is a constructor-injected {@link Duration} — the client never reads global config;
 * WI18 reserved, same form as registry-request-timeout). {@code <= 0} TTL fails fast
 * at construction (2511-③ parameter discipline).
 *
 * <p>CRA 8.43 R4-03 W-03 (MA-11, adjudication B.2): the client is
 * {@link AutoCloseable} — {@link #close()} shuts the JDK {@link HttpClient}
 * (selector thread + connection pool). The CONSTRUCTOR owns the client's lifecycle:
 * the assembling side (job assembly / test host; WI18 connector wiring later) is
 * responsible for closing it at the lifecycle end.
 */
public class AvroSchemaRegistryClient implements AutoCloseable {

    /** Default request-level timeout when not injected (same order as connectTimeout). */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * CRA 8.43 R4-03 W-02: default negative-cache window for reader-schema lookup
     * failures (convention key
     * {@code nop.stream.avro.reader-schema-fallback-ttl}) — backoff收益 and
     * recovery-probe latency midpoint per adjudication B.1.
     */
    public static final Duration DEFAULT_READER_SCHEMA_FALLBACK_TTL = Duration.ofSeconds(30);

    private final HttpClient http;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Duration readerSchemaFallbackTtl;
    private final Map<Integer, Schema> schemaByIdCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSchema> latestBySubject = new ConcurrentHashMap<>();

    /**
     * CRA 8.43 R4-03 W-02: negative cache of reader-schema lookup failures
     * (subject → failure expiry epoch millis). The ENTRY set is the S4 seam.
     */
    private final ConcurrentHashMap<String, Long> readerSchemaFailures = new ConcurrentHashMap<>();

    /** CRA 8.43 R4-03 W-03: idempotent-close guard (caller discipline backstop). */
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

    /** id + parsed schema for a subject's latest version. */
    record CachedSchema(int id, Schema schema) {
    }

    public AvroSchemaRegistryClient(String baseUrl) {
        this(baseUrl, DEFAULT_REQUEST_TIMEOUT, DEFAULT_READER_SCHEMA_FALLBACK_TTL);
    }

    public AvroSchemaRegistryClient(String baseUrl, Duration requestTimeout) {
        this(baseUrl, requestTimeout, DEFAULT_READER_SCHEMA_FALLBACK_TTL);
    }

    /**
     * Full constructor (CRA 8.43 R4-03 W-02): request timeout + reader-schema
     * negative-cache TTL, both constructor-injected (the client never reads
     * global config). A non-positive TTL fails fast.
     */
    public AvroSchemaRegistryClient(String baseUrl, Duration requestTimeout,
                                    Duration readerSchemaFallbackTtl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new StreamAvroException("registry baseUrl must not be empty");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new StreamAvroException("registry request timeout must be positive, got: "
                    + requestTimeout);
        }
        if (readerSchemaFallbackTtl == null || readerSchemaFallbackTtl.isZero()
                || readerSchemaFallbackTtl.isNegative()) {
            throw new StreamAvroException("reader schema fallback ttl must be positive, got: "
                    + readerSchemaFallbackTtl
                    + " (convention key nop.stream.avro.reader-schema-fallback-ttl)");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.readerSchemaFallbackTtl = readerSchemaFallbackTtl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * CRA 8.43 R4-03 W-02: whether {@code subject} sits in the negative cache
     * with a still-fresh expiry (the caller falls back to the writer schema
     * WITHOUT a registry call while fresh).
     */
    public boolean isReaderSchemaFailureCached(String subject) {
        Long expiry = readerSchemaFailures.get(subject);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** CRA 8.43 R4-03 W-02: record a reader-schema lookup failure for {@code subject} (arms the TTL window). */
    public void markReaderSchemaFailure(String subject) {
        readerSchemaFailures.put(subject,
                System.currentTimeMillis() + readerSchemaFallbackTtl.toMillis());
    }

    /** CRA 8.43 R4-03 W-02: a successful latest-schema lookup clears the negative-cache entry (evolution reads recover). */
    public void clearReaderSchemaFailure(String subject) {
        readerSchemaFailures.remove(subject);
    }

    /**
     * CRA 8.43 R4-03 W-03 (MA-11, adjudication B.2): shuts the JDK HttpClient's
     * selector thread and connection pool. Idempotent (double close is a
     * no-op). The constructor-side owner of this client MUST call this at the
     * lifecycle end — the returned {@code AvroWireFormat} factory closures
     * keep the wire-format (and this client) alive otherwise.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            http.close();
        }
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
