/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLongHashMap {

    @Test
    public void testInvalidLoadFactorRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LongHashMap<String>(8, 0f));
        assertThrows(IllegalArgumentException.class, () -> new LongHashMap<String>(8, 1.1f));
        // 边界值 1.0 合法
        new LongHashMap<String>(8, 1.0f);
    }

    @Test
    public void testPutGetRemoveBasics() {
        LongHashMap<String> map = new LongHashMap<>();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertNull(map.get(1));
        assertFalse(map.containsKey(1));
        assertEquals("{}", map.toString());

        assertNull(map.put(1, "a"));
        assertEquals("a", map.get(1));
        assertTrue(map.containsKey(1));
        assertEquals(1, map.size());

        // 覆盖写返回旧值，size 不变
        assertEquals("a", map.put(1, "b"));
        assertEquals(1, map.size());
        assertEquals("b", map.get(1));

        assertEquals("b", map.remove(1));
        assertNull(map.get(1));
        assertEquals(0, map.size());
        assertNull(map.remove(1));

        // 装箱 key 走 Map 接口入口
        map.put(Long.valueOf(2), "x");
        assertEquals("x", map.get(Long.valueOf(2)));
        assertTrue(map.containsKey(Long.valueOf(2)));
        assertEquals("x", map.remove(Long.valueOf(2)));
    }

    @Test
    public void testNullValueIsDistinguishableFromAbsentKey() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(5, null);
        assertEquals(1, map.size());
        assertTrue(map.containsKey(5));
        assertNull(map.get(5));
        // null 值参与 containsValue 匹配
        assertTrue(map.containsValue(null));
        assertFalse(map.containsValue("a"));
        // remove 命中 null 值条目
        assertNull(map.remove(5));
        assertEquals(0, map.size());
        assertFalse(map.containsKey(5));
    }

    @Test
    public void testCollisionProbingKeepsAllEntries() {
        // 容量 8，key 0/8/16 的 hash & 7 均为 0，强制线性探测
        LongHashMap<String> map = new LongHashMap<>(8, 1.0f);
        map.put(0, "a");
        map.put(8, "b");
        map.put(16, "c");
        assertEquals(3, map.size());
        assertEquals("a", map.get(0));
        assertEquals("b", map.get(8));
        assertEquals("c", map.get(16));

        // 删除探测链中间节点后，链尾仍可被找到（增量 rehash 语义）
        assertEquals("b", map.remove(8));
        assertEquals("c", map.get(16));
        assertNull(map.get(8));
    }

    @Test
    public void testRehashPreservesAllEntries() {
        LongHashMap<Integer> map = new LongHashMap<>(4);
        for (long i = 0; i < 1000; i++) {
            Integer value = (int) i;
            assertNull(map.put(i, value));
        }
        assertEquals(1000, map.size());
        for (long i = 0; i < 1000; i++) {
            assertEquals((int) i, map.get(i));
        }
        // 抽样删除后再校验
        for (long i = 0; i < 500; i++) {
            assertEquals((int) i, map.remove(i));
        }
        assertEquals(500, map.size());
        // 未删除的尾段仍可读，已删除的头段不可见
        assertEquals(999, map.get(999));
        assertNull(map.get(0));
    }

    @Test
    public void testNegativeKeys() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(-1, "neg");
        map.put(Long.MIN_VALUE, "min");
        assertEquals("neg", map.get(-1));
        assertEquals("min", map.get(Long.MIN_VALUE));
        assertTrue(map.containsKey(-1));
        assertEquals("neg", map.remove(-1));
        assertFalse(map.containsKey(-1));
    }

    @Test
    public void testKeySetEntrySetAndIteratorRemove() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");

        assertEquals(3, map.keySet().size());
        assertTrue(map.keySet().contains(1L));
        assertTrue(map.keySet().containsAll(java.util.Set.of(1L, 2L, 3L)));
        assertTrue(map.keySet().remove(1L));
        assertFalse(map.keySet().remove(99L));
        assertEquals(2, map.size());
        assertFalse(map.containsKey(1));

        // entrySet 迭代 + setValue + 迭代删除
        int visited = 0;
        Iterator<Map.Entry<Long, String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, String> e = it.next();
            if (e.getKey() == 2L) {
                e.setValue("B");
            }
            visited++;
            if (e.getKey() == 3L) {
                it.remove();
            }
        }
        assertEquals(2, visited);
        assertEquals("B", map.get(2));
        assertFalse(map.containsKey(3));
        assertEquals(1, map.size());
    }

    @Test
    public void testEntriesPrimitiveIterationAndSetValue() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(7, "a");
        map.put(9, "b");

        long sumKeys = 0;
        int count = 0;
        for (MapOfLong.PrimitiveEntry<String> e : map.entries()) {
            sumKeys += e.key();
            count++;
            if (e.key() == 7)
                e.setValue("A");
        }
        assertEquals(16, sumKeys);
        assertEquals(2, count);
        assertEquals("A", map.get(7));
        assertEquals("b", map.get(9));

        // 迭代器耗尽后再 next 抛 NoSuchElementException
        Iterator<MapOfLong.PrimitiveEntry<String>> it = map.entries().iterator();
        while (it.hasNext())
            it.next();
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    public void testValuesCollectionBackedByMap() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(1, "a");
        map.put(2, "b");
        assertEquals(2, map.values().size());
        assertTrue(map.values().contains("a"));
        map.values().remove("b");
        assertEquals(1, map.size());
        assertFalse(map.containsValue("b"));
    }

    @Test
    public void testPutAllBothPaths() {
        LongHashMap<String> source = new LongHashMap<>();
        source.put(1, "a");
        source.put(2, "b");

        // LongHashMap 走内部数组快速路径
        LongHashMap<String> target = new LongHashMap<>();
        target.putAll(source);
        assertEquals("a", target.get(1));
        assertEquals("b", target.get(2));

        // 普通 Map 走 entrySet 路径
        Map<Long, String> plain = new HashMap<>();
        plain.put(3L, "c");
        target.putAll(plain);
        assertEquals("c", target.get(3));
        assertEquals(3, target.size());
    }

    @Test
    public void testEqualsAndHashCode() {
        LongHashMap<String> a = new LongHashMap<>();
        a.put(1, "x");
        a.put(2, null);

        LongHashMap<String> b = new LongHashMap<>();
        b.put(2, null);
        b.put(1, "x");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
        assertNotEqualsLongHashMap(a, new LongHashMap<String>());

        LongHashMap<String> c = new LongHashMap<>();
        c.put(1, "y");
        assertNotEqualsLongHashMap(a, c);
    }

    private void assertNotEqualsLongHashMap(LongHashMap<String> a, LongHashMap<String> b) {
        if (a.equals(b))
            throw new AssertionError("maps expected to differ");
        // hash 码不保证不同，但 size 一定不同或 entry 不等
    }

    @Test
    public void testClearAndToString() {
        LongHashMap<String> map = new LongHashMap<>();
        map.put(1, "a");
        map.put(2, "b");
        assertTrue(map.toString().contains("1=a"));
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals("{}", map.toString());
        map.put(3, "c");
        assertEquals("c", map.get(3));
    }
}
