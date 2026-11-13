/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 */
package io.nop.stream.format.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WI18 (plan 2341): Confluent wire format codec over Avro GenericRecord —
 * {@code magic byte 0x0 + 4-byte schemaId (big-endian) + Avro binary payload}.
 *
 * <p>Schemas are registered on first write (idempotent server-side) and resolved by
 * id on read; both are cached. The {@code subject} follows the Confluent topic-name
 * strategy convention ({@code <topic>-value} / {@code <topic>-key}).
 *
 * <p>Factory methods {@link #avroDeserializer(String)} and {@link #avroSerializer(String)}
 * produce {@code Function} instances directly injectable into the stream connector
 * surface ({@code KafkaSourceFunction.valueDeserializer} /
 * {@code KafkaTwoPhaseCommitSink.valueSerializer}). The returned closures hold THIS
 * wire-format and its registry client — the assembling side (job assembly / test
 * host; WI18 connector wiring later) is responsible for calling {@link #close()}
 * at the lifecycle end (CRA 8.43 R4-03 W-03 caller-obligation declaration;
 * WI18 lands the host close-chain wiring together with the registry-request-timeout
 * production wiring).
 *
 * <p>CRA 8.43 R4-03 W-02 (MA-10, adjudication B.1): reader-schema resolution
 * degrades NON-silently — a registry failure falls back to the writer schema behind
 * a negative-cache window (one probe per TTL, WARN once per window per subject;
 * subject-missing 404 is the legitimate first-write race carve-out and falls back
 * without WARN). The fallback is a bounded availability form, not a silent
 * downgrade (owner doc failure-face contract stays typed elsewhere).
 */
public class AvroWireFormat implements AutoCloseable {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AvroWireFormat.class);

    private static final byte MAGIC_BYTE = 0x0;

    private final AvroSchemaRegistryClient client;

    /** subject → writer schema id (resolved once per subject, then cached). */
    private final Map<String, Integer> writerSchemaIds = new ConcurrentHashMap<>();

    public AvroWireFormat(AvroSchemaRegistryClient client) {
        if (client == null) {
            throw new StreamAvroException("registry client must not be null");
        }
        this.client = client;
    }

    /**
     * Encodes a GenericRecord into Confluent wire format: registers the record's
     * schema under the subject on first use, then writes magic + schemaId + binary.
     *
     * <p>Plan 2511 C2-002: the registry calls (POST register + id cache warm-up) run
     * OUTSIDE the {@code writerSchemaIds} CHM bin locks — get → miss → fetch →
     * {@code putIfAbsent} idempotent backfill. A hung registry can therefore delay at
     * most the fetching thread (bounded by the client's request timeout), never a
     * whole bin of concurrent encoders.
     */
    public byte[] encode(GenericRecord record, String subject) {
        Integer cached = writerSchemaIds.get(subject);
        if (cached == null) {
            int id = client.registerSchema(subject, record.getSchema());
            // cache for reads routed by this id
            client.getSchemaById(id);
            Integer race = writerSchemaIds.putIfAbsent(subject, id);
            cached = race != null ? race : id;
        }
        int schemaId = cached;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC_BYTE);
        out.write((schemaId >>> 24) & 0xFF);
        out.write((schemaId >>> 16) & 0xFF);
        out.write((schemaId >>> 8) & 0xFF);
        out.write(schemaId & 0xFF);
        try {
            GenericDatumWriter<GenericRecord> writer =
                    new GenericDatumWriter<>(record.getSchema());
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(record, encoder);
            encoder.flush();
        } catch (IOException e) {
            throw new StreamAvroException("Avro encode failed for subject " + subject
                    + ": " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /**
     * Decodes Confluent wire format into a GenericRecord: validates the magic byte,
     * resolves the writer schema by the embedded id, and applies the latest reader
     * schema for the subject (schema evolution via Avro's resolution rules).
     */
    public GenericRecord decode(byte[] bytes, String subject) {
        if (bytes == null || bytes.length < 5 || bytes[0] != MAGIC_BYTE) {
            throw new StreamAvroException("invalid Confluent wire format: expected magic byte 0x0 "
                    + "+ 4-byte schema id, got " + (bytes == null ? "null" : bytes.length + " bytes"));
        }
        int schemaId = ((bytes[1] & 0xFF) << 24) | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 8) | (bytes[4] & 0xFF);
        Schema writerSchema = client.getSchemaById(schemaId);
        Schema readerSchema = readerSchemaFor(subject, writerSchema);
        try {
            GenericDatumReader<GenericRecord> reader =
                    new GenericDatumReader<>(writerSchema, readerSchema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                    bytes, 5, bytes.length - 5, null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new StreamAvroException("Avro decode failed for subject " + subject
                    + " (schemaId " + schemaId + "): " + e.getMessage(), e);
        }
    }

    /**
     * CRA 8.43 R4-03 W-02 (adjudication B.1): reader-schema resolution with a
     * negative-cache backoff. Fresh negative-cache entry → fall back to the
     * writer schema with ZERO registry calls (the per-record failure-call
     * amplification during a registry outage is bounded to one probe per TTL
     * window). Miss or expired entry → one typed registry probe
     * ({@code getLatestSchema} keeps the 2511-③ request timeout + outside-lock
     * fetch contract): success clears the negative entry and restores evolution
     * reads; failure records the entry and falls back with a WARN once per
     * window per subject (HTTP 404 subject-missing = legitimate first-write
     * race → carve-out: falls back silently, still behind the backoff window).
     * The fallback is NOT silent: every window boundary WARNs (or is exempted
     * by the carve-out) and the window is bounded — the two halves of the
     * MA-10 defect (call amplification / zero signal) are closed together.
     */
    private Schema readerSchemaFor(String subject, Schema writerSchema) {
        if (client.isReaderSchemaFailureCached(subject)) {
            // Negative cache fresh: zero registry calls, zero new WARN — the
            // rate-limited fallback window.
            return writerSchema;
        }
        try {
            Schema latest = client.getLatestSchema(subject).schema();
            client.clearReaderSchemaFailure(subject);
            return latest;
        } catch (StreamAvroException e) {
            client.markReaderSchemaFailure(subject);
            if (isSubjectMissing(e)) {
                // F-1 carve-out: an unregistered subject is the legitimate
                // first-write-before-register race in some producers — falling
                // back is the correct steady state (self-heals within one TTL
                // once the subject registers); no WARN storm for an expected
                // condition.
                LOG.debug("reader schema for subject {} not registered yet (HTTP 404); "
                        + "falling back to writer schema for the TTL window", subject);
            } else {
                LOG.warn("reader schema lookup failed for subject {}; falling back to the "
                        + "writer schema for nop.stream.avro.reader-schema-fallback-ttl "
                        + "(schema evolution reads pause for the window): {}",
                        subject, e.toString());
            }
            return writerSchema;
        }
    }

    /** Subject-missing carve-out detection: the registry answered 404 for the subject's latest version. */
    private static boolean isSubjectMissing(StreamAvroException e) {
        String message = e.getMessage();
        return message != null && message.contains("HTTP 404");
    }

    // ==================== connector injection factories ====================

    /**
     * Deserializer for {@code KafkaSourceFunction.valueDeserializer}. Lifecycle:
     * the closure holds this wire-format and its registry client — the
     * assembling side must call {@link #close()} at the lifecycle end
     * (CRA 8.43 R4-03 W-03; WI18 lands the host close-chain wiring).
     */
    public java.util.function.Function<byte[], GenericRecord> avroDeserializer(String subject) {
        return bytes -> decode(bytes, subject);
    }

    /**
     * Serializer for {@code KafkaTwoPhaseCommitSink.valueSerializer}. Lifecycle:
     * the closure holds this wire-format and its registry client — the
     * assembling side must call {@link #close()} at the lifecycle end
     * (CRA 8.43 R4-03 W-03; WI18 lands the host close-chain wiring).
     */
    public java.util.function.Function<GenericRecord, byte[]> avroSerializer(String subject) {
        return record -> encode(record, subject);
    }

    /**
     * CRA 8.43 R4-03 W-03 (adjudication B.2): transparent close of the owned
     * registry client (single-holder semantics — this wire-format received the
     * client by constructor injection and is its lifecycle owner). Shuts the
     * HttpClient selector thread + connection pool. Idempotent (delegates to
     * the client's guarded close).
     */
    @Override
    public void close() {
        client.close();
    }
}
