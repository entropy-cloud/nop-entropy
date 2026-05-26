/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Tests for StateShard routing in MemoryKeyedStateBackend.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>shardCount=1 behavior is unchanged (backward compatible)</li>
 *   <li>shardCount>1 routes keys to different shards</li>
 *   <li>StateShard.stableHash is deterministic (same key → same shardId)</li>
 * </ul>
 */
public class TestStateShardRouting {

    // ==================== shardCount=1 backward compatibility ====================

    @Test
    public void testShardCountOne_BehavesIdenticallyToOriginal() throws IOException {
        // Two backends: old-style (no shardCount) and new-style (shardCount=1)
        MemoryKeyedStateBackend<String> oldBackend = new MemoryKeyedStateBackend<>(String.class);
        MemoryKeyedStateBackend<String> newBackend = new MemoryKeyedStateBackend<>(String.class, 1);

        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);

        ValueState<Long> oldState = oldBackend.getState(desc);
        ValueState<Long> newState = newBackend.getState(desc);

        String[] keys = {"key1", "key2", "key3", "key4", "key5"};

        for (String key : keys) {
            oldBackend.setCurrentKey(key);
            oldState.update((long) key.hashCode());

            newBackend.setCurrentKey(key);
            newState.update((long) key.hashCode());
        }

