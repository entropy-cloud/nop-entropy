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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestListFunctions {

    @Test
    public void testPopRemovesLastAndPushReturnsSize() {
        List<String> list = new ArrayList<>(Arrays.asList("a", "b"));
        assertEquals("b", ListFunctions.pop(list));
        assertEquals(1, list.size());
        assertEquals(2, ListFunctions.push(list, "x"));
        assertEquals(Arrays.asList("a", "x"), list);
        // varargs push
        assertEquals(4, ListFunctions.push(list, "y", "z"));
        assertEquals(Arrays.asList("a", "x", "y", "z"), list);
    }

    @Test
    public void testReduceRightIsRightToLeft() {
        List<String> list = Arrays.asList("a", "b", "c");
        // 从右向左折叠：init + c + b + a
        assertEquals("init-c-b-a",
                ListFunctions.reduceRight(list, (acc, item) -> acc + "-" + item, "init"));
        // null / 空列表返回 null（不是初始值）
        assertNull(ListFunctions.reduceRight(null, (acc, item) -> acc, "init"));
        assertNull(ListFunctions.reduceRight(Collections.emptyList(), (acc, item) -> acc, "init"));
    }

    @Test
    public void testReverseMutatesAndReturnsSameList() {
        List<String> list = new ArrayList<>(Arrays.asList("1", "2", "3"));
        List<String> ret = ListFunctions.reverse(list);
        assertSame(list, ret);
        assertEquals(Arrays.asList("3", "2", "1"), list);
    }

    @Test
    public void testShiftRemovesFirstOrNullWhenEmpty() {
        List<String> list = new ArrayList<>(Arrays.asList("a", "b"));
        assertEquals("a", ListFunctions.shift(list));
        assertEquals(1, list.size());
        List<String> empty = new ArrayList<>();
        assertNull(ListFunctions.shift(empty));
        assertEquals(0, empty.size());
    }

    @Test
    public void testSliceDoesNotMutateSource() {
        List<String> list = Arrays.asList("a", "b", "c", "d");

        // null endIndex → 到末尾
        assertEquals(Arrays.asList("c", "d"), ListFunctions.slice(list, 2, null));
        // 负 startIndex 从末尾起算
        assertEquals(Arrays.asList("c", "d"), ListFunctions.slice(list, -2, null));
        // startIndex 越界 → 空列表
        assertTrue(ListFunctions.slice(list, 4, null).isEmpty());
        // 负 endIndex 从末尾起算
        assertEquals(Arrays.asList("b", "c"), ListFunctions.slice(list, 1, -1));
        // endIndex 越界 → 截断到 size
        assertEquals(Arrays.asList("c", "d"), ListFunctions.slice(list, 2, 99));
        // endIndex < startIndex → 空列表
        assertTrue(ListFunctions.slice(list, 2, 1).isEmpty());
        // 正常区间
        assertEquals(Arrays.asList("b", "c"), ListFunctions.slice(list, 1, 3));
        // slice 不改变原集合
        assertEquals(Arrays.asList("a", "b", "c", "d"), list);
    }

    @Test
    public void testSpliceRemovesAndInserts() {
        List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        // 删除两个元素，返回被删片段
        List<String> removed = ListFunctions.splice(list, 1, 2);
        assertEquals(Arrays.asList("b", "c"), removed);
        assertEquals(Arrays.asList("a", "d"), list);

        // number=0：不删除，仅返回空列表
        List<String> none = ListFunctions.splice(list, 0, 0);
        assertTrue(none.isEmpty());
        assertEquals(Arrays.asList("a", "d"), list);

        // 删除后插入：插入位从 index 开始
        List<String> removed2 = ListFunctions.splice(list, 1, 0, "x", "y");
        assertTrue(removed2.isEmpty());
        assertEquals(Arrays.asList("a", "x", "y", "d"), list);

        // index 超过 size：返回空移除列表，原列表用 null 填充到 index 再插入
        List<String> removed3 = ListFunctions.splice(list, 6, 0, "z");
        assertTrue(removed3.isEmpty());
        assertEquals(Arrays.asList("a", "x", "y", "d", null, null, "z"), list);
    }

    @Test
    public void testSpliceRemovalClampedToEnd() {
        List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        // 删除个数越过末尾：截断处理
        List<String> removed = ListFunctions.splice(list, 2, 10);
        assertEquals(java.util.Collections.singletonList("c"), removed);
        assertEquals(Arrays.asList("a", "b"), list);
    }

    @Test
    public void testUnshiftPutsItemToFront() {
        List<String> list = new ArrayList<>(Arrays.asList("b"));
        assertEquals(2, ListFunctions.unshift(list, "a"));
        assertEquals(Arrays.asList("a", "b"), list);
        // varargs：varargs 先插入，item 最终位于最前
        assertEquals(5, ListFunctions.unshift(list, "0", "x", "y"));
        assertEquals(Arrays.asList("0", "x", "y", "a", "b"), list);
    }
}
