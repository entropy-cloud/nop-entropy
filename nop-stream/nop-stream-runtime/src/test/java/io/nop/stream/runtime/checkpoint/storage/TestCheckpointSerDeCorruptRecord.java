package io.nop.stream.runtime.checkpoint.storage;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_JOB_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_MISSING_FIELDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ST-06 regression: {@code CheckpointSerDe.deserializeCheckpoint} used to
 * return {@code null} for an existing-but-unreadable checkpoint record
 * (missing required identity fields), which the restore path folds into "no
 * checkpoint found" — a stateful job silently cold-started on empty state.
 * A corrupt record must now fail loud with the record's identity context,
 * while a valid legacy row (no checksum / no formatVersion) still parses.
 */
class TestCheckpointSerDeCorruptRecord {

    /** Legacy v1 body shape: no formatVersion, no checksum (both tolerated on read). */
    private static Map<String, Object> legacyCheckpointMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobId", "job-1");
        map.put("pipelineId", "pipe-1");
        map.put("checkpointId", 7L);
        map.put("triggerTimestamp", 1000L);
        map.put("completedTimestamp", 2000L);
        map.put("checkpointType", "CHECKPOINT");
        map.put("taskStates", new LinkedHashMap<>());
        return map;
    }

    private static byte[] bytes(Map<String, Object> map) {
        return JsonTool.serialize(map, false).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void corruptRow_missingCheckpointId_failsLoudWithContext() {
        Map<String, Object> map = legacyCheckpointMap();
        map.remove("checkpointId");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(bytes(map)),
                "A checkpoint record missing required fields must fail loud, not collapse to 'no checkpoint'");
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_DATA_CORRUPT.getErrorCode(), ex.getErrorCode());
        // Row context: the surviving identity fields are carried on the error.
        assertEquals("job-1", ex.getParam(ARG_JOB_ID));
        assertEquals(-1L, ex.getParam(ARG_EPOCH_ID));
        String missing = String.valueOf(ex.getParam(ARG_MISSING_FIELDS));
        assertTrue(missing.contains("checkpointId"), "missing fields must name checkpointId: " + missing);
    }

    @Test
    void corruptRow_missingMultipleFields_namesAllOfThem() {
        Map<String, Object> map = legacyCheckpointMap();
        map.remove("triggerTimestamp");
        map.remove("completedTimestamp");

        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(bytes(map)));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_DATA_CORRUPT.getErrorCode(), ex.getErrorCode());
        String missing = String.valueOf(ex.getParam(ARG_MISSING_FIELDS));
        assertTrue(missing.contains("triggerTimestamp"), missing);
        assertTrue(missing.contains("completedTimestamp"), missing);
        assertTrue(!missing.contains("jobId"), "present fields must not be listed as missing");
    }

    @Test
    void corruptRow_notAJsonObject_failsLoud() {
        // "null" parses to a null map — an existing but unreadable record.
        byte[] data = "null".getBytes(StandardCharsets.UTF_8);
        StreamException ex = assertThrows(StreamException.class,
                () -> CheckpointSerDe.deserializeCheckpoint(data));
        assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_DATA_CORRUPT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void normalLegacyRow_stillParses() {
        CompletedCheckpoint checkpoint = CheckpointSerDe.deserializeCheckpoint(bytes(legacyCheckpointMap()));
        assertNotNull(checkpoint, "a valid legacy row (no checksum/formatVersion) must keep parsing");
        assertEquals("job-1", checkpoint.getJobId());
        assertEquals("pipe-1", checkpoint.getPipelineId());
        assertEquals(7L, checkpoint.getCheckpointId());
        assertTrue(checkpoint.getTaskStates().isEmpty());
    }

    @Test
    void nullOrEmptyData_stillMeansNoCheckpoint() {
        // An absent row is the legal cold-start path — distinct from corruption.
        assertNull(CheckpointSerDe.deserializeCheckpoint(null));
        assertNull(CheckpointSerDe.deserializeCheckpoint(new byte[0]));
    }
}
