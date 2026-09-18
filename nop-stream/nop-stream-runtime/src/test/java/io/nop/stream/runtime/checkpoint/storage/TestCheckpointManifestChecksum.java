/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointFormatVersions;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 51 (roadmap item 25 / D-DRIFT-2): the five mandated test classes for the manifest
 * {@code stateFormatVersion} + {@code checksum} landing —
 *
 * <ol>
 *   <li>round-trip — new manifest serialize→deserialize→field equality + checksum recompute stable;</li>
 *   <li>legacy — manifests without the two fields restore successfully with verification skipped;</li>
 *   <li>tamper — mutating any payload field after writing fails fast with the typed error code
 *       and localization params (jobId/epochId/expected/actual);</li>
 *   <li>determinism — a manifest covering every field type family (integers, decimals written from
 *       non-fixed-point Number subtypes like BigDecimal, Base64 blobs, fingerprint hashes, segments,
 *       enumerator snapshots) satisfies writeHash == loadRecomputedHash. This is the platform-baseline
 *       tripwire: if JsonTool map/number semantics ever change, this test goes red;</li>
 *   <li>version mismatch — stateFormatVersion above current / envelope above current / the two
 *       version faces inconsistent each fail fast with the typed error.</li>
 * </ol>
 */
class TestCheckpointManifestChecksum {

    private static final TaskLocation LOC = new TaskLocation("ck-job", "ck-pipe", "v1", 0);

    /** Full-coverage manifest: every field family, including decimal-valued keyed state. */
    private EpochManifest fullCoverageManifest() {
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(LOC)
                .putOperatorState("op-counter", 42L)
                .putKeyedState("k-int", 7)
                .putKeyedState("k-long", 9_000_000_000L)
                .putKeyedState("k-double", 1.25D)
                .putKeyedState("k-bigdecimal", new BigDecimal("0.100"))
                .putKeyedState("k-sci", new BigDecimal("1E+2"))
                .putKeyedState("k-string", "text-value")
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC, snapshot);

        StreamModelFingerprint fingerprint = StreamModelFingerprint.builder()
                .version("v1")
                .dagTopologyHash("dag-hash-abcdef")
                .requirementsHash("req-hash-123456")
                .checkpointParticipantsHash("cp-hash-7890")
                .addComponentHash("vertex-1", "comp-hash-aa")
                .addComponentHash("vertex-2", "comp-hash-bb")
                .build();

        List<StateSegmentDescriptor> segments = List.of(new StateSegmentDescriptor(
                "rocksdb-sst", "content-hash-xyz", "identity", "content-hash-xyz", 1));

        Map<String, SourceEnumeratorSnapshot> enumerators = new LinkedHashMap<>();
        enumerators.put("3", new SourceEnumeratorSnapshot(1, new byte[]{1, 2, 3, 4, 5}));

