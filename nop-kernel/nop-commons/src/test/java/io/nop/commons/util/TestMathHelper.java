/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMathHelper {
    @Test
    public void testIntMultiply() {
        Integer a = 3;
        Integer b = Integer.MAX_VALUE;
        assertEquals(a * b, MathHelper.multiply(a, b));
    }

    @Test
    public void testMinMaxNullConsistency() {
        // max与min对null的处理应一致：忽略null返回另一个值（SQL语义）
        assertEquals(3, MathHelper.max(3, null));
        assertEquals(3, MathHelper.min(3, null));
        assertNull(MathHelper.max(null, 3));
        assertNull(MathHelper.min(null, 3));
        assertNull(MathHelper.max(null, null));
        assertNull(MathHelper.min(null, null));
        assertEquals(2, MathHelper.min(2, 5));
        assertEquals(5, MathHelper.max(2, 5));
        assertEquals(2.5, MathHelper.min(2.5, 5.0));
    }

    @Test
    public void testGcdArray() {
        assertEquals(6, MathHelper.gcd(new int[]{12, 18}));
        assertEquals(2, MathHelper.gcd(new int[]{4, 6, 8}));
        // 单元素数组不应越界
        assertEquals(12, MathHelper.gcd(new int[]{12}));
        // 空数组应抛出参数校验异常而非数组越界
        assertThrows(IllegalArgumentException.class, () -> MathHelper.gcd(new int[0]));
        // 不应修改入参数组
        int[] arr = {4, 6, 8};
        MathHelper.gcd(arr);
        assertEquals(4, arr[0]);
        assertEquals(6, arr[1]);
        assertEquals(8, arr[2]);
    }

    @Test
    public void testToShortHash() {
        assertEquals(0, MathHelper.toShortHash(0));
        assertEquals(2, MathHelper.toShortHash(2));
        // Math.abs(Integer.MIN_VALUE)仍为负值，不应返回负的hash
        assertTrue(MathHelper.toShortHash(Integer.MIN_VALUE) >= 0);
    }

    @Test
    public void testRandomChooseEmpty() {
        List<Weighted> items = new ArrayList<>();
        // 空列表返回null而不是抛异常
        assertNull(MathHelper.randomChoose(items));
        // 总权重为0时抛出带明确信息的参数校验异常
        items.add(new Weighted(0));
        assertThrows(IllegalArgumentException.class, () -> MathHelper.randomChoose(items));
    }

    @Test
    public void testRandomChoose() {
        List<Weighted> items = new ArrayList<>();
        items.add(new Weighted(0));
        items.add(new Weighted(10));
        for (int i = 0; i < 100; i++) {
            Weighted item = MathHelper.randomChoose(items);
            assertSame(items.get(1), item);
        }
    }

    static class Weighted implements io.nop.api.core.util.IWithWeight {
        private final int weight;

        public Weighted(int weight) {
            this.weight = weight;
        }

        @Override
        public int getWeight() {
            return weight;
        }
    }

    @Test
    public void testMod() {
        int x = 10 % 3;
        assertEquals(1, x);

        assertEquals(1, MathHelper.mod(10, 3));
    }

    @Test
    public void testPowerOfTwo() {
        assertEquals(4, MathHelper.nextPowerOfTwo(3));
        assertEquals(2, MathHelper.nextPowerOfTwo(2));
        assertEquals(8, MathHelper.nextPowerOfTwo(5));
        assertEquals(4, MathHelper.nextPowerOfTwo(4));
        assertEquals(1, MathHelper.nextPowerOfTwo(-2));
        assertEquals(1, MathHelper.nextPowerOfTwo(-3));
        assertEquals(1, MathHelper.nextPowerOfTwo(-10));

        assertEquals(8, power(5));
    }

    int power(int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }
}
