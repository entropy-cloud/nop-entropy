/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
