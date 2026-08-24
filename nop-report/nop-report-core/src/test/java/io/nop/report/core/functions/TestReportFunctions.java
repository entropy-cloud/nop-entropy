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

import static io.nop.report.core.functions.ReportFunctions.COUNTIF;
import static io.nop.report.core.functions.ReportFunctions.SUMIF;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestReportFunctions {
    @Test
    public void testRank() {
        List<Integer> list = Arrays.asList(100, 200, 300, 100, 200, 300);
        int[] ranks = RankCompute.computeRank(list, v -> v, SafeNumberComparator.DESC, Objects::equals).getRanks();
        assertEquals("[5, 3, 1, 5, 3, 1]", ArrayHelper.toList(ranks).toString());
    }

    @Test
    public void testCountIfMultiCharOperator() {
        List<Integer> values = Arrays.asList(5, 9, 10, 11, 15);
        assertEquals(3, COUNTIF(values, ">=10").intValue());
        assertEquals(2, COUNTIF(values, "<=9").intValue());
        assertEquals(4, COUNTIF(values, "<>10").intValue());
        // 单字符操作符行为保持不变
        assertEquals(3, COUNTIF(values, ">9").intValue());
        assertEquals(2, COUNTIF(values, "<10").intValue());
    }

    @Test
    public void testSumIfMultiCharOperator() {
        List<Integer> values = Arrays.asList(5, 9, 10, 11, 15);
        assertEquals(36, SUMIF(values, ">=10", null).intValue());
        assertEquals(14, SUMIF(values, "<=9", null).intValue());
        // 单字符操作符行为保持不变
        assertEquals(36, SUMIF(values, ">9", null).intValue());
    }
}