        return new EpochManifest(11L, "ck-job", "ck-pipe", 1234567890L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                taskSnapshots, fingerprint, segments, enumerators);
    }

    private Map<String, Object> parseToMap(byte[] bytes) {
        return JsonTool.parseMap(new String(bytes, StandardCharsets.UTF_8));
    }

    private byte[] replaceInJson(byte[] bytes, String from, String to) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains(from), "fixture must contain '" + from + "': " + json);
        return json.replace(from, to).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Single version truth
    // ------------------------------------------------------------------

    @Test
    void testSingleVersionTruthAlias() {
        assertEquals(CheckpointFormatVersions.CURRENT_FORMAT_VERSION, CheckpointSerDe.CURRENT_FORMAT_VERSION,
                "runtime envelope constant must alias the core single truth");
        assertEquals(CheckpointFormatVersions.LEGACY_FORMAT_VERSION, CheckpointSerDe.LEGACY_FORMAT_VERSION,
                "runtime legacy constant must alias the core single truth");
    }

    // ------------------------------------------------------------------
    // 1. round-trip
    // ------------------------------------------------------------------

    @Test
    void testRoundTripCarriesAndVerifiesNewFields() {
        EpochManifest original = fullCoverageManifest();

        byte[] bytes = CheckpointSerDe.serializeEpochManifest(original);
        Map<String, Object> raw = parseToMap(bytes);
        assertEquals(CheckpointFormatVersions.CURRENT_FORMAT_VERSION,
                ((Number) raw.get("stateFormatVersion")).intValue(),
                "serialized manifest must carry stateFormatVersion");
        assertNotNull(raw.get("checksum"), "serialized manifest must carry checksum");

        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(bytes);
        assertNotNull(restored);
        assertEquals(original.getEpochId(), restored.getEpochId());
        assertEquals(original.getJobId(), restored.getJobId());
        assertEquals(CheckpointFormatVersions.CURRENT_FORMAT_VERSION, restored.getStateFormatVersion(),
                "read-back stateFormatVersion must equal the single version truth");
        assertEquals(raw.get("checksum"), restored.getChecksum(),
                "read-back checksum must equal the value stored in the bytes");

        // re-serialization recomputes (not copies) the checksum and stays stable across a
        // deserialize round-trip: the re-serialized bytes carry the SAME checksum (the payload
        // text of non-fixed-point Number subtypes normalizes to the fixed-point form on
        // read-back — "0.100" -> "0.1" — but the canonical hash is defined over the normalized
        // form on BOTH sides, so it is invariant).
        byte[] bytes2 = CheckpointSerDe.serializeEpochManifest(restored);
        Map<String, Object> raw2 = parseToMap(bytes2);
        assertEquals(raw.get("checksum"), raw2.get("checksum"),
                "re-serialization must recompute the same checksum from equivalent content");
        EpochManifest restored2 = assertDoesNotThrow(() -> CheckpointSerDe.deserializeEpochManifest(bytes2),
                "re-serialized bytes must pass their own checksum verification");
        assertEquals(restored.getStateFormatVersion(), restored2.getStateFormatVersion());
    }

    // ------------------------------------------------------------------
    // 2. legacy compatibility
    // ------------------------------------------------------------------

    @Test
    void testLegacyManifestWithoutNewFieldsRestores() {
        // v2 envelope, but no stateFormatVersion / checksum keys: the exact bytes a pre-Stage-51
        // v2 writer produced. Must restore unchanged with verification skipped.
        String legacyJson = "{"
                + "\"formatVersion\":2,"
                + "\"epochId\":5,"
                + "\"jobId\":\"legacy-job\","
                + "\"pipelineId\":\"legacy-pipe\","
                + "\"timestamp\":111,"
                + "\"checkpointType\":\"CHECKPOINT\","
                + "\"state\":\"COMMITTED\","
                + "\"taskSnapshots\":{}"
                + "}";
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(legacyJson.getBytes(StandardCharsets.UTF_8));
        assertNotNull(restored, "legacy v2 manifest (no new fields) must restore");
        assertEquals(5L, restored.getEpochId());
        assertEquals("legacy-job", restored.getJobId());
        assertEquals(CheckpointFormatVersions.UNSET_FORMAT_VERSION, restored.getStateFormatVersion(),
                "legacy manifest has no stateFormatVersion — 0 sentinel (verification skipped)");
        assertNull(restored.getChecksum(), "legacy manifest has no checksum — verification skipped");
    }

    @Test
    void testLegacyV1ManifestWithoutEnvelopeRestores() {
        // no formatVersion key at all (v1 legacy), no new fields — oldest readable form
        String legacyJson = "{"
                + "\"epochId\":1,"
                + "\"jobId\":\"v1-job\","
                + "\"pipelineId\":\"v1-pipe\","
                + "\"timestamp\":222,"
                + "\"taskSnapshots\":{}"
                + "}";
        EpochManifest restored = assertDoesNotThrow(
                () -> CheckpointSerDe.deserializeEpochManifest(legacyJson.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(restored);
        assertEquals(1L, restored.getEpochId());
        assertEquals(CheckpointFormatVersions.UNSET_FORMAT_VERSION, restored.getStateFormatVersion());
        assertNull(restored.getChecksum());
    }

    // ------------------------------------------------------------------
    // 3. tamper detection
    // ------------------------------------------------------------------

    @Test
    void testTamperedEpochIdFailsFastWithTypedError() {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(fullCoverageManifest());
        // tamper a payload field after the checksum was written (epochId 11 -> 77)
        byte[] tampered = replaceInJson(bytes, "\"epochId\":11", "\"epochId\":77");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(tampered));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("ck-job", ex.getParams().get("jobId"), "localization param jobId");
        assertEquals(77L, ((Number) ex.getParams().get("epochId")).longValue(), "localization param epochId (tampered value)");
        assertEquals(parseToMap(bytes).get("checksum"), ex.getParams().get("expectedChecksum"),
                "localization param expectedChecksum = stored value");
        assertNotNull(ex.getParams().get("actualChecksum"), "localization param actualChecksum");
        assertNotEquals(ex.getParams().get("expectedChecksum"), ex.getParams().get("actualChecksum"));
    }

    @Test
    void testTamperedKeyedStateDecimalFailsFast() {
        EpochManifest manifest = fullCoverageManifest();
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(manifest);
        // tamper a nested payload value inside taskSnapshots.keyedStates (1.25 -> 9.99)
        byte[] tampered = replaceInJson(bytes, "\"k-double\":1.25", "\"k-double\":9.99");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(tampered));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("ck-job", ex.getParams().get("jobId"));
        assertEquals(11L, ((Number) ex.getParams().get("epochId")).longValue());
    }

    @Test
    void testTamperedChecksumValueItselfFailsFast() {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(fullCoverageManifest());
        Map<String, Object> raw = parseToMap(bytes);
        String stored = (String) raw.get("checksum");
        byte[] tampered = replaceInJson(bytes, stored, "0" + stored.substring(0, stored.length() - 1));

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(tampered));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("0" + stored.substring(0, stored.length() - 1), ex.getParams().get("expectedChecksum"),
                "expected param carries the tampered stored value");
        assertEquals(stored, ex.getParams().get("actualChecksum"),
                "actual param carries the honest recomputation of the untampered payload");
    }

    // ------------------------------------------------------------------
    // 4. determinism / platform-baseline tripwire
    // ------------------------------------------------------------------

    @Test
    void testWriteHashEqualsLoadRecomputedHashForAllFieldFamilies() {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(fullCoverageManifest());
        Map<String, Object> raw = parseToMap(bytes);
        String storedChecksum = (String) raw.get("checksum");
        assertNotNull(storedChecksum);

        // load-side recomputation exactly as the deserialize path performs it: parsed map,
        // checksum key removed, canonicalized through the production function.
        Map<String, Object> payloadOnly = new LinkedHashMap<>(raw);
        payloadOnly.remove(CheckpointSerDe.CHECKSUM_KEY);
        String recomputed = CheckpointSerDe.computeManifestChecksumHex(payloadOnly);

        assertEquals(storedChecksum, recomputed,
                "write-time hash must equal load-time recomputed hash (round-trip determinism)");
    }

    @Test
    void testDecimalNormalizationConvergesForNonFixedPointNumberSubtypes() {
        // keyedStates values written from BigDecimal forms that are NOT parse∘serialize fixed
        // points ("0.100" parses to Double 0.1 whose Double.toString is "0.1"; "1E+2" -> "100.0").
        // The canonical normalization must converge both sides onto the same text.
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(LOC)
                .putKeyedState("bd-trailing", new BigDecimal("0.100"))
                .putKeyedState("bd-sci", new BigDecimal("1E+2"))
                .putKeyedState("plain-double", 0.1D)
                .build();
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC, snapshot);
        EpochManifest manifest = new EpochManifest(12L, "ck-job", "ck-pipe", 999L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, taskSnapshots, null, null);

        byte[] bytes = CheckpointSerDe.serializeEpochManifest(manifest);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"bd-trailing\":0.100"),
                "BigDecimal toString is written verbatim (non-fixed-point text): " + json);

        Map<String, Object> raw = parseToMap(bytes);
        Map<String, Object> payloadOnly = new LinkedHashMap<>(raw);
        payloadOnly.remove(CheckpointSerDe.CHECKSUM_KEY);
        assertEquals(raw.get("checksum"), CheckpointSerDe.computeManifestChecksumHex(payloadOnly),
                "store-side hash (over live BigDecimal values) must equal load-side hash (over parsed Doubles) "
                        + "— normalization converges both sides to the fixed-point text");
    }

    /** 复现 S1 CDC E2E 回归形状：POJO bean 内嵌 scale-2 BigDecimal 字段（写侧是 bean 对象，非 Map）。 */
    @io.nop.api.core.annotations.data.DataBean
    public static class EnrichedBean {
        private BigDecimal avgAmount;

        public EnrichedBean() {
        }

        EnrichedBean(BigDecimal avgAmount) {
            this.avgAmount = avgAmount;
        }

        public BigDecimal getAvgAmount() {
            return avgAmount;
        }

        public void setAvgAmount(BigDecimal avgAmount) {
            this.avgAmount = avgAmount;
        }
    }

    @Test
    void testBeanWithBigDecimalFieldConvergesAcrossRoundTrip() {
        // 修复前：normalizeNumbersDeep 对 bean 对象原样透传（非 Number/Map/List），
        // 写侧序列化保留 "100.00"，读侧解析为普通 map 后规范化为 100，
        // checksum 恒不匹配（nop-stream-fraud-example S1/S2 E2E 确定性失败）。
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(LOC)
                .putKeyedState("enriched", new EnrichedBean(new BigDecimal("100.00")))
                .putKeyedState("enriched-big", new EnrichedBean(new BigDecimal("1200.00")))
                .build();
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC, snapshot);
        EpochManifest manifest = new EpochManifest(13L, "ck-job", "ck-pipe", 999L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, taskSnapshots, null, null);

        byte[] bytes = CheckpointSerDe.serializeEpochManifest(manifest);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"avgAmount\":100.00"),
                "bean BigDecimal field is written verbatim on the store side: " + json);

        Map<String, Object> raw = parseToMap(bytes);
        Map<String, Object> payloadOnly = new LinkedHashMap<>(raw);
        payloadOnly.remove(CheckpointSerDe.CHECKSUM_KEY);
        assertEquals(raw.get("checksum"), CheckpointSerDe.computeManifestChecksumHex(payloadOnly),
                "store-side hash (over live bean objects) must equal load-side hash (over parsed maps) "
                        + "— round-trip + numeric normalization converge both sides");
    }

    // ------------------------------------------------------------------
    // 5. version mismatch semantics
    // ------------------------------------------------------------------

    @Test
    void testStateFormatVersionAboveCurrentFailsFast() {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(fullCoverageManifest());
        // bump the stateFormatVersion value only (2 -> 3); envelope stays 2 → double-face mismatch too
        byte[] future = replaceInJson(bytes, "\"stateFormatVersion\":2", "\"stateFormatVersion\":3");
        // strip checksum so the version check is what fires (checksum would also fail — different code)
        Map<String, Object> raw = parseToMap(future);
        raw.remove(CheckpointSerDe.CHECKSUM_KEY);
        raw.put(CheckpointSerDe.CHECKSUM_KEY, "deadbeef");
        byte[] futureBytes = JsonTool.serialize(raw, false).getBytes(StandardCharsets.UTF_8);

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(futureBytes));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("ck-job", ex.getParams().get("jobId"));
        assertEquals(3, ((Number) ex.getParams().get("stateFormatVersion")).intValue());
        assertEquals(2, ((Number) ex.getParams().get("formatVersion")).intValue());
        assertEquals(CheckpointFormatVersions.CURRENT_FORMAT_VERSION,
                ((Number) ex.getParams().get("currentFormatVersion")).intValue());
    }

    @Test
    void testEnvelopeAboveCurrentFailsFast() {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(fullCoverageManifest());
        byte[] future = replaceInJson(bytes, "\"formatVersion\":2,\"stateFormatVersion\":2",
                "\"formatVersion\":3,\"stateFormatVersion\":3");
        Map<String, Object> raw = parseToMap(future);
        raw.remove(CheckpointSerDe.CHECKSUM_KEY);
        byte[] futureBytes = JsonTool.serialize(raw, false).getBytes(StandardCharsets.UTF_8);

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(futureBytes));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals(3, ((Number) ex.getParams().get("formatVersion")).intValue(),
                "envelope face above current must fail fast (was silently accepted pre-Stage-51)");
    }

    @Test
    void testInconsistentVersionFacesFailFast() {
        // envelope=2, field=1: mutually inconsistent — no writer ever produces this pair
        String json = "{"
                + "\"formatVersion\":2,"
                + "\"stateFormatVersion\":1,"
                + "\"epochId\":8,"
                + "\"jobId\":\"ck-job\","
                + "\"pipelineId\":\"ck-pipe\","
                + "\"timestamp\":1,"
                + "\"taskSnapshots\":{}"
                + "}";
        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeEpochManifest(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals(2, ((Number) ex.getParams().get("formatVersion")).intValue());
        assertEquals(1, ((Number) ex.getParams().get("stateFormatVersion")).intValue());
    }

    @Test
    void testConsistentLowerVersionFaceIsTolerated() {
        // envelope absent (legacy v1) + field=1: consistent pair at a lower version — tolerated
        String json = "{"
                + "\"stateFormatVersion\":1,"
                + "\"epochId\":9,"
                + "\"jobId\":\"old-job\","
                + "\"pipelineId\":\"old-pipe\","
                + "\"timestamp\":1,"
                + "\"taskSnapshots\":{}"
                + "}";
        EpochManifest restored = assertDoesNotThrow(
                () -> CheckpointSerDe.deserializeEpochManifest(json.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(restored);
        assertEquals(1, restored.getStateFormatVersion());
        assertEquals(9L, restored.getEpochId());
    }

    @Test
    void testCheckpointEnvelopeAboveCurrentFailsFast() {
        // the CompletedCheckpoint read path shares the envelope upper-bound semantics
        String json = "{"
                + "\"formatVersion\":7,"
                + "\"jobId\":\"cj\","
                + "\"pipelineId\":\"cp\","
                + "\"checkpointId\":1,"
                + "\"triggerTimestamp\":1,"
                + "\"completedTimestamp\":2,"
                + "\"checkpointType\":\"CHECKPOINT\","
                + "\"taskStates\":{}"
                + "}";
        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("cj", ex.getParams().get("jobId"));
        assertEquals(1L, ((Number) ex.getParams().get("epochId")).longValue(),
                "checkpointId is reported through the epochId param on the checkpoint read path");
    }
}
