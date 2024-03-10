/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.core.lang.json.delta.DeltaMergeHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMerge {
    /**
     * <p>
     * 例如 a=[a1,a2,a3,a4,a5]与b=[b1, a2, b3] 合并， 先得到 all = [a1,a2,a3,a4,a5, b1,a2,b3], 然后发现a2重复，需要以a2元素为基准移动a中的元素
     * 得到[a1,b1,a2,b3,a3,a4,a5]，b3在b中紧接着a2，所以在移动后的序列中也紧接着a2。
     *
     * <p>
     * 如果a与b=[a1, b1, a3, b3]合并，则先得到[a1,a2,a3,a4,a5, a1,b1,a3,b3], 发现a1,a3重复，移动后得到[a1,b1,a2,a3,b3,a4,a5]。
     *
     * <p>
     * 基准元素用于定位时可以理解为代表它以及它的后续元素（直到遇到另外一个基准元素为止）。
     *
     * <p>
     * 如果a与b=[a3,b1,a1]合并，则先得到[a1,a2,a3,a4,a5, a3,b1,a1], 现在b中a3和a1颠倒了顺序，先移动a1得到[a3,a4,a5, a3,b1,a1,a2]，再移动a3得到
     * [a3,b1,a4,a5,a1,a2]
     */
    @Test
    public void testMergeList() {
        List<String> a = Arrays.asList("a1", "a2", "a3", "a4", "a5");
        List<String> b = Arrays.asList("b1", "a2", "b3");

        assertEquals("[a1, b1, a2, b3, a3, a4, a5]", merge(a, b).toString());

        b = Arrays.asList("a1", "b1", "a3", "b3");
        assertEquals("[a1, b1, a2, a3, b3, a4, a5]", merge(a, b).toString());

        b = Arrays.asList("a3", "b1", "a1");
        assertEquals("[a3, b1, a4, a5, a1, a2]", merge(a, b).toString());

        b = Arrays.asList("a3", "b1", "a1", "a5");
        assertEquals("[a3, b1, a4, a1, a2, a5]", merge(a, b).toString());
    }

    static List<String> merge(List<String> a, List<String> b) {
        int[] bIndexes = new int[b.size()];
        for (int i = 0, n = b.size(); i < n; i++) {
            bIndexes[i] = a.indexOf(b.get(i));
        }
        List<DeltaMergeHelper.MatchData> merged = DeltaMergeHelper.mergeList(a.size(), bIndexes);
        return DeltaMergeHelper.buildResult(a, b, merged);
    }
}
