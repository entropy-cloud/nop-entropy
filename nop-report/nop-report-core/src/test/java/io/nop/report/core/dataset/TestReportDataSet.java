/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.dataset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestReportDataSet {

    static ReportDataSet dataSet(Object... values) {
        List<Object> items = new ArrayList<>(values.length);
        for (Object value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", value);
            items.add(row);
        }
        return new ReportDataSet("test", items);
    }

    static Object fieldValue(Object row) {
        return ((Map<?, ?>) row).get("value");
    }

    @Test
    public void testMinSkipsNullValues() {
        ReportDataSet ds = dataSet(5, null, 1);
        assertEquals(1, ((Number) ds.min("value")).intValue());
    }

    @Test
    public void testMinBySkipsNullValues() {
        ReportDataSet ds = dataSet(5, null, 1);
        assertEquals(1, ((Number) ds.minBy(TestReportDataSet::fieldValue)).intValue());
    }

    @Test
    public void testMinLeadingNullIsIgnored() {
        ReportDataSet ds = dataSet(null, 5, 1);
        assertEquals(1, ((Number) ds.min("value")).intValue());
    }

    @Test
    public void testMinAllNullReturnsNull() {
        ReportDataSet ds = dataSet(null, null);
        assertNull(ds.min("value"));
    }

    @Test
    public void testMaxSkipsNullValues() {
        ReportDataSet ds = dataSet(5, null, 1);
        assertEquals(5, ((Number) ds.max("value")).intValue());
    }
}