        // Verify all values match
        for (String key : keys) {
            oldBackend.setCurrentKey(key);
            newBackend.setCurrentKey(key);
            assertEquals(oldState.value(), newState.value(),
                    "Values should match for key: " + key);
        }
    }

    @Test
    public void testMemoryStateBackend_DefaultConstructorIsShardCountOne() throws IOException {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);

        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        backend.setCurrentKey("key1");
        state.update(42L);
        assertEquals(42L, state.value());

        backend.setCurrentKey("key2");
        assertEquals(0L, state.value());

        backend.close();
    }

    // ==================== shardCount>1 routing ====================

    @Test
    public void testShardCountTwo_KeysRouteToDifferentShards() throws IOException {
        // With shardCount=2, find two keys that route to different shards
        String key0 = findKeyForShard(2, 0);
        String key1 = findKeyForShard(2, 1);

        assertNotNull(key0, "Should find a key for shard 0");
        assertNotNull(key1, "Should find a key for shard 1");
        assertNotEquals(key0, key1, "Keys should be different");

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, 2);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        // Set value for key0
        backend.setCurrentKey(key0);
        state.update(100L);

        // Set value for key1
        backend.setCurrentKey(key1);
        state.update(200L);

        // Verify independence
        backend.setCurrentKey(key0);
        assertEquals(100L, state.value(), "key0 should have its own value");

        backend.setCurrentKey(key1);
        assertEquals(200L, state.value(), "key1 should have its own value");

        backend.close();
    }

    @Test
    public void testShardCountFour_KeysAreIsolatedAcrossShards() throws IOException {
        int shardCount = 4;
        // Find one key per shard
        String[] shardKeys = new String[shardCount];
        for (int i = 0; i < shardCount; i++) {
            shardKeys[i] = findKeyForShard(shardCount, i);
            assertNotNull(shardKeys[i], "Should find key for shard " + i);
        }

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, shardCount);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("val", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        // Set unique value per shard key
        for (int i = 0; i < shardCount; i++) {
            backend.setCurrentKey(shardKeys[i]);
            state.update((long) (i + 1) * 1000);
        }

        // Verify all values independently
        for (int i = 0; i < shardCount; i++) {
            backend.setCurrentKey(shardKeys[i]);
            assertEquals((long) (i + 1) * 1000, state.value(),
                    "Shard " + i + " key should have correct value");
        }

        backend.close();
    }

    @Test
    public void testShardCountTwo_SameShardKeysShareStorage() throws IOException {
        // Find two different keys that hash to the same shard
        int shardCount = 2;
        String keyA = findKeyForShard(shardCount, 0);
        String keyB = findKeyForShard(shardCount, 0);
        // Make sure they're different keys
        if (keyA.equals(keyB)) {
            // Find another
            keyB = findKeyForShard(shardCount, 0, keyA);
        }
        assertNotEquals(keyA, keyB, "Should have two different keys in same shard");

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, shardCount);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        backend.setCurrentKey(keyA);
        state.update(111L);

        backend.setCurrentKey(keyB);
        state.update(222L);

        // They should still be independent (different keys)
        backend.setCurrentKey(keyA);
        assertEquals(111L, state.value(), "keyA should retain its value");

        backend.setCurrentKey(keyB);
        assertEquals(222L, state.value(), "keyB should retain its value");

        backend.close();
    }

    // ==================== Determinism ====================

    @Test
    public void testStableHashIsDeterministic() {
        // Same key should always produce same shardId
        String key = "test-determinism-key";
        int shardCount = 16;

        int shard1 = Math.abs(StateShard.stableHash(key)) % shardCount;
        int shard2 = Math.abs(StateShard.stableHash(key)) % shardCount;
        int shard3 = Math.abs(StateShard.stableHash(key)) % shardCount;

        assertEquals(shard1, shard2, "stableHash should be deterministic");
        assertEquals(shard2, shard3, "stableHash should be deterministic");
    }

    @Test
    public void testStableHashDifferentKeysDifferentShards() {
        // With enough keys and shards, keys should distribute across shards
        int shardCount = 4;
        Set<Integer> usedShards = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String key = "key-" + i;
            int shardId = Math.abs(StateShard.stableHash(key)) % shardCount;
            usedShards.add(shardId);
        }

        // With 100 keys and 4 shards, we should hit all 4 shards
        assertEquals(shardCount, usedShards.size(),
                "100 keys should distribute across all 4 shards");
    }

    // ==================== Snapshot/Restore with Sharding ====================

    @Test
    public void testSnapshotRestore_WithSharding() throws Exception {
        int shardCount = 3;
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, shardCount);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        // Write values for multiple keys
        for (int i = 0; i < 10; i++) {
            backend.setCurrentKey("key-" + i);
            state.update((long) i * 10);
        }

        // Snapshot
        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot);

        // Restore into a new backend with same shardCount
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, shardCount);
        restored.restoreState(snapshot);

        ValueState<Long> restoredState = restored.getState(desc);
        for (int i = 0; i < 10; i++) {
            restored.setCurrentKey("key-" + i);
            assertEquals((long) i * 10, restoredState.value(),
                    "Restored value for key-" + i + " should match");
        }

        backend.close();
        restored.close();
    }

    @Test
    public void testSnapshotRestore_ShardCountOneBackwardCompatible() throws Exception {
        // Snapshot with shardCount=1, restore with shardCount=1
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("name", String.class);
        ValueState<String> state = backend.getState(desc);

        backend.setCurrentKey("user1");
        state.update("Alice");
        backend.setCurrentKey("user2");
        state.update("Bob");

        StateSnapshot snapshot = backend.snapshotState();

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);

        ValueState<String> restoredState = restored.getState(desc);
        restored.setCurrentKey("user1");
        assertEquals("Alice", restoredState.value());
        restored.setCurrentKey("user2");
        assertEquals("Bob", restoredState.value());

        backend.close();
        restored.close();
    }

    @Test
    public void testMemoryStateBackend_WithShardCountConstructor() throws IOException {
        MemoryStateBackend stateBackend = new MemoryStateBackend(4);
        IKeyedStateBackend<String> backend = stateBackend.createKeyedStateBackend(String.class);

        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        for (int i = 0; i < 20; i++) {
            backend.setCurrentKey("key-" + i);
            state.update((long) i);
        }

        for (int i = 0; i < 20; i++) {
            backend.setCurrentKey("key-" + i);
            assertEquals((long) i, state.value(), "Value for key-" + i + " should be correct");
        }

        backend.close();
    }

    // ==================== Validation ====================

    @Test
    public void testConstructor_RejectsInvalidShardCount() {
        assertThrows(StreamException.class, () ->
                new MemoryKeyedStateBackend<>(String.class, 0));
        assertThrows(StreamException.class, () ->
                new MemoryKeyedStateBackend<>(String.class, -1));
        assertThrows(StreamException.class, () ->
                new MemoryStateBackend(0));
        assertThrows(StreamException.class, () ->
                new MemoryStateBackend(-1));
    }

    // ==================== Helper methods ====================

    /**
     * Find a String key that routes to the specified shardId.
     */
    private static String findKeyForShard(int shardCount, int targetShardId) {
        return findKeyForShard(shardCount, targetShardId, null);
    }

    /**
     * Find a String key that routes to the specified shardId, excluding a specific key.
     */
    private static String findKeyForShard(int shardCount, int targetShardId, String exclude) {
        for (int i = 0; i < 10000; i++) {
            String candidate = "shard-key-" + i;
            if (candidate.equals(exclude)) continue;
            int shardId = Math.abs(StateShard.stableHash(candidate)) % shardCount;
            if (shardId == targetShardId) {
                return candidate;
            }
        }
        return null;
    }
}
