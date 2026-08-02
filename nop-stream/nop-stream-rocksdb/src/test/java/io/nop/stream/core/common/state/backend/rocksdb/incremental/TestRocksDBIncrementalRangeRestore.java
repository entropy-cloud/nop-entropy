/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb.incremental;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 35 (Stage 31 deferred item): real key-group range restore from an
 * incremental RocksDB checkpoint. The SST files produced by the Stage 34 v2
 * encoder carry the key-group id as a big-endian sortable prefix, so
 * {@link RocksDBIncrementalRestore#restoreRangeInto} can range-scan the
 * reconstructed DB and copy only the entries whose group falls in the target
 * range — a true SST range scan, not an in-memory filter.
 *
 * <p>Also covers the fail-fast contract: an incremental checkpoint restore
 * without a configured {@link ISegmentStore} throws rather than silently
 * degrading to a full-JSON (empty) restore.
 */
public class TestRocksDBIncrementalRangeRestore {

    private static final int MAX_P = 16;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    @TempDir
    Path tempDir;

    private static Long readValue(RocksDBKeyedStateBackend<String> backend, String key) throws Exception {
        backend.setCurrentKey(key);
        return backend.getState(COUNT).value();
    }

    /** Build a source backend, write keys, take an incremental snapshot, materialize SSTs. */
    private StateSnapshot takeIncrementalSnapshot(Map<String, Long> written) throws Exception {
        java.io.File srcDir = tempDir.resolve("inc-src").toFile();
        srcDir.mkdirs();
        RocksDBKeyedStateBackend<String> source = new RocksDBKeyedStateBackend<>(
                srcDir.getAbsolutePath(), String.class, MAX_P, null);
        source.setIncrementalCheckpointEnabled(true);
        source.setCheckpointBaseDir(tempDir.resolve("inc-base").toString());

        ValueState<Long> state = source.getState(COUNT);
        for (int i = 0; i < 40; i++) {
            String key = "inc-key-" + i;
            long val = 7000L + i;
            source.setCurrentKey(key);
            state.update(val);
            written.put(key, val);
        }
        StateSnapshot snapshot = source.snapshotState();
        source.close();
        return snapshot;
    }

    private ISegmentStore materializeSegments(StateSnapshot snapshot) throws Exception {
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));
        Object marker = snapshot.getStateData().get("__incremental_checkpoint__");
        io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult result =
                (io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult) marker;
        for (SharedStateHandle h : result.getSstHandles()) {
            store.storeSegment(Path.of(h.getFilePath()), h.getContentHash());
        }
        return store;
    }

    @Test
    public void incrementalRangeRestoreKeepsOnlyOwnedKeys() throws Exception {
        Map<String, Long> written = new LinkedHashMap<>();
        StateSnapshot snapshot = takeIncrementalSnapshot(written);
        ISegmentStore store = materializeSegments(snapshot);

        // Restore into 4 subtask backends, each with its own KeyGroupRange, via the
        // incremental MARKER path (real SST range scan).
        int keptTotal = 0;
        for (int sub = 0; sub < 4; sub++) {
            java.io.File dstDir = tempDir.resolve("inc-dst-" + sub).toFile();
            dstDir.mkdirs();
            RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                    dstDir.getAbsolutePath(), String.class, MAX_P, null);
            backend.setSegmentStore(store);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            for (Map.Entry<String, Long> e : written.entrySet()) {
                int gid = KeyGroupAssignment.assignToKeyGroup(e.getKey(), MAX_P);
                Long val = readValue(backend, e.getKey());
                if (range.contains(gid)) {
                    assertEquals(e.getValue(), val, "owned key " + e.getKey() + " must be range-restored on subtask " + sub);
                    keptTotal++;
                } else {
                    // Non-owned key must NOT carry its real restored value. The freshly
                    // created state's descriptor default (0L or null) is acceptable; what
                    // matters is the actual written value is absent.
                    assertEquals(false, e.getValue().equals(val),
                            "non-owned key " + e.getKey() + " must NOT be range-restored on subtask " + sub
                                    + " (got " + val + ")");
                }
            }
            backend.close();
        }
        assertEquals(40, keptTotal, "4 subtask ranges must partition all 40 keys via SST range scan");
    }

    @Test
    public void incrementalRestoreWithoutSegmentStoreFailsFast() throws Exception {
        Map<String, Long> written = new LinkedHashMap<>();
        StateSnapshot snapshot = takeIncrementalSnapshot(written);

        java.io.File dstDir = tempDir.resolve("inc-nostore").toFile();
        dstDir.mkdirs();
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dstDir.getAbsolutePath(), String.class, MAX_P, null);
        // No segment store configured: must throw (no silent empty/full fallback).
        assertThrows(Exception.class, () -> backend.restoreState(snapshot),
                "incremental restore without ISegmentStore must fail fast");
        backend.close();
    }

    @Test
    public void incrementalRestoreFullWhenNoRange() throws Exception {
        Map<String, Long> written = new LinkedHashMap<>();
        StateSnapshot snapshot = takeIncrementalSnapshot(written);
        ISegmentStore store = materializeSegments(snapshot);

        java.io.File dstDir = tempDir.resolve("inc-full").toFile();
        dstDir.mkdirs();
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dstDir.getAbsolutePath(), String.class, MAX_P, null);
        backend.setSegmentStore(store);
        // No target range => full restore of the whole reconstructed DB.
        backend.restoreState(snapshot);
        for (Map.Entry<String, Long> e : written.entrySet()) {
            assertEquals(e.getValue(), readValue(backend, e.getKey()),
                    "full incremental restore must contain every key");
        }
        backend.close();
    }
}
