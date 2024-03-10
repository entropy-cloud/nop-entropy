/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.functions;

import io.nop.commons.collections.SafeNumberComparator;
import io.nop.commons.util.ArrayHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestReportFunctions {
    @Test
    public void testRank() {
        List<Integer> list = Arrays.asList(100, 200, 300, 100, 200, 300);
        int[] ranks = RankCompute.computeRank(list, v -> v, SafeNumberComparator.DESC, Objects::equals).getRanks();
        assertEquals("[5, 3, 1, 5, 3, 1]", ArrayHelper.toList(ranks).toString());
    }
}
