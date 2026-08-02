/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 34 (G39): focused tests for {@link KeyGroupRange} set operations.
 * Stage 35 will consume these for range-intersection partial restore.
 */
class TestKeyGroupRange {

    @Test
    void equalsHashCodeToStringComplete() {
        KeyGroupRange a = new KeyGroupRange(3, 10);
        KeyGroupRange b = new KeyGroupRange(3, 10);
        KeyGroupRange c = new KeyGroupRange(3, 11);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(a.toString().contains("3"));
        assertTrue(a.toString().contains("10"));
    }

    @Test
    void numberOfKeyGroupsAndPointRange() {
        assertEquals(7, new KeyGroupRange(3, 10).getNumberOfKeyGroups());
        assertEquals(1, new KeyGroupRange(5, 6).getNumberOfKeyGroups());
        assertTrue(new KeyGroupRange(5, 6).isPointRange());
        assertFalse(new KeyGroupRange(5, 7).isPointRange());
        assertTrue(new KeyGroupRange(4, 4).isEmpty());
        assertEquals(0, new KeyGroupRange(4, 4).getNumberOfKeyGroups());
    }

    @Test
    void contains() {
        KeyGroupRange r = new KeyGroupRange(3, 10);
        assertTrue(r.contains(3));
        assertTrue(r.contains(9));
        assertFalse(r.contains(2));
        assertFalse(r.contains(10));
        // sub-range containment
        assertTrue(r.contains(new KeyGroupRange(3, 10)));
        assertTrue(r.contains(new KeyGroupRange(4, 8)));
        assertFalse(r.contains(new KeyGroupRange(2, 3)));
        assertFalse(r.contains(new KeyGroupRange(9, 11)));
        assertFalse(r.contains(null));
    }

    @Test
    void intersectOverlapping() {
        KeyGroupRange r = new KeyGroupRange(3, 10);
        assertEquals(new KeyGroupRange(5, 8), r.intersect(new KeyGroupRange(5, 8)));
        assertEquals(new KeyGroupRange(5, 10), r.intersect(new KeyGroupRange(5, 12)));
    }

    @Test
    void intersectAdjacent() {
        // Adjacent (touching but disjoint) ranges have empty intersection.
        KeyGroupRange r = new KeyGroupRange(3, 10);
        assertEquals(KeyGroupRange.EMPTY, r.intersect(new KeyGroupRange(10, 15)));
        assertTrue(r.isAdjacent(new KeyGroupRange(10, 15)));
        assertFalse(r.overlaps(new KeyGroupRange(10, 15)));
    }

    @Test
    void intersectDisjoint() {
        KeyGroupRange r = new KeyGroupRange(3, 10);
        assertEquals(KeyGroupRange.EMPTY, r.intersect(new KeyGroupRange(20, 30)));
        assertTrue(r.intersect(new KeyGroupRange(20, 30)).isEmpty());
        assertFalse(r.overlaps(new KeyGroupRange(20, 30)));
    }

    @Test
    void intersectNullReturnsEmpty() {
        assertEquals(KeyGroupRange.EMPTY, new KeyGroupRange(0, 5).intersect(null));
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new KeyGroupRange(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new KeyGroupRange(5, 2));
    }
}
