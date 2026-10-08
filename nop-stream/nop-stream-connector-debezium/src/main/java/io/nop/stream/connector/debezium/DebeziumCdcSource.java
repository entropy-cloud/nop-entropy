/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.nop.credential.api.ICredentialProvider;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;

import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;

/**
 * WI2 (plan 2343 Phase 3): FLIP-27 form of the debezium-cdc source — the connector
 * factory now produces this class; the legacy {@code DebeziumCdcSourceFunction} keeps
 * its programmatic API (data-migration orchestrator, fraud-example bean) but is no
 * longer produced by the factory. This completes adjudication R2/R3: 表 = split, the
 * B-layer {@code SubtaskShardedSourceFunction} exception is revoked on the factory
 * surface, and the R2 semantic baseline (REPLAYABLE offset checkpoint / parallel /
 * instance-level offset isolation) holds through {@link DebeziumCdcSourceReader}.
 */
public class DebeziumCdcSource implements Source<ChangeEvent, DebeziumTableSplit, DebeziumCdcEnumeratorState>,
        ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private final DebeziumConfig config;
    private final ICredentialProvider credentialProvider;
    /** WI9 first-run offset seed (data-migration orchestrator contract). */
    private Map<String, String> initialOffsets;

    public DebeziumCdcSource(DebeziumConfig config) {
        this(config, null);
    }

    public DebeziumCdcSource(DebeziumConfig config, ICredentialProvider credentialProvider) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.config = config;
        this.credentialProvider = credentialProvider;
    }

    /**
     * WI9 first-run offset seeding (data-migration pipeline §4.2): applied before
     * deployment; effective only on the true first run of each reader instance.
     */
    public void setInitialOffsets(Map<String, String> initialOffsets) {
        this.initialOffsets = initialOffsets;
    }

    DebeziumConfig getConfigForTest() {
        return config;
    }

    @Override
    public SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> createEnumerator() {
        return new DebeziumTableSplitEnumerator(
                DebeziumSourceSupport.includeListEntries(config.getTableIncludeList()));
    }

    @Override
    public SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> restoreEnumerator(
            DebeziumCdcEnumeratorState state) {
        DebeziumTableSplitEnumerator enumerator = new DebeziumTableSplitEnumerator(
                DebeziumSourceSupport.includeListEntries(config.getTableIncludeList()));
        enumerator.restoreState(state);
        return enumerator;
    }

    @Override
    public SourceReader<ChangeEvent, DebeziumTableSplit> createReader(SourceReaderContext ctx) {
        DebeziumCdcSourceReader reader = new DebeziumCdcSourceReader(config, credentialProvider,
                initialOffsets);
        reader.setContextForTest(ctx);
        return reader;
    }

    @Override
    public SimpleVersionedSerializer<DebeziumCdcEnumeratorState> getEnumeratorStateSerializer() {
        return new EnumeratorStateSerializer();
    }

    @Override
    public SimpleVersionedSerializer<DebeziumTableSplit> getSplitSerializer() {
        return new TableSplitSerializer();
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public void checkConnection() {
        DebeziumSourceSupport.checkConnection(config, credentialProvider);
    }

    // ====================== Serializers ======================

    /**
     * Encodes the split as JSON {@code {"table":..., "offsets":{...}|absent}} via
     * JsonTool (values are base64 strings — JSON-safe by construction).
     */
    static final class TableSplitSerializer implements SimpleVersionedSerializer<DebeziumTableSplit> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(DebeziumTableSplit split) throws IOException {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("table", split.getTableEntry());
            if (split.isOffsetCarrying() && split.getOffsetMap() != null) {
                payload.put("offsets", split.getOffsetMap());
            }
            String text = io.nop.core.lang.json.JsonTool.serialize(payload, false);
            if (text.indexOf('\n') >= 0) {
                // single-line payload discipline (mirror the file-split serializer)
                throw new IOException("DebeziumTableSplit payload contains a newline");
            }
            return text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        @SuppressWarnings("unchecked")
        public DebeziumTableSplit deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported DebeziumTableSplit serializer version: " + version);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            Map<String, Object> payload;
            try {
                payload = (Map<String, Object>) io.nop.core.lang.json.JsonTool.parseBeanFromText(
                        text, Object.class);
            } catch (Exception e) {
                throw new IOException("Malformed DebeziumTableSplit payload: " + text, e);
            }
            Object table = payload == null ? null : payload.get("table");
            if (!(table instanceof String) || ((String) table).isEmpty()) {
                throw new IOException("DebeziumTableSplit payload has no table entry: " + text);
            }
            Object offsets = payload == null ? null : payload.get("offsets");
            if (offsets instanceof Map) {
                Map<String, String> map = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> en : ((Map<String, Object>) offsets).entrySet()) {
                    map.put(en.getKey(), String.valueOf(en.getValue()));
                }
                return new DebeziumTableSplit((String) table, map, true);
            }
            return new DebeziumTableSplit((String) table);
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }

    /** Encodes the enumerator state as JSON. Versioned at 1. */
    static final class EnumeratorStateSerializer
            implements SimpleVersionedSerializer<DebeziumCdcEnumeratorState> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(DebeziumCdcEnumeratorState state) throws IOException {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("unassignedTables", state.getUnassignedTables());
            payload.put("nextSubtaskIndex", state.getNextSubtaskIndex());
            return io.nop.core.lang.json.JsonTool.serialize(payload, false)
                    .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public DebeziumCdcEnumeratorState deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported DebeziumCdcEnumeratorState serializer version: "
                        + version);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            try {
                Map<String, Object> payload =
                        (Map<String, Object>) io.nop.core.lang.json.JsonTool.parseBeanFromText(
                                text, Object.class);
                DebeziumCdcEnumeratorState state = new DebeziumCdcEnumeratorState();
                Object unassigned = payload == null ? null : payload.get("unassignedTables");
                if (unassigned instanceof List) {
                    List<String> tables = new java.util.ArrayList<>();
                    for (Object t : (List<Object>) unassigned) {
                        tables.add(String.valueOf(t));
                    }
                    state.setUnassignedTables(tables);
                }
                Object next = payload == null ? null : payload.get("nextSubtaskIndex");
                if (next instanceof Number) {
                    state.setNextSubtaskIndex(((Number) next).intValue());
                }
                return state;
            } catch (Exception e) {
                throw new IOException("Malformed DebeziumCdcEnumeratorState payload: " + text, e);
            }
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }
}
