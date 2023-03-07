/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core;

import org.junit.jupiter.api.Test;

public class TestMath {
    @Test
    public void testMath() {
        System.out.println(Double.NaN + 1);
        System.out.println(Double.NaN / 1);
        System.out.println(Double.NaN * 100);
        System.out.println(Double.NaN / 0.0);
        System.out.println(1 / 0.0000000000000000000000001);
        System.out.println(Math.pow(Double.NaN, 3));
    }

    @Test
    public void testPower2() {
        System.out.println((long) Math.pow(2, 10000));
    }
}