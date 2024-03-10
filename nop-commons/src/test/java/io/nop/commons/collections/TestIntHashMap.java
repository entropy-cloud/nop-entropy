/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIntHashMap {
    @Test
    public void testZero() {
        IntHashMap<String> map = new IntHashMap<>();
        map.put(0, "a");
        assertEquals(1, map.size());

        map.put(1, "b");
        assertEquals(2, map.size());

        assertEquals("b", map.remove(1));
        assertEquals(1, map.size());

        assertEquals("a", map.remove(0));
    }

    @Test
    public void testRandomEntries() {
        IntHashMap<Integer> map = new IntHashMap<>();

        for (int n = 100; n < 10000; n++) {

            for (int i = n - 1; i >= 0; i--) {
                map.put(i, 1);
            }

            Set<Integer> set = new HashSet<>(n * 2);
            map.randomForEachEntry((v, k) -> {
                set.add(k);
            });

            assertEquals(n, set.size());
        }
    }
}
