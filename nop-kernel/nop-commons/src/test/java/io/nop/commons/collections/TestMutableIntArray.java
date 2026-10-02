/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMutableIntArray {

    @Test
    public void testFactoriesAndBasicAccess() {
        MutableIntArray array = MutableIntArray.of(3, 1, 2);
        assertEquals(3, array.size());
        assertEquals(3, array.first());
        assertEquals(2, array.last());

        assertEquals(1, array.get(1));
        array.set(1, 9);
        assertEquals(9, array.get(1));

        assertTrue(MutableIntArray.empty().isEmpty());
        assertTrue(MutableIntArray.of().isEmpty());
        assertTrue(MutableIntArray.of(null).isEmpty());

        // 越界读：负下标越界、size 下标越界（容量仍有空间，防御的是逻辑 size 而非数组长度）
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(3, 0));
    }

    @Test
    public void testAddGrowsBeyondInitialCapacity() {
        MutableIntArray array = new MutableIntArray(2);
        for (int i = 0; i < 100; i++) {
            array.add(i);
        }
        assertEquals(100, array.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i, array.get(i));
        }
        assertEquals(99, array.last());
    }

    @Test
    public void testInsertOrderedPreservesOrder() {
        MutableIntArray array = MutableIntArray.of(1, 3);
        array.insert(1, 2);
        assertArrayEquals(new int[]{1, 2, 3}, array.toArray());

        // 无序模式下 insert 不搬移后续元素，而是把被覆盖位置的元素挪到尾部
        MutableIntArray unordered = new MutableIntArray(false, 5);
        unordered.add(1);
        unordered.add(2);
        unordered.add(3);
        unordered.insert(0, 9);
        assertEquals(4, unordered.size());
        assertEquals(9, unordered.get(0));
        // 原先 index 0 处的 1 被挪到尾部，中段 [2,3] 保持原位
        assertEquals(1, unordered.last());
        assertEquals(2, unordered.get(1));
        assertEquals(3, unordered.get(2));
    }

    @Test
    public void testRemoveIndexOrderedVsUnordered() {
        MutableIntArray ordered = MutableIntArray.of(1, 2, 3, 4);
        assertEquals(2, ordered.removeIndex(1));
        assertArrayEquals(new int[]{1, 3, 4}, ordered.toArray());

        // 显式构造无序数组：of(...) 产出的数组是 ordered 的
        MutableIntArray unordered = new MutableIntArray(false, 4);
        unordered.add(1);
        unordered.add(2);
        unordered.add(3);
        unordered.add(4);
        // 无序模式：删除位由最后一个元素填补
        unordered.removeIndex(0);
        assertEquals(3, unordered.size());
        assertEquals(4, unordered.get(0));
        assertEquals(2, unordered.get(1));
        assertEquals(3, unordered.get(2));

        assertThrows(IndexOutOfBoundsException.class, () -> ordered.removeIndex(3));
    }

    @Test
    public void testSearchAndRemoveValue() {
        MutableIntArray array = MutableIntArray.of(1, 2, 1, 3);
        assertEquals(0, array.indexOf(1));
        assertEquals(2, array.lastIndexOf(1));
        assertTrue(array.contains(3));
        assertFalse(array.contains(99));
        assertEquals(-1, array.indexOf(99));
        assertEquals(-1, array.lastIndexOf(99));

        assertTrue(array.removeValue(1));
        assertArrayEquals(new int[]{2, 1, 3}, array.toArray());
        // 只移除第一个匹配
        assertTrue(array.removeValue(1));
        assertArrayEquals(new int[]{2, 3}, array.toArray());
        assertFalse(array.removeValue(42));
    }

    @Test
    public void testSwapBounds() {
        MutableIntArray array = MutableIntArray.of(1, 2, 3);
        array.swap(0, 2);
        assertArrayEquals(new int[]{3, 2, 1}, array.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> array.swap(0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> array.swap(3, 0));
    }

    @Test
    public void testAddAllAndBounds() {
        MutableIntArray array = MutableIntArray.of(1);
        array.addAll(new int[]{2, 3});
        assertArrayEquals(new int[]{1, 2, 3}, array.toArray());

        array.addAll(new int[]{4, 5, 6}, 1, 2);
        assertArrayEquals(new int[]{1, 2, 3, 5, 6}, array.toArray());

        IntArray other = MutableIntArray.of(7, 8);
        array.addAll(other);
        assertArrayEquals(new int[]{1, 2, 3, 5, 6, 7, 8}, array.toArray());

        // offset + length 越界：IntArray 变体有前置校验，int[] 变体缺校验、由 arraycopy 抛出
        // （两变体校验行为不一致，已记录为观察项）
        assertThrows(IllegalArgumentException.class,
                () -> array.addAll(other, 1, 2));
        assertThrows(Exception.class,
                () -> array.addAll(new int[]{1, 2}, 1, 2));
    }

    @Test
    public void testStackSemantics() {
        MutableIntArray array = new MutableIntArray();
        assertEquals(-1, array.tryPeek());
        assertEquals(-1, array.tryPeek(1));

        array.push(1);
        array.push(2);
        array.push(3);
        assertEquals(3, array.peek());
        assertEquals(3, array.tryPeek());
        assertEquals(2, array.tryPeek(1));
        assertEquals(-1, array.tryPeek(3));

        array.replaceTop(30);
        assertEquals(30, array.pop());
        array.setLast(20);
        assertEquals(20, array.pop());
        assertEquals(1, array.pop());
        assertTrue(array.isEmpty());

        // pop 空栈：size 递减后读取 items[-1] 抛出数组越界
        assertThrows(Exception.class, () -> new MutableIntArray().pop());
    }

    @Test
    public void testMergeDedups() {
        MutableIntArray array = MutableIntArray.of(1, 2, 3);
        MutableIntArray merged = (MutableIntArray) array.merge(MutableIntArray.of(3, 4, 5));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, merged.toArray());

        // merge 单值：已存在不加，不存在追加
        assertSameArrayAfterMerge(array, 1);
        array.merge(6);
        assertEquals(6, array.last());

        // 与自身 merge 返回自身，不做任何变更
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, array.merge(array).toMutable().toArray());
    }

    private void assertSameArrayAfterMerge(MutableIntArray array, int existing) {
        int sizeBefore = array.size();
        array.merge(existing);
        assertEquals(sizeBefore, array.size());
    }

    @Test
    public void testSortReturnsSortedCopyOriginalUntouched() {
        MutableIntArray array = MutableIntArray.of(3, 1, 2);
        ImmutableIntArray sorted = (ImmutableIntArray) array.sort();
        assertArrayEquals(new int[]{1, 2, 3}, sorted.toMutable().toArray());
        // sort 不改变原数组
        assertArrayEquals(new int[]{3, 1, 2}, array.toArray());

        array.reverse();
        assertArrayEquals(new int[]{2, 1, 3}, array.toArray());
    }

    @Test
    public void testTruncateClearShrinkEnsureCapacity() {
        MutableIntArray array = MutableIntArray.of(1, 2, 3, 4, 5);
        array.truncate(3);
        assertEquals(3, array.size());
        // truncate 到更大 size 是无操作
        array.truncate(10);
        assertEquals(3, array.size());

        array.ensureCapacity(100);
        array.add(9);
        assertEquals(9, array.last());

        array.clear();
        assertTrue(array.isEmpty());
        assertEquals(0, array.size());

        array.shrink();
        array.add(1);
        assertEquals(1, array.first());
    }

    @Test
    public void testToStringAndEquality() {
        assertEquals("[]", new MutableIntArray().toString());
        assertEquals("", new MutableIntArray().toString(","));
        MutableIntArray array = MutableIntArray.of(1, 2, 3);
        assertEquals("[1, 2, 3]", array.toString());
        assertEquals("1,2,3", array.toString(","));

        assertTrue(array.isEqual(MutableIntArray.of(1, 2, 3)));
        assertFalse(array.isEqual(MutableIntArray.of(1, 2)));
        assertFalse(array.isEqual(MutableIntArray.of(1, 2, 4)));
        assertTrue(array.isEqual(array));

        ImmutableIntArray immutable = array.toImmutable();
        assertTrue(immutable.isEqual(array));
    }

    @Test
    public void testCopyConstructorPreservesContentAndOrderedFlag() {
        MutableIntArray source = MutableIntArray.of(1, 2, 3);
        MutableIntArray copy = new MutableIntArray(source);
        assertTrue(copy.isEqual(source));
        assertTrue(copy.isOrdered());

        MutableIntArray unordered = new MutableIntArray(false, 2);
        unordered.add(1);
        MutableIntArray copy2 = new MutableIntArray(unordered);
        assertFalse(copy2.isOrdered());
        assertEquals(1, copy2.first());
    }
}
