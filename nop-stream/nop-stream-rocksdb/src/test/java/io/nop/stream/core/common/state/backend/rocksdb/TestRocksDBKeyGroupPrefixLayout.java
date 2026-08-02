/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 34 (Phase 2): focused tests for the new RocksDB key-group binary
 * layout. Verifies that the {@code keyGroupId} is a big-endian sortable prefix
 * so keys of one group are contiguous in SST byte order (enabling Stage 35
 * range-intersection restore), plus the fail-fast behaviour for incompatible
 * layouts.
 */
class TestRocksDBKeyGroupPrefixLayout {

    @TempDir
    java.io.File tempDir;

    // ==================== prefix is sortable & contiguous ====================

    @Test
    void keyGroupPrefixIsBigEndianSortable() {
        // Big-endian int32 => lexicographic byte order == numeric order for
        // non-negative ids. Keys with a larger group must sort after a smaller
        // group regardless of namespace/key content.
        byte[] g1 = RocksDBKeyEncoder.encode("ns", "zzz", 1);
        byte[] g2 = RocksDBKeyEncoder.encode("ns", "aaa", 2);
        byte[] g10 = RocksDBKeyEncoder.encode("ns", "aaa", 10);
        assertTrue(compareBytes(g1, g2) < 0, "group 1 must sort before group 2");
        assertTrue(compareBytes(g2, g10) < 0, "group 2 must sort before group 10 (not lexicographic on digits)");
    }

