/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIntArrayMap {

    @Test
    public void testBoundsReturnNullForGetAndContains() {
        IntArrayMap<String> map = new IntArrayMap<>(3);
        assertTrue(map.isEmpty());
        assertEquals(3, map.capacity());

        // 越界 get / containsKey 返回 null/false，不抛异常
        assertNull(map.get(-1));
        assertNull(map.get(3));
        assertFalse(map.containsKey(-1));
        assertFalse(map.containsKey(0));
        assertFalse(map.remove(-1, "x"));
    }

    @Test
    public void testPutSetAndSizeAccounting() {
        IntArrayMap<String> map = new IntArrayMap<>(4);
        assertNull(map.put(1, "a"));
        assertEquals(1, map.size());

        // put 覆盖：返回旧值，size 不变
        assertEquals("a", map.put(1, "b"));
        assertEquals(1, map.size());
        assertEquals("b", map.get(1));

        // set 覆盖：不改变 size
        map.set(1, "c");
        assertEquals(1, map.size());
        assertEquals("c", map.get(1));

        map.set(3, "d");
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());

        // set/put 越界写被 Guard 拒绝
        assertThrows(Exception.class, () -> map.set(4, "x"));
        assertThrows(Exception.class, () -> map.put(4, "x"));
        assertThrows(Exception.class, () -> map.set(-1, "x"));
    }

    @Test
    public void testPutNullValueCountsAsPresent() {
        IntArrayMap<String> map = new IntArrayMap<>(2);
        map.put(0, null);
        assertEquals(1, map.size());
        assertTrue(map.containsKey(0));
        assertNull(map.get(0));
        // put null 也是"占用"，覆盖时不递增 size
        map.put(0, "v");
        assertEquals(1, map.size());
    }

    @Test
    public void testRemoveByIndexAndIdentityRemove() {
        String value = new String("v");
        IntArrayMap<String> map = new IntArrayMap<>(4);
        map.put(1, value);
        map.put(2, "keep");

        assertEquals("v", map.remove(1));
        assertEquals(1, map.size());
        assertNull(map.remove(1));
        assertNull(map.remove(99));

        // remove(index, value) 采用引用相等：值相等但非同一实例不移除
        map.put(0, new String("v"));
        assertFalse(map.remove(0, new String("v")));
        assertTrue(map.containsKey(0));
        assertTrue(map.remove(0, map.get(0)));
        assertFalse(map.containsKey(0));
    }

    @Test
    public void testKeysIteratorSkipsUndefinedAndFailsAtEnd() {
        IntArrayMap<String> map = new IntArrayMap<>(5);
        map.put(1, "a");
        map.put(3, "b");

        IntArray keys = map.keySet();
        assertEquals(2, keys.size());
        assertEquals(1, keys.get(0));
        assertEquals(3, keys.get(1));

        List<Integer> iterated = new ArrayList<>();
        var it = map.keysIterator();
        while (it.hasNext()) {
            iterated.add(it.nextInt());
        }
        assertEquals(Arrays.asList(1, 3), iterated);
        // KeysIterator 耗尽后 nextInt 抛 IllegalStateException("iterator eof")
        assertThrows(IllegalStateException.class, it::nextInt);

        // 未占用位不进入迭代
        map.put(0, "z");
        var it2 = map.keysIterator();
        it2.nextInt();
        assertEquals(1, it2.nextInt());
    }

    @Test
    public void testForEachEntryVisitsOnlyDefinedSlots() {
        IntArrayMap<String> map = new IntArrayMap<>(4);
        map.put(0, "a");
        map.put(2, "c");
        List<String> seen = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        map.forEachEntry((v, i) -> {
            seen.add(v);
            indices.add(i);
        });
        assertEquals(Arrays.asList("a", "c"), seen);
        assertEquals(Arrays.asList(0, 2), indices);
    }

    @Test
    public void testClearAndClone() {
        IntArrayMap<String> map = new IntArrayMap<>(3);
        map.put(0, "a");
        map.put(2, "c");
        IntArrayMap<String> clone = map.cloneInstance();
        assertEquals(2, clone.size());
        assertEquals("a", clone.get(0));

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertNull(map.get(0));
        // 克隆独立于原 map
        assertEquals(2, clone.size());
        assertEquals("c", clone.get(2));
    }

    @Test
    public void testDefaultValueAbsentSlots() {
        IntArrayMap<String> map = new IntArrayMap<>(3);
        map.put(2, "only");
        assertNull(map.get(0));
        assertNull(map.get(1));
        assertFalse(map.containsKey(1));
        assertTrue(map.containsKey(2));
    }
}
