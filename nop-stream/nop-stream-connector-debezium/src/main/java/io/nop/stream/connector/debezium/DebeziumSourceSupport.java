/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.util.ICancellable;
import io.nop.credential.api.ICredentialProvider;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;

import io.nop.stream.core.credentials.StreamCredentialSupport;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONFIG_ERROR;

/**
 * WI2 (plan 2343 Phase 3): sharedDebezium instance-scoping logic extracted from the
 * legacy {@link DebeziumCdcSourceFunction} (which delegates — its observable behavior is
 * unchanged) so the FLIP-27 reader can compose the SAME semantics. The combination path
 * {@link #buildInstanceConfig} is NEW: an instance-scoped config carrying an ARBITRARY
 * table set (the legacy path only ever derived the table set from hash routing), which
 * is what the split-based enumerator assignment needs (adjudication R2: 表 = split).
 */
final class DebeziumSourceSupport {

    private static final String FIELD_DATABASE_USER = "databaseUser";
    private static final String FIELD_DATABASE_PASSWORD = "databasePassword";
    private static final String FIELD_SCHEMA_HISTORY_USER = "schemaHistoryJdbcUser";
    private static final String FIELD_SCHEMA_HISTORY_PASSWORD = "schemaHistoryJdbcPassword";

    private DebeziumSourceSupport() {
    }

    // ---------------- table routing primitives (legacy parity) ----------------

    static List<String> includeListEntries(String includeList) {
        if (includeList == null || includeList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> entries = new ArrayList<>();
        for (String entry : includeList.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    /** floor-mod of the stable JLS {@code String.hashCode()} — deterministic, negative-safe. */
    static int shardOf(String tableEntry, int totalParallelism) {
        return Math.floorMod(tableEntry.hashCode(), totalParallelism);
    }

    static List<String> route(List<String> tables, int totalParallelism, int subtaskIndex) {
        List<String> routed = new ArrayList<>();
        for (String table : tables) {
            if (shardOf(table, totalParallelism) == subtaskIndex) {
                routed.add(table);
            }
        }
        return routed;
    }

    static String instanceName(String baseName, int subtaskIndex, int totalParallelism) {
        return totalParallelism > 1 ? baseName + "-" + subtaskIndex : baseName;
    }

    // ---------------- instance qualification (schema history / server name) ----------------

    static void qualifySchemaHistoryPerInstance(DebeziumConfig config, String instance) {
        String path = config.getSchemaHistoryPath();
        if (path != null && !path.isEmpty()) {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            java.nio.file.Path parent = p.getParent();
            config.setSchemaHistoryPath((parent == null ? "" : parent + "/")
                    + instance + "-" + p.getFileName());
        }
        String table = config.getSchemaHistoryJdbcTable();
        // R5 REG-3: suffix with the FULL sanitized instance name (see legacy comment).
        config.setSchemaHistoryJdbcTable(
                (table == null || table.isEmpty() ? "nop_cdc_schema_history" : table)
                        + "_" + sanitizeIdentifier(instance));
    }

    static String sanitizeIdentifier(String instance) {
        return instance.replaceAll("[^A-Za-z0-9]", "_");
    }

    // ---------------- credential reference handling (D4) ----------------

    static boolean hasCredentialReferences(DebeziumConfig config) {
        return StreamCredentialSupport.isCredentialReference(config.getDatabaseUser())
                || StreamCredentialSupport.isCredentialReference(config.getDatabasePassword())
                || StreamCredentialSupport.isCredentialReference(config.getSchemaHistoryJdbcUser())
                || StreamCredentialSupport.isCredentialReference(config.getSchemaHistoryJdbcPassword());
    }

    static String resolveIfReference(String value, String field, ICredentialProvider provider) {
        if (StreamCredentialSupport.isCredentialReference(value)) {
            return StreamCredentialSupport.resolve(value, field, provider);
        }
        return value;
    }

    static void verifyCredentialReferences(DebeziumConfig config, ICredentialProvider provider) {
        resolveIfReference(config.getDatabaseUser(), FIELD_DATABASE_USER, provider);
        resolveIfReference(config.getDatabasePassword(), FIELD_DATABASE_PASSWORD, provider);
        resolveIfReference(config.getSchemaHistoryJdbcUser(), FIELD_SCHEMA_HISTORY_USER, provider);
        resolveIfReference(config.getSchemaHistoryJdbcPassword(), FIELD_SCHEMA_HISTORY_PASSWORD,
                provider);
    }

    /**
     * The engine-visible config for an instance: the ORIGINAL config when it carries no
     * credential references; otherwise a TRANSIENT DECRYPTED COPY (serialization
     * round-trip — D4: reference persists on every persistent path, plaintext is
     * engine-side transient only).
     */
    static DebeziumConfig decryptedCopyIfReferenced(DebeziumConfig config,
                                                    ICredentialProvider provider) {
        if (!hasCredentialReferences(config)) {
            return config;
        }
        DebeziumConfig result = serializationRoundTripCopy(config);
        result.setDatabaseUser(resolveIfReference(config.getDatabaseUser(), FIELD_DATABASE_USER, provider));
        result.setDatabasePassword(resolveIfReference(config.getDatabasePassword(), FIELD_DATABASE_PASSWORD, provider));
        result.setSchemaHistoryJdbcUser(resolveIfReference(config.getSchemaHistoryJdbcUser(),
                FIELD_SCHEMA_HISTORY_USER, provider));
        result.setSchemaHistoryJdbcPassword(resolveIfReference(config.getSchemaHistoryJdbcPassword(),
                FIELD_SCHEMA_HISTORY_PASSWORD, provider));
        return result;
    }

    static void checkConnection(DebeziumConfig config, ICredentialProvider provider) {
        if (config == null) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param("argName", (Object) "config");
        }
        resolveConnectorName(config);
        verifyCredentialReferences(config, provider);
    }

    static String resolveConnectorName(DebeziumConfig config) {
        if (config != null && config.getName() != null && !config.getName().isEmpty()) {
            return config.getName();
        }
        throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                .param(ARG_DETAIL, "Debezium connector name is required: set DebeziumConfig.name so the "
                        + "offset registry bucket is unique per connector");
    }

    /**
     * WI2 plan 2343 Phase 3 (M-1 combination path): an instance-scoped engine config with
     * an ARBITRARY table set — the split-assignment shape the legacy hash routing could
     * never express. Every instance-scoping rule from the legacy B-layer block applies:
     * unique connector name (offset registry bucket, failure listener registry, Debezium
     * {@code name} property), serverName instance-qualification (JMX ObjectName/topic
     * prefix), and instance-qualified schema history (file name / JDBC table). The
     * original config object is never mutated.
     */
    static DebeziumConfig buildInstanceConfig(DebeziumConfig base, String instance,
                                              List<String> assignedTables,
                                              ICredentialProvider provider) {
        DebeziumConfig result = decryptedCopyIfReferenced(base, provider);
        if (result == base) {
            result = serializationRoundTripCopy(base);
        }
        result.setName(instance);
        result.setTableIncludeList(String.join(",", assignedTables));
        result.setServerName(instance);
        qualifySchemaHistoryPerInstance(result, instance);
        return result;
    }

    static <X> X serializationRoundTripCopy(X obj) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
                oos.writeObject(obj);
            }
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bos.toByteArray()))) {
                @SuppressWarnings("unchecked")
                X copy = (X) ois.readObject();
                return copy;
            }
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR, e)
                    .param(ARG_DETAIL, "failed to build the serialization round-trip copy");
        }
    }
}
