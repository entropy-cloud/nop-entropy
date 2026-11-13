/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.core.lang.json.JsonTool;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.stream.core.exceptions.StreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED;

/**
 * Plan 2511 C8-001 (adjudication ⑥): per-record last-emitted position protocol for the
 * Debezium CDC source.
 *
 * <p>The engine's offset store advances when the engine handler RETURNS, which can be
 * up to {@code QUEUE_CAPACITY (256) + 1 handler-in-flight} records ahead of what the
 * reader actually emitted downstream — the pre-fix checkpoint lifted that store value
 * directly, so a crash silently dropped up to 256 committed-at-the-store but
 * never-emitted records (REPLAYABLE violation).
 *
 * <p>This tracker maintains the per-SERVER last-emitted position (engine offset
 * semantics are instance/server-level, not per-table — {@link DebeziumTableSplit}
 * javadoc): at the emission point the position is extracted from the event's
 * {@link ChangeEventMetadata} connector-specific fields, priority order
 * {@code lsn} (PostgreSQL) → {@code commitLsn} (SQL Server) →
 * {@code binlogPosition + binlogFile} (MySQL, both fields required). A null metadata
 * or an incomplete selected key form is a TYPED failure — never a fallback to an old
 * offset and never a silent skip (rule 24).
 *
 * <p>{@link #synthesizeOffsetMap()} renders the table as a
 * {@code NopStreamOffsetBackingStore.toSerializable}-compatible map (base64 JSON
 * partition keys / offset values): one engine-shaped entry per server (partition
 * {@code {"server":...}}, value {@code {"lsn":..}|{"commit_lsn":..}|{"file":..,"pos":..}})
 * plus a marker entry the restore side uses to identify last-emitted maps. JSON field
 * order is irrelevant to the engine's name-keyed offset lookup, so the synthesized
 * entries resume the engine from the last-EMITTED boundary. {@link #seedFromOffsetMap}
 * restores by taking the NUMERIC MINIMUM across split maps (conservative replay —
 *宁可重复不丢失), replacing the pre-fix "take the first one" arbitrariness.
 */
