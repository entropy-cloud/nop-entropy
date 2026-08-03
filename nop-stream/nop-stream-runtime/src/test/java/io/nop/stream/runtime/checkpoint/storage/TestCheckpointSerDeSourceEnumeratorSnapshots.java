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
import io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 2 (D2): verifies that CheckpointSerDe round-trips the
 * {@code sourceEnumeratorSnapshots} section of {@link EpochManifest}.
 */
class TestCheckpointSerDeSourceEnumeratorSnapshots {

    @Test
    void manifestWithEnumeratorSnapshotsRoundTrips() {
        Map<String, SourceEnumeratorSnapshot> snaps = new LinkedHashMap<>();
        snaps.put("vertex-src-1", new SourceEnumeratorSnapshot(1, new byte[]{0xA, 0xB, 0xC}));
        snaps.put("vertex-src-2", new SourceEnumeratorSnapshot(2, new byte[]{0xD, 0xE}));

        EpochManifest original = new EpochManifest(
                5L, "job-rt", "pipe-0", 200L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, null, snaps);

        byte[] serialized = CheckpointSerDe.serializeEpochManifest(original);
        assertNotNull(serialized);

        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(serializedSafe(serialized));
        assertNotNull(restored);
        assertEquals(5L, restored.getEpochId());
        Map<String, SourceEnumeratorSnapshot> restoredSnaps =
                restored.getSourceEnumeratorSnapshots();
        assertNotNull(restoredSnaps);
        assertEquals(2, restoredSnaps.size());
        assertEquals(1, restoredSnaps.get("vertex-src-1").getVersion());
        assertArrayEquals(new byte[]{0xA, 0xB, 0xC}, restoredSnaps.get("vertex-src-1").getStateBytes());
        assertEquals(2, restoredSnaps.get("vertex-src-2").getVersion());
        assertArrayEquals(new byte[]{0xD, 0xE}, restoredSnaps.get("vertex-src-2").getStateBytes());
    }

    @Test
    void manifestWithoutEnumeratorSnapshotsDeserializesToEmptyMap() {
        EpochManifest original = new EpochManifest(
                1L, "job-empty", "pipe-0", 0L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, null);

        byte[] serialized = CheckpointSerDe.serializeEpochManifest(original);
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(serializedSafe(serialized));

        assertNotNull(restored.getSourceEnumeratorSnapshots());
        assertTrue(restored.getSourceEnumeratorSnapshots().isEmpty(),
                "manifest without enumerator section must deserialize to empty map (not null)");
    }

    @Test
    void base64EncodingOfStateBytesIsDecodedBack() {
        // Verify the JSON encoding path explicitly: state bytes go through Base64 since
        // raw byte[] is not JSON-native. This guards against accidental UTF-8 mangling.
        byte[] binaryPayload = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x42};
        Map<String, SourceEnumeratorSnapshot> snaps = new LinkedHashMap<>();
        snaps.put("v-bin", new SourceEnumeratorSnapshot(3, binaryPayload));

        EpochManifest manifest = new EpochManifest(
                1L, "job-bin", "pipe-0", 0L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, null, snaps);

        byte[] serialized = CheckpointSerDe.serializeEpochManifest(manifest);
        String json = new String(serialized, java.nio.charset.StandardCharsets.UTF_8);
        // Sanity: the base64-encoded form of binaryPayload must appear in the JSON
        String expectedB64 = Base64.getEncoder().encodeToString(binaryPayload);
        assertTrue(json.contains(expectedB64),
                "expected base64-encoded state bytes in manifest JSON: " + expectedB64);

        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(serializedSafe(serialized));
        assertArrayEquals(binaryPayload, restored.getSourceEnumeratorSnapshots().get("v-bin").getStateBytes());
    }

    private static byte[] serializedSafe(byte[] bytes) {
        return bytes;
    }
}
