/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 34 (G37/G38) focused tests for {@link KeyGroupAssignment}: stable-hash
 * determinism, distribution, key&#8594;group mapping bounds, and routing
 * parity with the legacy {@code (key.hashCode() & 0x7FFFFFFF) % shardCount}
 * formula for built-in value types.
 */
class TestKeyGroupAssignment {

    private static final int MAX_PARALLELISM = KeyGroup.DEFAULT_MAX_PARALLELISM; // 128

    // ==================== G38: stable-hash determinism ====================

    @Test
    void stableHashIsNullSafe() {
        assertEquals(0, KeyGroupAssignment.stableHash(null));
    }

    @Test
    void stableHashDeterministicAcrossRetriesForBuiltInTypes() {
        // Same value re-constructed multiple times -> same hash.
        assertEquals(KeyGroupAssignment.stableHash("hello"), KeyGroupAssignment.stableHash(new String("hello")));
        assertEquals(KeyGroupAssignment.stableHash(42L), KeyGroupAssignment.stableHash(Long.valueOf(42)));
        assertEquals(KeyGroupAssignment.stableHash(7), KeyGroupAssignment.stableHash(Integer.valueOf(7)));
    }

    @Test
    void stableHashDeterministicAfterSimulatedRestartForString() {
        // A "restart" is simulated by rebuilding the key in a new method frame.
        int first = KeyGroupAssignment.stableHash(buildKeyInNewFrame());
        int second = KeyGroupAssignment.stableHash(buildKeyInNewFrame());
        assertEquals(first, second);
    }

    private static String buildKeyInNewFrame() {
        return "restart-key-" + 1234;
    }

    @Test
    void stableHashEqualButNotSamePojoProducesSameHash() {
        // Two equal-but-not-same POJOs (TimeWindow) must hash identically under
        // the JSON path; their Object.hashCode() is value-stable here too, but
        // the point is that the result does not depend on identity.
        TimeWindow a = new TimeWindow(1000L, 2000L);
        TimeWindow b = new TimeWindow(1000L, 2000L);
        assertEquals(KeyGroupAssignment.stableHash(a), KeyGroupAssignment.stableHash(b));
    }

    @Test
    void stableHashDistributionAcrossBuckets() {
        int buckets = MAX_PARALLELISM;
        Map<Integer, Integer> counts = new HashMap<>();
        int n = 5000;
        for (int i = 0; i < n; i++) {
            int g = (KeyGroupAssignment.stableHash("key-" + i) & 0x7FFFFFFF) % buckets;
            counts.merge(g, 1, Integer::sum);
        }
        // No single bucket holds an unreasonable share; and many buckets are used.
        double avg = ((double) n) / buckets;
        for (Integer c : counts.values()) {
            assertTrue(c < avg * 3, "bucket over-filled: " + c + " vs avg " + avg);
        }
        assertTrue(counts.size() > buckets * 0.6, "distribution too skewed: only " + counts.size() + " buckets used");
    }

    // ==================== G37: key->group mapping ====================

    @Test
    void assignToKeyGroupIsInBoundsAndDeterministic() {
        for (int i = 0; i < 1000; i++) {
            String key = "k-" + i;
            int g = KeyGroupAssignment.assignToKeyGroup(key, MAX_PARALLELISM);
            assertTrue(g >= 0 && g < MAX_PARALLELISM, "group out of bounds: " + g);
            assertEquals(g, KeyGroupAssignment.assignToKeyGroup(key, MAX_PARALLELISM));
        }
    }

