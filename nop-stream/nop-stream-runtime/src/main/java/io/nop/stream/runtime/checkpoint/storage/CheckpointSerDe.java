/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointFormatVersions;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.incremental.SstFileChecksum;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CURRENT_FORMAT_VERSION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_FORMAT_VERSION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_JOB_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_FORMAT_VERSION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

@Internal
public class CheckpointSerDe {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointSerDe.class);

    /**
     * Stage 29 (G59): format version envelope. {@code 1} = legacy (no marker in JSON, treated as
     * v1 on read). {@code 2} = current (explicit {@code formatVersion} field present). Future
     * format changes bump this number; {@link #deserializeCheckpoint} and
     * {@link #deserializeEpochManifest} log a debug message and accept legacy v1 JSON.
     *
     * <p>Stage 51 (roadmap item 25): the canonical constants live in core
     * {@link CheckpointFormatVersions} (single version truth — runtime aliases them so a
     * second independent number is structurally impossible).
     */
    public static final int CURRENT_FORMAT_VERSION = CheckpointFormatVersions.CURRENT_FORMAT_VERSION;
    public static final int LEGACY_FORMAT_VERSION = CheckpointFormatVersions.LEGACY_FORMAT_VERSION;
    private static final String FORMAT_VERSION_KEY = "formatVersion";

    /** Stage 51: manifest-level self-describing state format version field key. */
    static final String STATE_FORMAT_VERSION_KEY = "stateFormatVersion";

    /** Stage 51: manifest integrity checksum field key. Always the LAST key in the document. */
    static final String CHECKSUM_KEY = "checksum";

    /**
     * Stage 51: canonical field order for the epoch manifest top-level map. The store-side
     * assembly follows this order; the load-side checksum recomputation re-orders the parsed
     * map through the same list, so both sides hash an identical canonical form. Unknown
     * (forward-compat) keys are appended after the known ones in document order.
     */
    static final java.util.List<String> MANIFEST_FIELD_ORDER = java.util.List.of(
            FORMAT_VERSION_KEY, STATE_FORMAT_VERSION_KEY,
            "epochId", "jobId", "pipelineId", "timestamp",
            "checkpointType", "state", "taskSnapshots", "streamModelFingerprint",
            "segments", "sourceEnumeratorSnapshots");

    /**
     * F-10b (plan 2026-09-04-1326-3): canonical field order for the {@code .checkpoint}
     * body top-level map — the same canonical-checksum mechanism Stage 51 established
     * for the epoch manifest, extended to the checkpoint body (which previously carried
     * NO integrity protection on the main restore path).
     */
    static final java.util.List<String> CHECKPOINT_FIELD_ORDER = java.util.List.of(
            FORMAT_VERSION_KEY, "jobId", "pipelineId", "checkpointId",
            "triggerTimestamp", "completedTimestamp", "checkpointType", "restored",
            "taskStates");

    public static byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put(FORMAT_VERSION_KEY, CURRENT_FORMAT_VERSION);
        serializable.put("jobId", checkpoint.getJobId());
        serializable.put("pipelineId", checkpoint.getPipelineId());
        serializable.put("checkpointId", checkpoint.getCheckpointId());
        serializable.put("triggerTimestamp", checkpoint.getTriggerTimestamp());
        serializable.put("completedTimestamp", checkpoint.getCompletedTimestamp());
        serializable.put("checkpointType", checkpoint.getCheckpointType().name());
        serializable.put("restored", checkpoint.isRestored());

        Map<String, Object> taskStatesMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : checkpoint.getTaskStates().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            taskStatesMap.put(key, serializeTaskStateSnapshot(entry.getValue()));
        }
        serializable.put("taskStates", taskStatesMap);

        // F-10b: body checksum stamped at this choke point (same canonical mechanism as
        // the epoch manifest — re-serialization always recomputes from content).
        serializable.put(CHECKSUM_KEY, computeCanonicalChecksumHex(serializable, CHECKPOINT_FIELD_ORDER));
        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    public static CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        int formatVersion = detectFormatVersion(map);
        if (formatVersion < CURRENT_FORMAT_VERSION) {
            LOG.debug("Deserializing legacy checkpoint (formatVersion={}, current={}) — backward-compatible",
                    formatVersion, CURRENT_FORMAT_VERSION);
        }
        // Stage 51: an envelope version above the current one is a future format whose
        // semantics this runtime cannot interpret — fail fast instead of silently accepting
        // (previously the value passed through unchecked).
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            throw unsupportedFormatVersion(map, formatVersion, CheckpointFormatVersions.UNSET_FORMAT_VERSION);
        }

        // F-10b: body checksum verification BEFORE any value is consumed — present means
        // verify (typed fail-fast on mismatch, same semantics as the Stage 51 manifest
        // checksum), absent means a legacy body written before the checksum was stamped
        // (adjudicated legacy tolerance, debug-logged like the version tolerance above).
        // This closes the integrity gap on the MAIN restore path: a tampered/truncated
        // `.checkpoint` body is rejected before any embedded payload reaches the native
        // deserialization path.
        Object checksumObj = map.get(CHECKSUM_KEY);
        if (checksumObj != null) {
            if (!(checksumObj instanceof String)) {
                throw checkpointChecksumMismatch(map, String.valueOf(checksumObj), "non-string checksum value");
            }
            String stored = (String) checksumObj;
            Map<String, Object> payloadOnly = new LinkedHashMap<>(map);
            payloadOnly.remove(CHECKSUM_KEY);
            String recomputed = computeCanonicalChecksumHex(payloadOnly, CHECKPOINT_FIELD_ORDER);
            if (!stored.equals(recomputed)) {
                throw checkpointChecksumMismatch(map, stored, recomputed);
            }
        } else {
            LOG.debug("Checkpoint body carries no checksum (pre-checksum legacy bytes) — "
                    + "skipping integrity verification");
        }

        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        Long checkpointId = map.get("checkpointId") instanceof Number ? ((Number) map.get("checkpointId")).longValue() : null;
        Long triggerTimestamp = map.get("triggerTimestamp") instanceof Number ? ((Number) map.get("triggerTimestamp")).longValue() : null;
        Long completedTimestamp = map.get("completedTimestamp") instanceof Number ? ((Number) map.get("completedTimestamp")).longValue() : null;
        if (jobId == null || pipelineId == null || checkpointId == null
                || triggerTimestamp == null || completedTimestamp == null) {
            LOG.warn("Checkpoint data missing required fields, skipping deserialization");
            return null;
        }
        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : CheckpointType.CHECKPOINT;
        Boolean restored = (Boolean) map.get("restored");

        Map<String, Object> taskStatesMap = map.get("taskStates") instanceof Map
                ? (Map<String, Object>) map.get("taskStates") : null;
        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        if (taskStatesMap != null) {
            for (Map.Entry<String, Object> entry : taskStatesMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    LOG.warn("Failed to parse TaskLocation from key '{}', using fallback", entry.getKey(), e);
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                if (!(entry.getValue() instanceof Map)) {
                    LOG.warn("Skipping non-map task state for key '{}'", entry.getKey());
                    continue;
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskStates.put(taskLocation, snapshot);
            }
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(triggerTimestamp)
                .completedTimestamp(completedTimestamp)
                .checkpointType(checkpointType)
                .taskStates(taskStates)
                .build();

        if (restored != null) {
            checkpoint.setRestored(restored);
        }

        return checkpoint;
    }

    /**
     * Stage 51: serializes the manifest with the self-describing state format version and the
     * integrity checksum stamped at this choke point (both storages go through here). The
     * canonical map follows {@link #MANIFEST_FIELD_ORDER}; the checksum covers that map minus
     * the {@code checksum} key itself and is appended as the LAST key. Re-serialization always
     * recomputes the checksum from content — a stale value from a previously deserialized
     * manifest is never copied.
     */
    public static byte[] serializeEpochManifest(EpochManifest manifest) {
        Map<String, Object> serializable = buildEpochManifestMap(manifest);
        String checksum = computeCanonicalChecksumHex(serializable, MANIFEST_FIELD_ORDER);
        serializable.put(CHECKSUM_KEY, checksum);
        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Stage 51: single canonical assembly path for the manifest top-level map (fixed field
     * order, no checksum key). The state format version is stamped with the single version
     * truth {@link #CURRENT_FORMAT_VERSION} — the output of THIS writer is by definition in
     * the current state format.
     */
    private static Map<String, Object> buildEpochManifestMap(EpochManifest manifest) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put(FORMAT_VERSION_KEY, CURRENT_FORMAT_VERSION);
        serializable.put(STATE_FORMAT_VERSION_KEY, CURRENT_FORMAT_VERSION);
        serializable.put("epochId", manifest.getEpochId());
        serializable.put("jobId", manifest.getJobId());
        serializable.put("pipelineId", manifest.getPipelineId());
        serializable.put("timestamp", manifest.getTimestamp());
        if (manifest.getCheckpointType() != null) {
            serializable.put("checkpointType", manifest.getCheckpointType().name());
        }
        if (manifest.getState() != null) {
            serializable.put("state", manifest.getState().name());
        }

        Map<String, Object> taskSnapshotsMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : manifest.getTaskSnapshots().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            taskSnapshotsMap.put(key, serializeTaskStateSnapshot(entry.getValue()));
        }
        serializable.put("taskSnapshots", taskSnapshotsMap);

        if (manifest.getStreamModelFingerprint() != null) {
            StreamModelFingerprint fp = manifest.getStreamModelFingerprint();
            Map<String, Object> fpMap = new LinkedHashMap<>();
            fpMap.put("version", fp.getVersion());
            fpMap.put("dagTopologyHash", fp.getDagTopologyHash());
            fpMap.put("requirementsHash", fp.getRequirementsHash());
            fpMap.put("checkpointParticipantsHash", fp.getCheckpointParticipantsHash());
            fpMap.put("componentHashes", fp.getComponentHashes());
            serializable.put("streamModelFingerprint", fpMap);
        }

        if (manifest.getSegments() != null && !manifest.getSegments().isEmpty()) {
            java.util.List<Map<String, Object>> segmentsList = new java.util.ArrayList<>();
            for (StateSegmentDescriptor seg : manifest.getSegments()) {
                Map<String, Object> segMap = new LinkedHashMap<>();
                segMap.put("segmentType", seg.getSegmentType());
                segMap.put("path", seg.getPath());
                segMap.put("codec", seg.getCodec());
                segMap.put("checksum", seg.getChecksum());
                segMap.put("schemaVersion", seg.getSchemaVersion());
                segmentsList.add(segMap);
            }
            serializable.put("segments", segmentsList);
        }

        // Stage 49 D2: serialize per-source-vertex enumerator snapshots when present.
        // Backward compatible: absent on jobs without split-based sources / legacy checkpoints.
        if (manifest.getSourceEnumeratorSnapshots() != null && !manifest.getSourceEnumeratorSnapshots().isEmpty()) {
            Map<String, Object> enumeratorMap = new LinkedHashMap<>();
            for (Map.Entry<String, SourceEnumeratorSnapshot> entry : manifest.getSourceEnumeratorSnapshots().entrySet()) {
                SourceEnumeratorSnapshot snap = entry.getValue();
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("version", snap.getVersion());
                entryMap.put("stateBytes", java.util.Base64.getEncoder().encodeToString(snap.getStateBytes() != null ? snap.getStateBytes() : new byte[0]));
                enumeratorMap.put(entry.getKey(), entryMap);
            }
            serializable.put("sourceEnumeratorSnapshots", enumeratorMap);
        }
        return serializable;
    }

    /**
     * Stage 51: canonical integrity checksum of a manifest map (must NOT contain the
     * {@code checksum} key). Defined so that store-side hashing and load-side recomputation
     * converge on identical bytes by construction:
     *
     * <ul>
     *   <li>The map is first re-ordered through {@link #canonicalizeFieldOrder} — the
     *       shared fixed-field-order canonical form (store-side assembly already follows it;
     *       load-side parsed maps are re-ordered defensively through the same function).</li>
     *   <li>One JSON text round-trip ({@code serialize -> parseMap -> serialize}) normalizes
     *       number representations to their {@code parse∘serialize} fixed points: int/long
     *       decimal text is stable (platform TextScanner fast path), while decimals written
     *       from arbitrary {@code Number} subtypes (e.g. {@code BigDecimal "0.100"}, scientific
     *       notation) converge to the {@code Double.toString} fixed point. Both sides run the
     *       same normalization, so {@code writeHash == loadRecomputedHash} holds structurally.
     *       This is also the platform-baseline tripwire anchor: if JsonTool map/number
     *       semantics ever change, the determinism test goes red.</li>
     * </ul>
     *
     * <p>Digest: SHA-256 hex via core {@link SstFileChecksum}.
     */
    static String computeManifestChecksumHex(Map<String, Object> manifestMap) {
        return computeCanonicalChecksumHex(manifestMap, MANIFEST_FIELD_ORDER);
    }

    /**
     * Stage 51 / F-10b: canonical integrity checksum shared by the epoch manifest and
     * the {@code .checkpoint} body (must NOT contain the {@code checksum} key). Defined
     * so that store-side hashing and load-side recomputation converge on identical
     * bytes by construction:
     *
     * <ul>
     *   <li>The map is first re-ordered through the given field order — the shared
     *       fixed-field-order canonical form (store-side assembly already follows it;
     *       load-side parsed maps are re-ordered defensively through the same
     *       function).</li>
     *   <li>One JSON text round-trip ({@code serialize -> parseMap -> serialize}) normalizes
     *       number representations to their {@code parse∘serialize} fixed points: int/long
     *       decimal text is stable (platform TextScanner fast path), while decimals written
     *       from arbitrary {@code Number} subtypes (e.g. {@code BigDecimal "0.100"}, scientific
     *       notation) converge to the {@code Double.toString} fixed point. Both sides run the
     *       same normalization, so {@code writeHash == loadRecomputedHash} holds structurally.
     *       This is also the platform-baseline tripwire anchor: if JsonTool map/number
     *       semantics ever change, the determinism test goes red.</li>
     * </ul>
     *
     * <p>Digest: SHA-256 hex via core {@link SstFileChecksum}.
     */
    static String computeCanonicalChecksumHex(Map<String, Object> map,
                                              java.util.List<String> fieldOrder) {
        Map<String, Object> canonical = canonicalizeFieldOrder(map, fieldOrder);
        String text = JsonTool.serialize(canonical, false);
        Map<String, Object> normalized = JsonTool.parseMap(text);
        String normalizedText = JsonTool.serialize(normalized, false);
        return SstFileChecksum.sha256Hex(normalizedText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Stage 51 / F-10b: re-orders a map into the given canonical field order; keys not
     * in the list (forward-compat extras) keep their document order after the known
     * fields. Used by both the store-side and the load-side checksum paths — one
     * canonicalization function, no second assembly implementation to drift.
     */
    static Map<String, Object> canonicalizeFieldOrder(Map<String, Object> map,
                                                      java.util.List<String> fieldOrder) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String key : fieldOrder) {
            if (map.containsKey(key)) {
                ordered.put(key, map.get(key));
            }
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!ordered.containsKey(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        return ordered;
    }

    public static EpochManifest deserializeEpochManifest(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        int formatVersion = detectFormatVersion(map);
        if (formatVersion < CURRENT_FORMAT_VERSION) {
            LOG.debug("Deserializing legacy epoch manifest (formatVersion={}, current={}) — backward-compatible",
                    formatVersion, CURRENT_FORMAT_VERSION);
        }

        long epochId = map.get("epochId") instanceof Number ? ((Number) map.get("epochId")).longValue() : -1;
        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        long timestamp = map.get("timestamp") instanceof Number ? ((Number) map.get("timestamp")).longValue() : 0;

        // Stage 51: read-side version semantics. Envelope above current = future format we
        // cannot interpret — fail fast (previously silently accepted). The manifest-level
        // stateFormatVersion field, when PRESENT, must equal the envelope version: the new
        // writer always writes both from the same single version truth, so an inconsistent
        // pair (e.g. envelope=2, field=3) is anomalous/tampered. Field absent (legacy) is
        // tolerated — 0 sentinel on the bean.
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            throw unsupportedFormatVersion(map, formatVersion, CheckpointFormatVersions.UNSET_FORMAT_VERSION);
        }
        int stateFormatVersion = readStateFormatVersion(map, formatVersion);

        // Stage 51: checksum verification — present means verify (typed fail-fast on
        // mismatch), absent means a legacy manifest written before Stage 51 (explicit
        // adjudicated skip, debug-logged like the legacy format-version tolerance above).
        String storedChecksum = verifyManifestChecksum(map);

        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : null;

        String stateName = (String) map.get("state");
        EpochState epochState = stateName != null ? EpochState.valueOf(stateName) : null;

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots =
                deserializeTaskSnapshots(map, jobId, pipelineId);

        StreamModelFingerprint fingerprint = deserializeStreamModelFingerprint(map);

        java.util.List<StateSegmentDescriptor> segments = deserializeSegments(map);

        // Stage 49 D2: deserialize per-source-vertex enumerator snapshots when present.
        // Backward compatible: absent on legacy checkpoints → empty map.
        Map<String, SourceEnumeratorSnapshot> enumeratorSnapshots =
                deserializeSourceEnumeratorSnapshots(map);

        return new EpochManifest(epochId, jobId, pipelineId, timestamp, checkpointType, epochState,
                taskSnapshots, fingerprint, segments, enumeratorSnapshots, stateFormatVersion, storedChecksum);
    }

    /**
     * Stage 51: reads the manifest-level {@code stateFormatVersion} field. When PRESENT it
     * must be a number equal to the envelope version and not above the current version:
     * the new writer always writes both from the same single version truth, so an
     * inconsistent pair (e.g. envelope=2, field=3) is anomalous/tampered. Field absent
     * (legacy) is tolerated — {@link CheckpointFormatVersions#UNSET_FORMAT_VERSION}
     * sentinel on the bean. An older-but-consistent version is debug-logged and accepted
     * (backward-compatible).
     */
    private static int readStateFormatVersion(Map<String, Object> map, int formatVersion) {
        int stateFormatVersion = CheckpointFormatVersions.UNSET_FORMAT_VERSION;
        Object sfvObj = map.get(STATE_FORMAT_VERSION_KEY);
        if (sfvObj != null) {
            if (!(sfvObj instanceof Number)) {
                throw unsupportedFormatVersion(map, formatVersion, -1);
            }
            stateFormatVersion = ((Number) sfvObj).intValue();
            if (stateFormatVersion != formatVersion || stateFormatVersion > CURRENT_FORMAT_VERSION) {
                throw unsupportedFormatVersion(map, formatVersion, stateFormatVersion);
            }
            if (stateFormatVersion < CURRENT_FORMAT_VERSION) {
                LOG.debug("Epoch manifest carries older state format version {} (current={}) — backward-compatible",
                        stateFormatVersion, CURRENT_FORMAT_VERSION);
            }
        }
        return stateFormatVersion;
    }

    /**
     * Stage 51: verifies the manifest integrity checksum. Present means verify (typed
     * fail-fast on mismatch), absent means a legacy manifest written before Stage 51
     * (explicit adjudicated skip, debug-logged like the legacy format-version tolerance).
     *
     * @return the stored checksum string when present, {@code null} for legacy manifests.
     */
    private static String verifyManifestChecksum(Map<String, Object> map) {
        String storedChecksum = null;
        Object checksumObj = map.get(CHECKSUM_KEY);
        if (checksumObj != null) {
            if (!(checksumObj instanceof String)) {
                throw checkpointChecksumMismatch(map, String.valueOf(checksumObj), "non-string checksum value");
            }
            storedChecksum = (String) checksumObj;
            Map<String, Object> payloadOnly = new LinkedHashMap<>(map);
            payloadOnly.remove(CHECKSUM_KEY);
            String recomputed = computeManifestChecksumHex(payloadOnly);
            if (!storedChecksum.equals(recomputed)) {
                throw checkpointChecksumMismatch(map, storedChecksum, recomputed);
            }
        } else {
            LOG.debug("Epoch manifest carries no checksum (pre-Stage-51 legacy bytes) — skipping integrity verification");
        }
        return storedChecksum;
    }

    /**
     * Deserializes the {@code taskSnapshots} field: a map of TaskLocation keys (parsed
     * via {@link #stringToTaskLocation}, with a jobId/pipelineId/vertexId/0 fallback on
     * unparseable keys) to {@link TaskStateSnapshot} values. Absent field → empty map.
     */
    private static Map<TaskLocation, TaskStateSnapshot> deserializeTaskSnapshots(Map<String, Object> map,
                                                                                 String jobId, String pipelineId) {
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        Map<String, Object> taskSnapshotsMap = map.get("taskSnapshots") instanceof Map
                ? (Map<String, Object>) map.get("taskSnapshots") : null;
        if (taskSnapshotsMap != null) {
            for (Map.Entry<String, Object> entry : taskSnapshotsMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    LOG.warn("Failed to parse TaskLocation from key '{}' in epoch manifest, using fallback", entry.getKey(), e);
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                if (!(entry.getValue() instanceof Map)) {
                    LOG.warn("Skipping non-map task snapshot for key '{}' in epoch manifest", entry.getKey());
                    continue;
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskSnapshots.put(taskLocation, snapshot);
            }
        }
        return taskSnapshots;
    }

    /**
     * Deserializes the {@code streamModelFingerprint} field into a
     * {@link StreamModelFingerprint}. Absent field → {@code null}.
     */
    private static StreamModelFingerprint deserializeStreamModelFingerprint(Map<String, Object> map) {
        StreamModelFingerprint fingerprint = null;
        Map<String, Object> fpMap = (Map<String, Object>) map.get("streamModelFingerprint");
        if (fpMap != null) {
            StreamModelFingerprint.Builder fpBuilder = StreamModelFingerprint.builder();
            fpBuilder.version((String) fpMap.get("version"));
            fpBuilder.dagTopologyHash((String) fpMap.get("dagTopologyHash"));
            fpBuilder.requirementsHash((String) fpMap.get("requirementsHash"));
            fpBuilder.checkpointParticipantsHash((String) fpMap.get("checkpointParticipantsHash"));
            Map<String, String> compHashes = (Map<String, String>) fpMap.get("componentHashes");
            if (compHashes != null) {
                for (Map.Entry<String, String> e : compHashes.entrySet()) {
                    fpBuilder.addComponentHash(e.getKey(), e.getValue());
                }
            }
            fingerprint = fpBuilder.build();
        }
        return fingerprint;
    }

    /**
     * Deserializes the {@code segments} field into {@link StateSegmentDescriptor}s
     * (schemaVersion defaults to 1 when absent/non-numeric). Absent field → empty list.
     */
    private static java.util.List<StateSegmentDescriptor> deserializeSegments(Map<String, Object> map) {
        java.util.List<StateSegmentDescriptor> segments = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> segmentsList = (java.util.List<Map<String, Object>>) map.get("segments");
        if (segmentsList != null) {
            for (Map<String, Object> segMap : segmentsList) {
                segments.add(new StateSegmentDescriptor(
                        (String) segMap.get("segmentType"),
                        (String) segMap.get("path"),
                        (String) segMap.get("codec"),
                        (String) segMap.get("checksum"),
                        segMap.get("schemaVersion") instanceof Number ? ((Number) segMap.get("schemaVersion")).intValue() : 1
                ));
            }
        }
        return segments;
    }

    /**
     * Stage 49 D2: deserializes the per-source-vertex {@code sourceEnumeratorSnapshots}
     * field (Base64 {@code stateBytes} + version). Absent field → empty map
     * (backward compatible with legacy checkpoints).
     */
    private static Map<String, SourceEnumeratorSnapshot> deserializeSourceEnumeratorSnapshots(Map<String, Object> map) {
        Map<String, SourceEnumeratorSnapshot> enumeratorSnapshots = new LinkedHashMap<>();
        Map<String, Object> enumeratorMap = map.get("sourceEnumeratorSnapshots") instanceof Map
                ? (Map<String, Object>) map.get("sourceEnumeratorSnapshots") : null;
        if (enumeratorMap != null) {
            for (Map.Entry<String, Object> entry : enumeratorMap.entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    LOG.warn("Skipping non-map source enumerator snapshot for key '{}'", entry.getKey());
                    continue;
                }
                Map<String, Object> snapMap = (Map<String, Object>) entry.getValue();
                int version = snapMap.get("version") instanceof Number ? ((Number) snapMap.get("version")).intValue() : 0;
                String b64 = (String) snapMap.get("stateBytes");
                byte[] stateBytes = (b64 != null)
                        ? java.util.Base64.getDecoder().decode(b64)
                        : new byte[0];
                enumeratorSnapshots.put(entry.getKey(), new SourceEnumeratorSnapshot(version, stateBytes));
            }
        }
        return enumeratorSnapshots;
    }

    /**
     * Stage 51: typed fail-fast for an unreadable version face. Localization params carry
     * jobId/epochId (best-effort — garbage bytes may lack them) plus both version faces and
     * the current supported version.
     */
    private static io.nop.api.core.exceptions.NopException unsupportedFormatVersion(
            Map<String, Object> map, int formatVersion, int stateFormatVersion) {
        return new StreamException(ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED)
                .param(ARG_JOB_ID, map.get("jobId") instanceof String ? map.get("jobId") : null)
                .param(ARG_EPOCH_ID, map.get("epochId") instanceof Number
                        ? ((Number) map.get("epochId")).longValue()
                        : (map.get("checkpointId") instanceof Number ? ((Number) map.get("checkpointId")).longValue() : -1L))
                .param(ARG_FORMAT_VERSION, formatVersion)
                .param(ARG_STATE_FORMAT_VERSION, stateFormatVersion)
                .param(ARG_CURRENT_FORMAT_VERSION, CURRENT_FORMAT_VERSION);
    }

    /**
     * Stage 51 / F-10b: typed fail-fast for checksum verification failure ({@code stored}
     * value is what the document claimed, {@code recomputed} is what the restore path
     * calculated) — shared by the epoch manifest and the {@code .checkpoint} body paths.
     */
    private static io.nop.api.core.exceptions.NopException checkpointChecksumMismatch(
            Map<String, Object> map, String stored, String recomputed) {
        return new StreamException(ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH)
                .param(ARG_JOB_ID, map.get("jobId") instanceof String ? map.get("jobId") : null)
                .param(ARG_EPOCH_ID, map.get("epochId") instanceof Number
                        ? ((Number) map.get("epochId")).longValue()
                        : (map.get("checkpointId") instanceof Number ? ((Number) map.get("checkpointId")).longValue() : -1L))
                .param(ARG_EXPECTED_CHECKSUM, stored)
                .param(ARG_ACTUAL_CHECKSUM, recomputed);
    }

    public static String taskLocationToString(TaskLocation loc) {
        return loc.getJobId() + "|" + loc.getPipelineId() + "|" + loc.getVertexId() + "|" + loc.getTaskIndex();
    }

    public static TaskLocation stringToTaskLocation(String str) {
        String[] parts = str.split("\\|");
        if (parts.length != 4) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "taskLocation")
                    .param(ARG_DETAIL, "Invalid TaskLocation string: " + str);
        }
        return new TaskLocation(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    public static Map<String, Object> serializeTaskStateSnapshot(TaskStateSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot.getOperatorStates() != null && !snapshot.getOperatorStates().isEmpty()) {
            map.put("operatorStates", serializeOperatorStates(snapshot.getOperatorStates()));
        }
        if (snapshot.getKeyedStates() != null && !snapshot.getKeyedStates().isEmpty()) {
            map.put("keyedStates", snapshot.getKeyedStates());
        }
        // Stage 35: persist key-group ownership metadata so a rescale restore
        // can read the recorded range instead of re-deriving it. Backward
        // compatible: absent on legacy checkpoints.
        if (snapshot instanceof TaskEpochSnapshot) {
            TaskEpochSnapshot epoch = (TaskEpochSnapshot) snapshot;
            if (epoch.isKeyGroupOwnershipMaterialized()) {
                map.put("parallelism", epoch.getParallelism());
                map.put("maxParallelism", epoch.getMaxParallelism());
                map.put("keyGroupRangeStart", epoch.getKeyGroupRangeStart());
                map.put("keyGroupRangeEnd", epoch.getKeyGroupRangeEnd());
            }
            // Stage 43: persist unaligned-checkpoint channel state when present.
            // Absent on aligned checkpoints / legacy snapshots (backward compatible).
            ChannelState channelState = epoch.getChannelState();
            if (channelState != null) {
                Map<String, Object> csForm = channelState.toSerializableForm();
                if (csForm != null && !csForm.isEmpty()) {
                    map.put("channelState", csForm);
                }
            }
        }
        return map;
    }

    public static TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
        if (map == null) {
            return null;
        }
        TaskStateSnapshot snapshot = new TaskStateSnapshot(taskLocation);

        Map<String, Object> operatorStates = map.get("operatorStates") instanceof Map
                ? (Map<String, Object>) map.get("operatorStates") : null;
        if (operatorStates != null) {
            for (Map.Entry<String, Object> entry : operatorStates.entrySet()) {
                snapshot.putOperatorState(entry.getKey(),
                        deserializeOperatorState(entry.getValue()));
            }
        }

        Map<String, Object> keyedStates = map.get("keyedStates") instanceof Map
                ? (Map<String, Object>) map.get("keyedStates") : null;
        if (keyedStates != null) {
            for (Map.Entry<String, Object> entry : keyedStates.entrySet()) {
                snapshot.putKeyedState(entry.getKey(), entry.getValue());
            }
        }

        // Stage 35: reload key-group ownership metadata when present.
        Object kgrs = map.get("keyGroupRangeStart");
        Object csObj = map.get("channelState");
        if (kgrs instanceof Number || csObj instanceof Map) {
            TaskEpochSnapshot epoch = TaskEpochSnapshot.fromTaskStateSnapshot(snapshot);
            if (kgrs instanceof Number) {
                epoch.setParallelism(intField(map, "parallelism", 1));
                epoch.setMaxParallelism(intField(map, "maxParallelism",
                        io.nop.stream.core.common.state.shard.KeyGroup.DEFAULT_MAX_PARALLELISM));
                epoch.setKeyGroupRangeStart(((Number) kgrs).intValue());
                Object kgre = map.get("keyGroupRangeEnd");
                if (kgre instanceof Number) {
                    epoch.setKeyGroupRangeEnd(((Number) kgre).intValue());
                }
            }
            // Stage 43: reload unaligned-checkpoint channel state when present.
            if (csObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> csMap = (Map<String, Object>) csObj;
                epoch.setChannelState(ChannelState.fromSerializableForm(csMap));
            }
            return epoch;
        }

        return snapshot;
    }

    private static int intField(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        return (v instanceof Number) ? ((Number) v).intValue() : defaultValue;
    }

    /**
     * Converts operator-state values that are not JSON-safe (not Nop {@code @DataBean}s)
     * into plain JSON-safe map forms, so the checkpoint JSON persist path can store them.
     * Currently handled: {@code HeapInternalTimerService.TimerSnapshot},
     * {@code WindowOperator.PaneTrackingSnapshot}, and {@code SimpleAccumulator} values
     * (trigger-accumulators map). Everything else passes through unchanged.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeOperatorStates(Map<String, Object> operatorStates) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : operatorStates.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof io.nop.stream.core.operators.HeapInternalTimerService.TimerSnapshot) {
                result.put(entry.getKey(),
                        ((io.nop.stream.core.operators.HeapInternalTimerService.TimerSnapshot<?, ?>) value)
                                .toSerializableForm());
            } else if (value instanceof io.nop.stream.runtime.operators.windowing.WindowOperator.PaneTrackingSnapshot) {
                result.put(entry.getKey(),
                        ((io.nop.stream.runtime.operators.windowing.WindowOperator.PaneTrackingSnapshot) value)
                                .toSerializableForm());
            } else if (isAccumulatorMap(value)) {
                Map<String, Object> accMap = (Map<String, Object>) value;
                Map<String, Object> accForms = new LinkedHashMap<>();
                for (Map.Entry<String, Object> accEntry : accMap.entrySet()) {
                    accForms.put(accEntry.getKey(), accumulatorToForm(
                            (io.nop.stream.core.common.accumulators.SimpleAccumulator<?>) accEntry.getValue()));
                }
                result.put(entry.getKey(), accForms);
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static boolean isAccumulatorMap(Object value) {
        if (!(value instanceof Map)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        if (map.isEmpty()) {
            return false;
        }
        for (Object v : map.values()) {
            if (!(v instanceof io.nop.stream.core.common.accumulators.SimpleAccumulator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rebuilds typed operator-state values from their JSON-safe forms (the inverse of
     * {@link #serializeOperatorStates}). Self-describing {@code "@type"} markers route
     * each form back to its original type; unknown / plain maps pass through unchanged.
     */
    @SuppressWarnings("unchecked")
    private static Object deserializeOperatorState(Object value) {
        if (!(value instanceof Map)) {
            return value;
        }
        Map<String, Object> form = (Map<String, Object>) value;
        Object type = form.get("@type");
        if ("TimerSnapshot".equals(type)) {
            return io.nop.stream.core.operators.HeapInternalTimerService.TimerSnapshot.fromSerializableForm(form);
        }
        if ("PaneTrackingSnapshot".equals(type)) {
            return io.nop.stream.runtime.operators.windowing.WindowOperator.PaneTrackingSnapshot.fromSerializableForm(form);
        }
        if (isAccumulatorFormMap(form)) {
            Map<String, Object> accMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> accEntry : form.entrySet()) {
                accMap.put(accEntry.getKey(),
                        accumulatorFromForm((Map<String, Object>) accEntry.getValue()));
            }
            return accMap;
        }
        return value;
    }

    /**
     * Accumulator map form detection: every value is a self-describing
     * {@code {"@type": <accumulator class>, "localValue": ...}} map.
     */
    private static boolean isAccumulatorFormMap(Map<String, Object> form) {
        if (form.isEmpty()) {
            return false;
        }
        for (Object v : form.values()) {
            if (!(v instanceof Map)) {
                return false;
            }
            Object vt = ((Map<String, Object>) v).get("@type");
            if (!(vt instanceof String) || ((String) vt).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * JSON-safe form of a {@code SimpleAccumulator}: the concrete class name plus the
     * accumulated local value (re-accumulated on restore via the no-arg constructor +
     * {@code add}).
     */
    private static Map<String, Object> accumulatorToForm(io.nop.stream.core.common.accumulators.SimpleAccumulator<?> acc) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("@type", acc.getClass().getName());
        form.put("localValue", acc.getLocalValue());
        return form;
    }

    private static io.nop.stream.core.common.accumulators.SimpleAccumulator<?> accumulatorFromForm(Map<String, Object> form) {
        String typeName = (String) form.get("@type");
        io.nop.stream.core.common.accumulators.SimpleAccumulator<?> acc;
        try {
            io.nop.stream.core.util.ClassNameValidator.validateAccumulatorClass(typeName);
            acc = (io.nop.stream.core.common.accumulators.SimpleAccumulator<?>)
                    Class.forName(typeName).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to recreate accumulator of type " + typeName);
        }
        Object localValue = form.get("localValue");
        if (localValue != null) {
            ((io.nop.stream.core.common.accumulators.SimpleAccumulator<Object>) acc).add(localValue);
        }
        return acc;
    }

    /**
     * Stage 29 (G59): detect the format version from a deserialized JSON map. Absent
     * {@code formatVersion} field means legacy version {@code 1}; otherwise the explicit
     * integer value is used. Never throws — unknown / non-numeric values are treated as legacy.
     */
    private static int detectFormatVersion(Map<String, Object> map) {
        Object v = map.get(FORMAT_VERSION_KEY);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return LEGACY_FORMAT_VERSION;
    }
}
