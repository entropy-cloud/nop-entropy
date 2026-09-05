/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.dataset;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ReportDataSet.avg/avgBy 分母缺陷回归测试：空值行不应计入分母。
 */
public class TestReportDataSetAvg {

    private Map<String, Object> score(Integer value) {
        Map<String, Object> map = new HashMap<>();
        map.put("score", value);
        return map;
    }

    @Test
    public void testAvgSkipsEmptyValuesInDenominator() {
        List<Object> items = Arrays.asList(score(90), score(60), score(null));
        ReportDataSet ds = new ReportDataSet("ds", items);
        // 分子跳过空值，分母也只应统计非空行：(90+60)/2 = 75，而不是 150/3 = 50
        assertEquals(75.0, ds.avg("score").doubleValue(), 1e-9);
    }

    @Test
    public void testAvgBySkipsEmptyValuesInDenominator() {
        List<Object> items = Arrays.asList(score(90), score(60), score(null));
        ReportDataSet ds = new ReportDataSet("ds", items);
        assertEquals(75.0, ds.avgBy(item -> ((Map<?, ?>) item).get("score")).doubleValue(), 1e-9);
    }

    @Test
    public void testAvgEmptyDataSetReturnsNull() {
        ReportDataSet ds = new ReportDataSet("ds", Collections.emptyList());
        // 空数据集不应返回NaN静默传播
        assertNull(ds.avg("score"));
    }
}