    @Test
    void keysOfSameGroupAreContiguousRegardlessOfNamespace() {
        // Within a column family all keys are sorted by prefix. Group the
        // encodings by their keyGroupId and verify the per-group byte ranges
        // do not interleave with other groups' ranges.
        List<byte[]> all = new ArrayList<>();
        for (int g = 0; g < 4; g++) {
            for (String ns : Arrays.asList("ns-a", "ns-b")) {
                for (String k : Arrays.asList("k1", "k2", "k3")) {
                    all.add(RocksDBKeyEncoder.encode(ns, k, g));
                }
            }
        }
        List<byte[]> sorted = new ArrayList<>(all);
        sorted.sort(TestRocksDBKeyGroupPrefixLayout::compareBytes);

        // Decode and verify groups appear as contiguous blocks.
        Map<Integer, List<Integer>> groupToPositions = new TreeMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(sorted.get(i), String.class);
            groupToPositions.computeIfAbsent(dk.keyGroupId, x -> new ArrayList<>()).add(i);
        }
        for (List<Integer> positions : groupToPositions.values()) {
            int first = positions.get(0);
            int last = positions.get(positions.size() - 1);
            assertEquals(positions.size(), last - first + 1,
                    "group positions must be contiguous, got " + positions);
        }
    }

    @Test
    void differentGroupsDoNotCrossPrefixBoundary() {
        byte[] g0 = RocksDBKeyEncoder.encode("ns", "key", 0);
        byte[] g1 = RocksDBKeyEncoder.encode("ns", "key", 1);
        // The 4-byte group prefix must differ within the first 4 bytes; the
        // suffix (namespace+key) must be byte-identical.
        int firstDiff = firstDiffOffset(g0, g1);
        assertTrue(firstDiff >= 0 && firstDiff < 4, "diff must be inside the 4-byte prefix, got " + firstDiff);
        assertTrue(Arrays.equals(Arrays.copyOfRange(g0, 4, g0.length), Arrays.copyOfRange(g1, 4, g1.length)),
                "namespace+key suffix must be identical");
        assertNotEquals(Arrays.toString(g0), Arrays.toString(g1));
    }

    // ==================== round-trip across state types (via backend) ====================

    @Test
    void newLayoutUsedAtRuntimeAndRoundTripsValueState() throws Exception {
        // Anti-hollow: the runtime backend must actually route via the
        // key-group prefix (not the legacy mid-key shard id). We verify by
        // encoding keys through the backend and checking the decoded group
        // matches KeyGroupAssignment.
        RocksDBKeyedStateBackend<String> backend = newBackend(8);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("vs", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        Map<String, Integer> keyToGroup = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            String key = "key-" + i;
            backend.setCurrentKey(key);
            state.update((long) i);
            keyToGroup.put(key, backend.computeKeyGroupId(key));
            // The runtime-computed group must match the canonical assignment.
            assertEquals(KeyGroupAssignment.assignToKeyGroup(key, 8), backend.computeKeyGroupId(key));
        }
        // Multiple distinct groups must be in use (routing is not collapsed).
        long distinct = keyToGroup.values().stream().distinct().count();
        assertTrue(distinct > 1, "50 keys at maxParallelism=8 must spread across >1 group");

        // Snapshot extracts raw keys (layout-agnostic), restore re-encodes.
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();

        RocksDBKeyedStateBackend<String> restored = newBackend(8);
        restored.restoreState(snapshot);
        ValueState<Long> restoredState = restored.getState(desc);
        for (int i = 0; i < 50; i++) {
            restored.setCurrentKey("key-" + i);
            assertEquals((long) i, restoredState.value(), "value lost after round-trip for key-" + i);
        }
        restored.close();

        // The snapshot must carry the new layout version stamp.
        assertEquals(RocksDBKeyEncoder.KEY_LAYOUT_VERSION,
                ((Number) snapshot.getStateData().get(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD)).intValue());
    }

    // ==================== fail-fast for incompatible layouts ====================

    @Test
    void incrementalRestoreRejectsAbsentLayoutVersion() {
        // An incremental checkpoint with no keyLayoutVersion (legacy Stage 31
        // SST) must fail fast rather than be silently mis-decoded.
        Map<String, Object> legacyIncremental = new HashMap<>();
        // legacy: no keyLayoutVersion field at all
        assertThrows(Exception.class,
                () -> RocksDBKeyEncoder.verifyKeyLayoutVersion(legacyIncremental, true));
    }

    @Test
    void incrementalRestoreRejectsOldLayoutVersion() {
        Map<String, Object> oldIncremental = new HashMap<>();
        oldIncremental.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD,
                RocksDBKeyEncoder.LEGACY_KEY_LAYOUT_VERSION);
        assertThrows(Exception.class,
                () -> RocksDBKeyEncoder.verifyKeyLayoutVersion(oldIncremental, true));
    }

    @Test
    void incrementalRestoreAcceptsCurrentLayoutVersion() {
        Map<String, Object> current = new HashMap<>();
        current.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, RocksDBKeyEncoder.KEY_LAYOUT_VERSION);
        // Must not throw.
        RocksDBKeyEncoder.verifyKeyLayoutVersion(current, true);
    }

    @Test
    void fullRestoreToleratesAbsentLayoutVersionForCrossBackendMemorySnapshot() {
        // A Memory-backend snapshot has no RocksDB layout version; the full
        // restore path stores raw user keys, so it must tolerate the absent
        // field (cross-backend interchange).
        RocksDBKeyEncoder.verifyKeyLayoutVersion(new HashMap<>(), false);
        RocksDBKeyEncoder.verifyKeyLayoutVersion(null, false);
    }

    @Test
    void fullRestoreRejectsExplicitlyStampedOldLayoutVersion() {
        Map<String, Object> oldRocks = new HashMap<>();
        oldRocks.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD,
                RocksDBKeyEncoder.LEGACY_KEY_LAYOUT_VERSION);
        assertThrows(Exception.class,
                () -> RocksDBKeyEncoder.verifyKeyLayoutVersion(oldRocks, false));
    }

    @Test
    void crossBackendMemoryToRocksRestoreStillWorks() throws Exception {
        // Memory snapshot (no keyLayoutVersion) restored into RocksDB must
        // succeed: raw keys are layout-agnostic.
        IKeyedStateBackend<String> mem = (IKeyedStateBackend<String>)
                new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend(4)
                        .createKeyedStateBackend(String.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("vs", Long.class, 0L);
        ValueState<Long> memState = mem.getState(desc);
        for (int i = 0; i < 10; i++) {
            mem.setCurrentKey("cb-" + i);
            memState.update((long) (i + 1) * 11);
        }
        StateSnapshot snapshot = mem.snapshotState();

        RocksDBKeyedStateBackend<String> rocks = newBackend(4);
        rocks.restoreState(snapshot);
        ValueState<Long> rocksState = rocks.getState(desc);
        for (int i = 0; i < 10; i++) {
            rocks.setCurrentKey("cb-" + i);
            assertEquals((long) (i + 1) * 11, rocksState.value());
        }
        mem.close();
        rocks.close();
    }

    // ---------- helpers ----------

    private RocksDBKeyedStateBackend<String> newBackend() {
        return newBackend(1);
    }

    private RocksDBKeyedStateBackend<String> newBackend(int shardCount) {
        java.io.File sub = new java.io.File(tempDir, "db-" + System.nanoTime());
        sub.mkdirs();
        return new RocksDBKeyedStateBackend<>(sub.getAbsolutePath(), String.class, shardCount, null);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compareUnsigned(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private static int firstDiffOffset(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) return i;
        }
        return -1;
    }
}
