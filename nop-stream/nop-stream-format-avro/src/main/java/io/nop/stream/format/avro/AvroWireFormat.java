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
 * {@code KafkaTwoPhaseCommitSink.valueSerializer}).
 */
public class AvroWireFormat {

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
     */
    public byte[] encode(GenericRecord record, String subject) {
        int schemaId = writerSchemaIds.computeIfAbsent(subject, s -> {
            int id = client.registerSchema(s, record.getSchema());
            // cache for reads routed by this id
            client.getSchemaById(id);
            return id;
        });
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
     * Reader schema resolution: prefers the latest registered schema for the subject;
     * falls back to the writer schema itself when the subject has no registered
     * latest (first-write-before-register race in some producers).
     */
    private Schema readerSchemaFor(String subject, Schema writerSchema) {
        try {
            return client.getLatestSchema(subject).schema();
        } catch (StreamAvroException e) {
            return writerSchema;
        }
    }

    // ==================== connector injection factories ====================

    /** Deserializer for {@code KafkaSourceFunction.valueDeserializer}. */
    public java.util.function.Function<byte[], GenericRecord> avroDeserializer(String subject) {
        return bytes -> decode(bytes, subject);
    }

    /** Serializer for {@code KafkaTwoPhaseCommitSink.valueSerializer}. */
    public java.util.function.Function<GenericRecord, byte[]> avroSerializer(String subject) {
        return record -> encode(record, subject);
    }
}
