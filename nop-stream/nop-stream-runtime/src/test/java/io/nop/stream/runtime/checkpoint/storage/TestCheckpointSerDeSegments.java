/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 31 Phase 3: the EpochManifest segments serialization path (CheckpointSerDe
 * :185-197 serialize / :268-280 deserialize) must round-trip non-empty segments
 * with full fidelity, so incremental checkpoints (which carry SST references in
 * segments) survive persistence.
 */
class TestCheckpointSerDeSegments {

    private EpochManifest manifestWithSegments(long epochId, List<StateSegmentDescriptor> segments) {
        return new EpochManifest(
                epochId, "job-1", "pipe-1", 1234567L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, segments);
    }

    private StateSegmentDescriptor sstSegment(String hash) {
        return new StateSegmentDescriptor(
                StateSegmentDescriptor.SEGMENT_TYPE_ROCKSDB_SST,
                hash,
                StateSegmentDescriptor.CODEC_IDENTITY,
                hash,
                StateSegmentDescriptor.SCHEMA_VERSION_ROCKSDB_SST);
    }

    @Test
    void nonEmptySegmentsRoundTrip() {
        List<StateSegmentDescriptor> segments = new ArrayList<>();
        segments.add(sstSegment("aaaa1111bbbb2222"));
        segments.add(sstSegment("cccc3333dddd4444"));
        EpochManifest original = manifestWithSegments(42L, segments);

        byte[] bytes = CheckpointSerDe.serializeEpochManifest(original);
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(bytes);

        assertNotNull(restored);
        assertEquals(42L, restored.getEpochId());
        assertNotNull(restored.getSegments());
        assertEquals(2, restored.getSegments().size());

        for (int i = 0; i < segments.size(); i++) {
            StateSegmentDescriptor exp = segments.get(i);
            StateSegmentDescriptor got = restored.getSegments().get(i);
            assertEquals(exp.getSegmentType(), got.getSegmentType());
            assertEquals(exp.getPath(), got.getPath());
            assertEquals(exp.getCodec(), got.getCodec());
            assertEquals(exp.getChecksum(), got.getChecksum());
            assertEquals(exp.getSchemaVersion(), got.getSchemaVersion());
        }
    }

    @Test
    void identityCodecSurvivesRoundTrip() {
        EpochManifest original = manifestWithSegments(99L, Collections.singletonList(sstSegment("deadbeef")));
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(
                CheckpointSerDe.serializeEpochManifest(original));

        StateSegmentDescriptor seg = restored.getSegments().get(0);
        assertEquals(StateSegmentDescriptor.CODEC_IDENTITY, seg.getCodec());
        assertEquals(StateSegmentDescriptor.SEGMENT_TYPE_ROCKSDB_SST, seg.getSegmentType());
        assertEquals("deadbeef", seg.getPath());
        assertEquals("deadbeef", seg.getChecksum());
        assertEquals(1, seg.getSchemaVersion());
    }

    @Test
    void emptySegmentsSerializeAsOmittedAndDeserializeAsEmpty() {
        // Manifest with null/empty segments (non-incremental path) must round-trip to empty list.
        EpochManifest original = manifestWithSegments(1L, Collections.emptyList());
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(original);
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(bytes);

        assertNotNull(restored.getSegments());
        assertTrue(restored.getSegments().isEmpty());
    }

    @Test
    void segmentsJsonContainsExpectedFields() {
        EpochManifest original = manifestWithSegments(
                5L, Collections.singletonList(sstSegment("h1")));
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(original);
        String json = new String(bytes);

        assertTrue(json.contains("\"segments\""), json);
        assertTrue(json.contains("\"codec\":\"identity\""), json);
        assertTrue(json.contains("\"segmentType\":\"rocksdb-sst\""), json);
        assertTrue(json.contains("\"path\":\"h1\""), json);
        assertTrue(json.contains("\"checksum\":\"h1\""), json);
    }

    @Test
    void restoredSegmentsValidateKnownCodec() {
        EpochManifest original = manifestWithSegments(7L, Collections.singletonList(sstSegment("xyz")));
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(
                CheckpointSerDe.serializeEpochManifest(original));
        // every restored segment must pass the known-codec fail-fast check
        for (StateSegmentDescriptor seg : restored.getSegments()) {
            seg.validateCodec();
        }
    }
}