    @Test
    void assignToKeyGroupRejectsInvalidMaxParallelism() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.assignToKeyGroup("x", 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.assignToKeyGroup("x", -1));
    }

    @Test
    void assignToKeyGroupCoversAllGroups() {
        // With enough keys, every group in [0, maxParallelism) should be hit.
        int maxP = 32;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            used.add(KeyGroupAssignment.assignToKeyGroup("spread-" + i, maxP));
        }
        assertEquals(maxP, used.size());
    }

    // ==================== Routing parity (legacy formula) ====================

    @Test
    void routingParityForBuiltInTypesWhenMaxParallelismEqualsShardCount() {
        // For built-in value types, the new stable hash delegates to hashCode(),
        // so at maxParallelism == shardCount the bucket must match the legacy
        // (key.hashCode() & 0x7FFFFFFF) % shardCount formula exactly.
        int n = 8;
        checkParity("a-string-key", n);
        checkParity(Long.valueOf(123456789L), n);
        checkParity(Integer.valueOf(-42), n);
        checkParity("key-0", n);
        checkParity("", n);
    }

    private static void checkParity(Object key, int shardCount) {
        int legacy = (key.hashCode() & 0x7FFFFFFF) % shardCount;
        int now = KeyGroupAssignment.assignToKeyGroup(key, shardCount);
        assertEquals(legacy, now, "routing parity broken for " + key.getClass().getSimpleName());
    }

    // ==================== G39 / Phase 3: group->subtask mapping ====================

    @Test
    void keyGroupRangesForSubtasksAreDisjointAndCoverWholeSpace() {
        int maxP = 128;
        int parallelism = 4;
        Set<Integer> covered = new HashSet<>();
        int subtaskCount = 0;
        for (int i = 0; i < parallelism; i++) {
            KeyGroupRange r = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(maxP, parallelism, i);
            assertFalse(r.isEmpty(), "subtask " + i + " range must not be empty");
            for (int g = r.getStartKeyGroup(); g < r.getEndKeyGroup(); g++) {
                assertTrue(covered.add(g), "group " + g + " owned by >1 subtask");
            }
            subtaskCount++;
        }
        assertEquals(parallelism, subtaskCount);
        // Union must be exactly [0, maxParallelism).
        assertEquals(maxP, covered.size());
        for (int g = 0; g < maxP; g++) {
            assertTrue(covered.contains(g), "group " + g + " unowned");
        }
    }

    @Test
    void parallelismChangeKeepsKeyToGroupMappingStable() {
        // The core rescale invariant: a key's group depends only on
        // maxParallelism, NOT on parallelism. Changing parallelism only
        // reshuffles which subtask owns the group.
        int maxP = 128;
        String key = "rescale-probe";
        int groupAt4 = KeyGroupAssignment.assignToKeyGroup(key, maxP);
        int groupAt8 = KeyGroupAssignment.assignToKeyGroup(key, maxP);
        int groupAt16 = KeyGroupAssignment.assignToKeyGroup(key, maxP);
        assertEquals(groupAt4, groupAt8);
        assertEquals(groupAt8, groupAt16);

        // The owner subtask may differ across parallelism, but the group is fixed.
        int ownerAt4 = KeyGroupAssignment.assignKeyGroupToSubtask(groupAt4, maxP, 4);
        int ownerAt8 = KeyGroupAssignment.assignKeyGroupToSubtask(groupAt4, maxP, 8);
        assertTrue(ownerAt4 >= 0 && ownerAt4 < 4);
        assertTrue(ownerAt8 >= 0 && ownerAt8 < 8);
    }

    @Test
    void assignKeyGroupToSubtaskIsInverseOfRangeComputation() {
        int maxP = 128;
        for (int parallelism : new int[]{1, 2, 3, 4, 5, 8, 16, 128}) {
            for (int g = 0; g < maxP; g++) {
                int owner = KeyGroupAssignment.assignKeyGroupToSubtask(g, maxP, parallelism);
                KeyGroupRange ownerRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(maxP, parallelism, owner);
                assertTrue(ownerRange.contains(g),
                        "group " + g + " -> subtask " + owner + " whose range " + ownerRange + " doesn't contain it");
            }
        }
    }

    @Test
    void parallelismOneOwnsEverything() {
        KeyGroupRange r = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(128, 1, 0);
        assertEquals(new KeyGroupRange(0, 128), r);
    }

    @Test
    void rangesAreContiguous() {
        int maxP = 10;
        int parallelism = 3;
        int prevEnd = 0;
        for (int i = 0; i < parallelism; i++) {
            KeyGroupRange r = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(maxP, parallelism, i);
            assertEquals(prevEnd, r.getStartKeyGroup(), "range " + i + " not contiguous with previous");
            prevEnd = r.getEndKeyGroup();
        }
        assertEquals(maxP, prevEnd);
    }

    @Test
    void rangeComputationRejectsInvalidArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 8, 0)); // parallelism > maxP
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 4, -1));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(4, 4, 4)); // index == parallelism
    }
}
