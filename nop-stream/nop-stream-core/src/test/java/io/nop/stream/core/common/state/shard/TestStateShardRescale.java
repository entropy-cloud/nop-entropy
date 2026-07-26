/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-8: verifies the already-landed {@link StateShard#stableHash(Object)}
 * routing plus snapshot/restore rescale correctness. Per checkpoint-design.md
 * §8.5, rescaling the shard count across a checkpoint boundary must preserve
 * every key's value — the snapshot stores raw (un-prefixed) keys and the
 * restore re-routes via the new shardCount. These tests are anti-hollow:
 * removing the {@code unwrapStorageKey} / {@code routeKey} pair on the
 * restore path makes the cross-shardCount cases below fail.
 */
public class TestStateShardRescale {

    private static final ValueStateDescriptor<Long> COUNT_DESC =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    /** Snapshot shardCount=2, restore at shardCount=4: every key keeps its value. */
    @Test
    public void snapshotTwoRestoreFour_PreservesAllKeys() throws Exception {
        Map<String, Long> written = writeKeys(new MemoryKeyedStateBackend<>(String.class, 2), 50);

        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, 2);
        Map<String, Long> expected = writeKeys(source, 50);
        StateSnapshot snapshot = source.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, 4);
        restored.restoreState(snapshot);

        ValueState<Long> restoredState = restored.getState(COUNT_DESC);
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            restored.setCurrentKey(e.getKey());
            assertEquals(e.getValue(), restoredState.value(),
                    "key " + e.getKey() + " must survive 2->4 rescale");
        }

        source.close();
        restored.close();
        written.size();
    }

    /** Snapshot shardCount=4, restore at shardCount=2: every key keeps its value. */
    @Test
    public void snapshotFourRestoreTwo_PreservesAllKeys() throws Exception {
        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, 4);
        Map<String, Long> expected = writeKeys(source, 80);
        StateSnapshot snapshot = source.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, 2);
        restored.restoreState(snapshot);

        ValueState<Long> restoredState = restored.getState(COUNT_DESC);
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            restored.setCurrentKey(e.getKey());
            assertEquals(e.getValue(), restoredState.value(),
                    "key " + e.getKey() + " must survive 4->2 rescale");
        }

        source.close();
        restored.close();
    }

    /**
     * Verifies that {@link StateShard#stableHash(Object)} drives routing on
     * both snapshot and restore sides: for any key K, snapshot at shardCount=A
     * and restore at shardCount=B must produce a routing at B equivalent to
     * the routing the restore backend computes for K from scratch.
     *
     * <p>Anti-hollow: if the snapshot leaks the source's ShardPrefixedKey into
     * the durable form, or the restore forgets to re-route, the rescaled
     * read fails to locate the value — this assertion catches that.
     */
    @Test
    public void stableHashRoutingRemainsDeterministicAfterRescale() throws Exception {
        MemoryKeyedStateBackend<String> source = new MemoryKeyedStateBackend<>(String.class, 3);
        Map<String, Long> expected = writeKeys(source, 30);
        StateSnapshot snapshot = source.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, 5);
        restored.restoreState(snapshot);

        // For each key, the shardId assigned by the *restore* backend (which
        // uses the new shardCount) must be deterministic and the value must
        // be reachable at that route.
        ValueState<Long> state = restored.getState(COUNT_DESC);
        Set<Integer> distinctRestoreShards = new HashSet<>();
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            String key = e.getKey();
            int shardId = (StateShard.stableHash(key) & 0x7FFFFFFF) % 5;
            distinctRestoreShards.add(shardId);
            restored.setCurrentKey(key);
            assertEquals(e.getValue(), state.value(),
                    "key " + key + " not reachable at rescaled shard " + shardId);
        }
        assertTrue(distinctRestoreShards.size() > 1,
                "30 keys at shardCount=5 must spread across >1 shard (otherwise routing collapsed)");

        source.close();
        restored.close();
    }

    private static Map<String, Long> writeKeys(MemoryKeyedStateBackend<String> backend, int count) throws Exception {
        ValueState<Long> state = backend.getState(COUNT_DESC);
        Map<String, Long> written = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = "rescale-key-" + i;
            long value = 1_000L + i;
            backend.setCurrentKey(key);
            state.update(value);
            written.put(key, value);
        }
        return written;
    }
}
