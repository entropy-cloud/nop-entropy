/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-10b (plan 2026-09-04-1326-3): canonical checksum on the {@code .checkpoint} BODY —
 * the main restore path previously carried no integrity protection on the body JSON.
 * The Stage 51 canonical-checksum mechanism (already proven for the epoch manifest) is
 * extended here; proofs:
 * <ol>
 *   <li>round-trip — serialize→deserialize keeps fields and stamps a checksum;</li>
 *   <li>tamper (top level) — mutating a header field fails fast with the typed
 *       checksum error;</li>
 *   <li>tamper (embedded payload) — mutating a state value INSIDE taskStates is caught
 *       BEFORE any value is consumed (checksum-before-deserialize);</li>
 *   <li>legacy tolerance — a body without the checksum field restores (pre-checksum
 *       bytes keep working, same tolerance as the manifest).</li>
 * </ol>
 */
class TestCheckpointBodyChecksum {

    private static final TaskLocation LOC = new TaskLocation("body-job", "body-pipe", "v1", 0);

    private CompletedCheckpoint checkpoint(long id) {
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(LOC)
                .putOperatorState("op-counter", 7L)
                .putKeyedState("k-int", 5)
                .build();
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(LOC, snapshot);
        return new CompletedCheckpoint("body-job", "body-pipe", id,
                1234567890L, 1234567999L, CheckpointType.CHECKPOINT, taskStates);
    }

    private byte[] replaceInJson(byte[] bytes, String from, String to) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains(from), "fixture must contain '" + from + "': " + json);
        return json.replace(from, to).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTripStampsAndVerifiesBodyChecksum() {
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(checkpoint(5L));
        Map<String, Object> map = JsonTool.parseMap(new String(bytes, StandardCharsets.UTF_8));
        assertNotNull(map.get("checksum"), "body carries the canonical checksum: " + map.keySet());

        CompletedCheckpoint restored = CheckpointSerDe.deserializeCheckpoint(bytes);
        assertEquals("body-job", restored.getJobId());
        assertEquals(5L, restored.getCheckpointId());
        assertEquals(1, restored.getTaskStates().size());
    }

    @Test
    void tamperedHeaderFieldFailsTyped() {
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(checkpoint(5L));
        byte[] tampered = replaceInJson(bytes, "\"checkpointId\":5", "\"checkpointId\":9");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(tampered));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(),
                ex.getErrorCode(), "typed checksum mismatch error");
        assertEquals("body-job", ex.getParams().get("jobId"), "localization params carry jobId");
    }

    @Test
    void tamperedEmbeddedStatePayloadFailsBeforeDeserialization() {
        // The adversarial target: a value INSIDE taskStates (the embedded state payload
        // that would otherwise reach operator state restoration / native deserialization).
        // The checksum gate runs BEFORE any value is consumed — the tamper is rejected
        // without the payload ever being materialized.
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(checkpoint(5L));
        byte[] tampered = replaceInJson(bytes, "\"k-int\":5", "\"k-int\":8");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(tampered),
                "embedded-payload tamper must be caught by the body checksum");
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    void nonStringChecksumFailsTyped() {
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(checkpoint(5L));
        Map<String, Object> map = JsonTool.parseMap(new String(bytes, StandardCharsets.UTF_8));
        map.put("checksum", 42);
        byte[] hostile = JsonTool.serialize(map, false).getBytes(StandardCharsets.UTF_8);

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(hostile));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(),
                ex.getErrorCode(), "non-string checksum is a typed rejection");
    }

    @Test
    void legacyBodyWithoutChecksumStillRestores() {
        // Adjudicated legacy tolerance: bytes written before the checksum was stamped
        // (no checksum field) restore without verification — same policy as the epoch
        // manifest (Stage 51).
        byte[] bytes = CheckpointSerDe.serializeCheckpoint(checkpoint(5L));
        Map<String, Object> legacy = JsonTool.parseMap(new String(bytes, StandardCharsets.UTF_8));
        Object stamped = legacy.remove("checksum");
        assertNotNull(stamped, "fixture must have carried a checksum to strip");
        byte[] legacyBytes = JsonTool.serialize(legacy, false).getBytes(StandardCharsets.UTF_8);

        CompletedCheckpoint restored = assertDoesNotThrow(
                () -> CheckpointSerDe.deserializeCheckpoint(legacyBytes));
        assertEquals(5L, restored.getCheckpointId(), "legacy body restores without the field");
    }
}
