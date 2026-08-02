/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage 35: per-subtask KeyGroupRange partial restore on the RocksDB backend
 * (full-JSON snapshot path). Parallel to {@code TestKeyGroupRangeBackendRestore}
 * for the Memory backend.
 *
 * <p>Anti-hollow: a full snapshot containing keys across all groups is restored
 * into a backend whose target {@link KeyGroupRange} covers only one subtask's
 * slice; after restore the backend must hold <em>only</em> the in-range keys.
 */
public class TestRocksDBKeyGroupRangeRestore {

    private static final int MAX_P = 16;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    @TempDir
    Path tempDir;

    private static Long readValue(RocksDBKeyedStateBackend<String> backend, String key) throws Exception {
        backend.setCurrentKey(key);
        return backend.getState(COUNT).value();
    }

    @Test
    public void rangeRestoreKeepsOnlyOwnedKeys() throws Exception {
        java.io.File srcDir = tempDir.resolve("rocks-src").toFile();
        srcDir.mkdirs();
        RocksDBKeyedStateBackend<String> source = new RocksDBKeyedStateBackend<>(
                srcDir.getAbsolutePath(), String.class, MAX_P, null);

        ValueState<Long> state = source.getState(COUNT);
        java.util.Map<String, Long> allKeys = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            String key = "rocks-key-" + i;
            long val = 5000L + i;
            source.setCurrentKey(key);
            state.update(val);
            allKeys.put(key, val);
        }
        StateSnapshot snapshot = source.snapshotState();
        source.close();

        // Restore into 4 subtask backends, each with its own KeyGroupRange.
        int keptTotal = 0;
        for (int sub = 0; sub < 4; sub++) {
            java.io.File dstDir = tempDir.resolve("rocks-dst-" + sub).toFile();
            dstDir.mkdirs();
            RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                    dstDir.getAbsolutePath(), String.class, MAX_P, null);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            for (java.util.Map.Entry<String, Long> e : allKeys.entrySet()) {
                int gid = KeyGroupAssignment.assignToKeyGroup(e.getKey(), MAX_P);
                Long val = readValue(backend, e.getKey());
                if (range.contains(gid)) {
                    assertEquals(e.getValue(), val,
                            "owned key " + e.getKey() + " must be restored on subtask " + sub);
                    keptTotal++;
                } else {
                    assertEquals(null, val, "non-owned key " + e.getKey() + " must NOT be restored on subtask " + sub);
                }
            }
            backend.close();
        }
        assertEquals(50, keptTotal, "4 subtask ranges must partition all 50 keys");
    }

    @Test
    public void noRangeRestoresAllBackwardCompatible() throws Exception {
        java.io.File srcDir = tempDir.resolve("rocks-src2").toFile();
        srcDir.mkdirs();
        RocksDBKeyedStateBackend<String> source = new RocksDBKeyedStateBackend<>(
                srcDir.getAbsolutePath(), String.class, MAX_P, null);
        ValueState<Long> state = source.getState(COUNT);
        java.util.Map<String, Long> allKeys = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            String key = "rk-" + i;
            source.setCurrentKey(key);
            state.update((long) i);
            allKeys.put(key, (long) i);
        }
        StateSnapshot snapshot = source.snapshotState();
        source.close();

        java.io.File dstDir = tempDir.resolve("rocks-dst-full").toFile();
        dstDir.mkdirs();
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dstDir.getAbsolutePath(), String.class, MAX_P, null);
        backend.restoreState(snapshot);
        for (java.util.Map.Entry<String, Long> e : allKeys.entrySet()) {
            assertEquals(e.getValue().longValue(), readValue(backend, e.getKey()));
        }
        backend.close();
    }
}
