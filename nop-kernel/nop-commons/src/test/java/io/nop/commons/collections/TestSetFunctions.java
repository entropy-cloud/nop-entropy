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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSetFunctions {

    @Test
    public void testIncludesNullSafe() {
        assertFalse(SetFunctions.includes(null, 1));
        assertFalse(SetFunctions.includes(Arrays.asList(1, 2), 3));
        assertTrue(SetFunctions.includes(Arrays.asList(1, 2), 2));
    }

    @Test
    public void testRemoveNullSafe() {
        assertFalse(SetFunctions.remove(null, 1));
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));
        assertTrue(SetFunctions.remove(list, 2));
        assertEquals(Arrays.asList(1, 3), list);
        assertFalse(SetFunctions.remove(list, 9));
        assertFalse(SetFunctions.remove(list, null));
    }

    @Test
    public void testSomeAndEvery() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertTrue(SetFunctions.some(list, i -> i > 2));
        assertFalse(SetFunctions.some(list, i -> i > 5));
        assertFalse(SetFunctions.some(new ArrayList<>(), i -> true));

        assertTrue(SetFunctions.every(list, i -> i > 0));
        // 存在任一不匹配即 false
        assertFalse(SetFunctions.every(list, i -> i > 1));
        // 空集上 every 恒真
        assertTrue(SetFunctions.every(new ArrayList<>(), i -> false));
    }

    @Test
    public void testFilterAndMap() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4);
        assertEquals(Arrays.asList(2, 4), SetFunctions.filter(list, i -> i % 2 == 0));
        assertEquals(Arrays.asList("1", "2", "3", "4"), SetFunctions.map(list, String::valueOf));
        assertEquals(0, SetFunctions.map(new ArrayList<Integer>(), String::valueOf).size());
    }

    @Test
    public void testFlatMapExpandsCollectionsAndStreams() {
        List<Integer> list = Arrays.asList(1, 2);
        // fn 返回集合：展开压平
        List<Integer> expanded = SetFunctions.flatMap(list, i -> Arrays.asList(i, i * 10));
        assertEquals(Arrays.asList(1, 10, 2, 20), expanded);

        // fn 返回 Stream：同样展开
        List<Integer> streamed = SetFunctions.flatMap(list, i -> java.util.stream.Stream.of(i, i + 1));
        assertEquals(Arrays.asList(1, 2, 2, 3), streamed);
    }

    @Test
    public void testConcatCollectionAndScalar() {
        List<String> base = Arrays.asList("a");
        assertEquals(Arrays.asList("a", "b"), SetFunctions.concat(base, Arrays.asList("b")));
        // concat 不改变原集合
        assertEquals(1, base.size());

        List<String> multi = SetFunctions.concat(base, "b", Arrays.asList("c"), "d");
        assertEquals(Arrays.asList("a", "b", "c", "d"), multi);
    }

    /**
     * 回归覆盖 wi2#2（plan 2306 项 31）：concat 标量分支按 JS Array.concat 语义
     * 保留接收集合内容，结果 = list + source。
     */
    @Test
    public void testConcatScalarBranchKeepsReceiverList() {
        List<String> base = Arrays.asList("a");
        List<String> result = SetFunctions.concat(base, "b");
        assertEquals(Arrays.asList("a", "b"), result);
    }

    /**
     * 回归覆盖 wi2#3（plan 2306 项 31）：flatMap 标量分支必须加入映射结果而非原元素。
     */
    @Test
    public void testFlatMapScalarBranchUsesMappedValue() {
        List<String> list = Arrays.asList("a", "bb");
        // 标量映射：取长度
        List<Integer> result = SetFunctions.flatMap(list, String::length);
        assertEquals(Arrays.asList(1, 2), result);
    }

    @Test
    public void testReduce() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertEquals(6, SetFunctions.reduce(list, (a, b) -> a + b, 0));
        assertEquals(10, SetFunctions.reduce(list, (a, b) -> a + b, 4));
        // null / 空集合返回初始值
        assertEquals("init", SetFunctions.reduce(null, (a, b) -> a + b, "init"));
        assertEquals("init", SetFunctions.reduce(new ArrayList<Integer>(), (a, b) -> a + b, "init"));
    }

    @Test
    public void testJoinAndToString() {
        assertEquals("1,2,3", SetFunctions.join(Arrays.asList(1, 2, 3), ","));
        assertEquals("a-b", SetFunctions.join(Arrays.asList("a", "b"), "-"));

        assertEquals("1,2,3", SetFunctions.toString(Arrays.asList(1, 2, 3)));
        assertEquals("", SetFunctions.toString(new ArrayList<Integer>()));
    }

    @Test
    public void testHasNextBoundary() {
        List<Integer> list = Arrays.asList(1, 2);
        // index <= size-1 视为还有下一个
        assertTrue(SetFunctions.hasNext(list, 0));
        assertTrue(SetFunctions.hasNext(list, 1));
        assertFalse(SetFunctions.hasNext(list, 2));
    }

    @Test
    public void testForEach2AndMap2CarryIndex() {
        List<String> list = Arrays.asList("a", "b");
        List<String> collected = new ArrayList<>();
        SetFunctions.forEach2(list, (item, idx) -> collected.add(idx + ":" + item));
        assertEquals(Arrays.asList("0:a", "1:b"), collected);

        assertEquals(Arrays.asList("0a", "1b"), SetFunctions.map2(list, (item, idx) -> idx + item));
    }

    @Test
    public void testFindVariants() {
        List<Integer> list = Arrays.asList(1, 2, 3, 2);

        // find 返回第一个匹配
        assertEquals(2, SetFunctions.find(list, i -> i > 1));
        assertNull(SetFunctions.find(list, i -> i > 9));
        assertNull(SetFunctions.find(null, i -> true));

        // findLast 从后向前
        assertEquals(2, SetFunctions.findLast(list, i -> i < 3));
        assertNull(SetFunctions.findLast(list, i -> i < 0));
        assertNull(SetFunctions.findLast(null, i -> true));

        // findIndex / findLastIndex 返回对应位置
        assertEquals(1, SetFunctions.findIndex(list, i -> i > 1));
        assertEquals(-1, SetFunctions.findIndex(list, i -> i > 9));
        assertEquals(-1, SetFunctions.findIndex(null, i -> true));

        // 值 2 出现在下标 1 和 3：findLastIndex 应返回更靠后的 3
        assertEquals(3, SetFunctions.findLastIndex(list, i -> i == 2));
        assertEquals(-1, SetFunctions.findLastIndex(list, i -> i > 9));
        assertEquals(-1, SetFunctions.findLastIndex(null, i -> true));
    }

    @Test
    public void testFilterPreservesDuplicatesAndOrder() {
        Set<String> set = Set.of("a", "b");
        assertTrue(SetFunctions.every(set, s -> s.length() == 1));
        assertTrue(SetFunctions.some(set, s -> s.equals("b")));
        List<String> fromSet = SetFunctions.filter(set, s -> !s.equals("a"));
        assertEquals(java.util.Collections.singletonList("b"), fromSet);
    }
}
