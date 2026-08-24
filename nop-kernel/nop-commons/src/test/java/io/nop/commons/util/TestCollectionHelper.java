/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.mutable.MutableInt;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author canonical_entropy@163.com
 */
public class TestCollectionHelper {
    Set<Serializable> newSet(int count) {
        Set<Serializable> set = new LinkedHashSet<Serializable>(count);
        for (int i = 0; i < count; i++) {
            set.add(String.valueOf(i));
        }
        return set;
    }

    @Test
    public void testBitSet() {
        for (int i = 1; i < 500; i++) {
            IBitSet bs = CollectionHelper.newFixedBitSet(i);
            for (int j = 0; j < i; j++) {
                bs.set(j);
            }
            MutableInt n = new MutableInt();
            bs.forEach(k -> {
                n.incrementAndGet();
            });
            assertEquals(i, n.get());
        }
    }

    @Test
    public void testChunk() {
        List<? extends Collection<Serializable>> ret = CollectionHelper.splitChunk(newSet(1001), 1000);
        assertEquals(2, ret.size());
        assertEquals(1000, ret.get(0).size());
        assertEquals(1, ret.get(1).size());

        ret = CollectionHelper.splitChunk(newSet(2050), 1000);
        assertEquals(3, ret.size());
        assertEquals(1000, ret.get(0).size());
        assertEquals(1000, ret.get(1).size());
        assertEquals(50, ret.get(2).size());
        assertEquals("0", ret.get(0).iterator().next());
        assertEquals("1000", ret.get(1).iterator().next());
        assertEquals("2000", ret.get(2).iterator().next());

        ret = CollectionHelper.splitChunk(newSet(76), 7);
        assertEquals(11, ret.size());
        assertEquals(7, ret.get(8).size());
        assertEquals(6, ret.get(10).size());
        System.out.println(ret);
    }

    @Test
    public void testSplitChunkInvalidSize() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> CollectionHelper.splitChunk(list, 0));
        assertThrows(IllegalArgumentException.class, () -> CollectionHelper.splitChunk(list, -1));
    }

    @Test
    public void testSumDouble() {
        // double累加器不应截断小数部分
        assertEquals(1.0, CollectionHelper.sumDouble(Arrays.asList(0.5, 0.5), Double::doubleValue), 0.0);
        assertEquals(4.0, CollectionHelper.sumDouble(Arrays.asList(1.5, 2.5), Double::doubleValue), 0.0);
        assertEquals(0.2, CollectionHelper.sumDouble(Arrays.asList(0.1, 0.1), Double::doubleValue), 1e-9);
        assertEquals(0.0, CollectionHelper.sumDouble(Arrays.asList(), Double::doubleValue), 0.0);
    }

    @Test
    public void testGetByIndexNegative() {
        // 负下标应统一返回null，而不是在List分支抛IndexOutOfBoundsException
        assertNull(CollectionHelper.getByIndex(Arrays.asList(1, 2), -1));
        assertNull(CollectionHelper.getByIndex(new LinkedHashSet<>(Arrays.asList(1, 2)), -1));
        assertEquals(2, CollectionHelper.getByIndex(Arrays.asList(1, 2), 1));
        assertNull(CollectionHelper.getByIndex(Arrays.asList(1, 2), 2));
    }

    @Test
    public void testDisjoint() {
        List<String> ret = CollectionHelper.disjoint(Arrays.asList("a", "b"), Arrays.asList("b", "c"),
                new ArrayList<>());
        assertEquals(Arrays.asList("a", "c"), ret);

        // 重复元素按集合语义去重：{a,b}与{c}的对称差为{a,b,c}，a不因重复出现两次
        List<String> dup = CollectionHelper.disjoint(Arrays.asList("a", "a", "b"), Arrays.asList("c"),
                new ArrayList<>());
        assertEquals(Arrays.asList("a", "b", "c"), dup);

        // 交集为空/全交
        List<String> none = CollectionHelper.disjoint(Arrays.asList("a"), Arrays.asList("a"),
                new ArrayList<>());
        assertEquals(Arrays.asList(), none);
    }
}
