/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.functions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * COUNTIF/SUMIF 条件解析缺陷回归测试。
 */
public class TestReportFunctionsCondition {

    @Test
    public void testCountIfGteOperator() {
        List<Integer> data = Arrays.asList(5, 10, 15);
        // ">=" 不能被截断为 ">"：10和15满足条件
        assertEquals(2, ReportFunctions.COUNTIF(data, ">=10").intValue());
    }

    @Test
    public void testCountIfLeOperator() {
        List<Integer> data = Arrays.asList(5, 10, 15);
        // "<=" 不能被截断为 "<"：全部三个值都满足条件
        assertEquals(3, ReportFunctions.COUNTIF(data, "<=15").intValue());
    }

    @Test
    public void testCountIfNotEqualsOperator() {
        List<Integer> data = Arrays.asList(2, 10, 30);
        // "<>" 排除10，其余2个计数
        assertEquals(2, ReportFunctions.COUNTIF(data, "<>10").intValue());
    }

    @Test
    public void testSumIfWithoutSumRangeSumsMatchedCells() {
        List<Integer> data = Arrays.asList(1, 2, 3, 4);
        // 不传sumRange时，Excel语义为对满足条件的单元格自身求和：3+4=7
        assertEquals(7, ReportFunctions.SUMIF(data, ">2", null).intValue());
    }

    @Test
    public void testSumIfWithSumRange() {
        List<String> keys = Arrays.asList("a", "b", "a", "b");
        List<Integer> vals = Arrays.asList(1, 2, 3, 4);
        assertEquals(4, ReportFunctions.SUMIF(keys, "a", vals).intValue());
    }
}
