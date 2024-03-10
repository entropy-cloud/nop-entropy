/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMathHelper {
    @Test
    public void testIntMultiply() {
        Integer a = 3;
        Integer b = Integer.MAX_VALUE;
        assertEquals(a * b, MathHelper.multiply(a, b));
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