public class LastEmittedOffsetTracker implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(LastEmittedOffsetTracker.class);

    /** Marker partition key identifying a last-emitted-synthesized offset map. */
    static final String MARKER_JSON = "{\"nop.cdc.last-emitted\":true}";
    static final String MARKER_KEY = base64(MARKER_JSON);

    /** serverName → connector-specific offset fields (JSON-able, numeric/String values). */
    private final Map<String, Map<String, Object>> lastEmittedByServer = new LinkedHashMap<>();

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    /**
     * Records the position of an event that has just been emitted downstream. Typed
     * failure when the position is unavailable: metadata null (the converter returns
     * null metadata when the source block is missing) or the selected key form's
     * fields missing/incomplete (MySQL needs BOTH binlogPosition and binlogFile).
     */
    public void onEmitted(ChangeEvent event) {
        if (event == null) {
            return;
        }
        ChangeEventMetadata metadata = event.getMetadata();
        if (metadata == null) {
            throw asTypedFailure("metadata is null (converter source block missing)");
        }
        Map<String, Object> fields;
        if (metadata.getLsn() != null) {
            // PostgreSQL: lsn
            fields = new LinkedHashMap<>();
            fields.put("lsn", metadata.getLsn());
        } else if (metadata.getCommitLsn() != null) {
            // SQL Server: commit_lsn
            fields = new LinkedHashMap<>();
            fields.put("commit_lsn", metadata.getCommitLsn());
        } else if (metadata.getBinlogPosition() != null && metadata.getBinlogFile() != null) {
            // MySQL: file + pos, both fields required (incomplete form = typed failure)
            fields = new LinkedHashMap<>();
            fields.put("file", metadata.getBinlogFile());
            fields.put("pos", metadata.getBinlogPosition());
        } else {
            throw asTypedFailure("no usable position field (checked lsn -> commitLsn ->"
                    + " binlogPosition+binlogFile); metadata=" + metadata);
        }
        String server = metadata.getServerName();
        if (server == null || server.isEmpty()) {
            throw asTypedFailure("metadata serverName is empty; metadata=" + metadata);
        }
        synchronized (this) {
            // the LAST emitted event's position wins (the boundary tracks emission);
            // numeric min is a RESTORE-only rule (seedFromOffsetMap, conservative replay)
            lastEmittedByServer.put(server, fields);
        }
    }

    private StreamException asTypedFailure(String reason) {
        // rule 24: a position-less event must fail typed, never fall back to an old
        // offset and never be silently skipped — falling back would re-open the
        // ≤256-record silent-loss window this protocol closes
        return (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                .param(ARG_DETAIL, "C8-001: cannot extract last-emitted offset: " + reason);
    }

    /** Whether at least one emitted position has been recorded. */
    public synchronized boolean hasPositions() {
        return !lastEmittedByServer.isEmpty();
    }

    /** Snapshot of the per-server last-emitted positions. Test/ops visibility. */
    public synchronized Map<String, Map<String, Object>> snapshotPositions() {
        return new LinkedHashMap<>(lastEmittedByServer);
    }

    /**
     * Point-wise numeric minimum of two positions of the same key form (same
     * binlogFile compare pos; cross-file compare file names then pos — conservative).
     * Key forms are stable per server within a run (adjudication ⑥); if two restored
     * generations ever disagree on the form, the incoming map {@code b} is preferred
     * over mixing fields across forms.
     */
    static Map<String, Object> minPosition(Map<String, Object> a, Map<String, Object> b) {
        if (a.containsKey("lsn")) {
            if (!b.containsKey("lsn")) {
                return b;
            }
            return ((Number) a.get("lsn")).longValue() <= ((Number) b.get("lsn")).longValue() ? a : b;
        }
        if (a.containsKey("commit_lsn")) {
            if (!b.containsKey("commit_lsn")) {
                return b;
            }
            return ((Number) a.get("commit_lsn")).longValue() <= ((Number) b.get("commit_lsn")).longValue()
                    ? a : b;
        }
        String fileA = String.valueOf(a.get("file"));
        String fileB = String.valueOf(b.get("file"));
        int byFile = fileA.compareTo(fileB);
        if (byFile != 0) {
            return byFile < 0 ? a : b;
        }
        return ((Number) a.get("pos")).longValue() <= ((Number) b.get("pos")).longValue() ? a : b;
    }

    /**
     * Renders the table as a {@code NopStreamOffsetBackingStore.toSerializable}-compatible
     * map: a marker entry plus one engine-shaped (partition {@code {"server":...}} /
     * offset-value JSON) entry per server.
     */
    public synchronized TreeMap<String, String> synthesizeOffsetMap() {
        TreeMap<String, String> map = new TreeMap<>();
        map.put(MARKER_KEY, base64(JsonTool.stringify(lastEmittedByServer)));
        for (Map.Entry<String, Map<String, Object>> entry : lastEmittedByServer.entrySet()) {
            Map<String, Object> partition = new LinkedHashMap<>();
            partition.put("server", entry.getKey());
            map.put(base64(JsonTool.stringify(partition)), base64(JsonTool.stringify(entry.getValue())));
        }
        return map;
    }

    /**
     * Seeds/merges positions from a restored offset map. Maps WITHOUT the marker
     * (pre-fix engine-store snapshots) are ignored — the restore side falls back to
     * the legacy passthrough for those (legacy checkpoint compatibility, logged by
     * the caller), never silently advanced.
     */
    @SuppressWarnings("unchecked")
    public synchronized void seedFromOffsetMap(Map<String, String> offsetMap) {
        if (offsetMap == null || !offsetMap.containsKey(MARKER_KEY)) {
            return;
        }
        try {
            Map<String, Object> decoded = (Map<String, Object>) JsonTool.parseNonStrict(
                    decode(offsetMap.get(MARKER_KEY)));
            for (Map.Entry<String, Object> entry : decoded.entrySet()) {
                Map<String, Object> fields = (Map<String, Object>) entry.getValue();
                Map<String, Object> existing = lastEmittedByServer.get(entry.getKey());
                lastEmittedByServer.put(entry.getKey(),
                        existing == null ? fields : minPosition(existing, fields));
            }
        } catch (Exception e) {
            // an undecodable marker entry must not masquerade as a healthy seed —
            // leave the table untouched (the caller keeps its legacy fallback)
            LOG.warn("nop.stream.debezium.last-emitted-seed-fail", e);
        }
    }
}
