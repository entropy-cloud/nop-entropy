/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 1 (D2): tests for the new coordinator-state checkpoint slot
 * — {@link EpochManifest#getSourceEnumeratorSnapshots()} keyed by source vertex id.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>the section round-trips through {@link EpochManifest} construction, and</li>
 *   <li>{@link SourceEnumeratorSnapshot} carries the version + bytes pair intact.</li>
 * </ul>
 *
 * <p>End-to-end JSON serialization round-trip is exercised at the runtime layer in
 * {@code TestCheckpointSerDeSourceEnumeratorSnapshots} (Stage 49 Phase 2).
 */
class TestSourceEnumeratorSnapshot {

    @Test
    void snapshotCarriesVersionAndBytes() {
        byte[] payload = {1, 2, 3, 4, 5};
        SourceEnumeratorSnapshot snap = new SourceEnumeratorSnapshot(7, payload);

        assertEquals(7, snap.getVersion());
        assertArrayEquals(payload, snap.getStateBytes());
    }

    @Test
    void snapshotDefaultConstructorHasEmptyState() {
        SourceEnumeratorSnapshot empty = new SourceEnumeratorSnapshot();

        assertEquals(0, empty.getVersion());
        // Default state bytes may be null; manifest serialization handles null as empty.
    }

    @Test
    void epochManifestDefaultsToEmptyEnumeratorSection() {
        EpochManifest manifest = new EpochManifest(
                1L, "job-1", "pipe-0", 100L,
                CheckpointType.CHECKPOINT, EpochState.DURABLE,
                Collections.emptyMap(), null, null);

        assertNotNull(manifest.getSourceEnumeratorSnapshots());
        assertTrue(manifest.getSourceEnumeratorSnapshots().isEmpty(),
                "manifest without enumerator snapshots must default to empty map, not null");
    }

    @Test
    void epochManifestPreservesEnumeratorSectionKeyedByVertexId() {
        Map<String, SourceEnumeratorSnapshot> snaps = new LinkedHashMap<>();
        snaps.put("vertex-src-1", new SourceEnumeratorSnapshot(1, new byte[]{0xA, 0xB}));
        snaps.put("vertex-src-2", new SourceEnumeratorSnapshot(2, new byte[]{0xC}));

        EpochManifest manifest = new EpochManifest(
                5L, "job-1", "pipe-0", 200L,
                CheckpointType.CHECKPOINT, EpochState.DURABLE,
                Collections.emptyMap(), null, null, snaps);

        Map<String, SourceEnumeratorSnapshot> out = manifest.getSourceEnumeratorSnapshots();
        assertEquals(2, out.size());
        assertEquals(1, out.get("vertex-src-1").getVersion());
        assertArrayEquals(new byte[]{0xA, 0xB}, out.get("vertex-src-1").getStateBytes());
        assertEquals(2, out.get("vertex-src-2").getVersion());
        assertArrayEquals(new byte[]{0xC}, out.get("vertex-src-2").getStateBytes());
    }

    @Test
    void sourceEnumeratorSnapshotsSectionIsImmutable() {
        Map<String, SourceEnumeratorSnapshot> input = new LinkedHashMap<>();
        input.put("v-1", new SourceEnumeratorSnapshot(1, new byte[]{1}));

        EpochManifest manifest = new EpochManifest(
                1L, "job-1", "pipe-0", 0L,
                CheckpointType.CHECKPOINT, EpochState.DURABLE,
                Collections.emptyMap(), null, null, input);

        Map<String, SourceEnumeratorSnapshot> out = manifest.getSourceEnumeratorSnapshots();
        assertEquals(1, out.size());

        // Mutating the original input map must not leak into the manifest
        input.put("v-2", new SourceEnumeratorSnapshot(2, new byte[]{2}));
        assertEquals(1, out.size(), "manifest enumerator-snapshot section must be a defensive copy");
    }
}
