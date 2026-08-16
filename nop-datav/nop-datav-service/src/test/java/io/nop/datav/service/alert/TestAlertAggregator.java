package io.nop.datav.service.alert;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.service.NopDatavErrors;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AlertAggregator} 纯单元测试（D5-2 Phase 4）。
 *
 * <p>不依赖 IoC/数据库，直接构造 {@link PanelDataResult} 验证聚合契约。
 * 覆盖：none/first/sum/avg/min/max/count 路径、列不存在、非数值、无数据行。</p>
 */
public class TestAlertAggregator {

    private static final String RULE_ID = "test-rule";

    private static PanelDataResult data(List<String> columns, List<Map<String, Object>> rows) {
        return new PanelDataResult("panel-1", "chart", true, columns, rows);
    }

    private static Map<String, Object> row(String col, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(col, value);
        return m;
    }

    // ==================== aggregation 路径 ====================

    @Test
    public void testFirstAggregation() {
        PanelDataResult d = data(Arrays.asList("amount"),
                Arrays.asList(row("amount", new BigDecimal("100")), row("amount", new BigDecimal("200"))));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "amount", "first", RULE_ID);
        assertEquals(0, new BigDecimal("100").compareTo(r.value));
        assertEquals(2, r.rowCount);
    }

    @Test
    public void testNoneAggregationEqualsFirst() {
        PanelDataResult d = data(Collections.singletonList("amount"),
                Collections.singletonList(row("amount", 42L)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "amount", "none", RULE_ID);
        assertEquals(0, new BigDecimal("42").compareTo(r.value));
    }

    @Test
    public void testSumAggregation() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Arrays.asList(row("v", 100), row("v", 200), row("v", 50)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "sum", RULE_ID);
        assertEquals(0, new BigDecimal("350").compareTo(r.value));
    }

    @Test
    public void testAvgAggregation() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Arrays.asList(row("v", 100), row("v", 200), row("v", 50)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "avg", RULE_ID);
        // 350/3 = 116.6667 (scale=4, ROUND_HALF_UP)
        assertEquals(0, new BigDecimal("116.6667").compareTo(r.value));
    }

    @Test
    public void testMinAggregation() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Arrays.asList(row("v", 100), row("v", 200), row("v", 50)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "min", RULE_ID);
        assertEquals(0, new BigDecimal("50").compareTo(r.value));
    }

    @Test
    public void testMaxAggregation() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Arrays.asList(row("v", 100), row("v", 200), row("v", 50)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "max", RULE_ID);
        assertEquals(0, new BigDecimal("200").compareTo(r.value));
    }

    @Test
    public void testCountAggregation() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Arrays.asList(row("v", 100), row("v", 200), row("v", 50)));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "count", RULE_ID);
        assertEquals(0, new BigDecimal("3").compareTo(r.value));
        assertEquals(3, r.rowCount);
    }

    @Test
    public void testDefaultAggregationIsFirst() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Collections.singletonList(row("v", 99)));
        // aggregation 为 null → 默认 first
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", null, RULE_ID);
        assertEquals(0, new BigDecimal("99").compareTo(r.value));
    }

    @Test
    public void testStringValueConvertedToBigDecimal() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Collections.singletonList(row("v", "123.45")));
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "first", RULE_ID);
        assertEquals(0, new BigDecimal("123.45").compareTo(r.value));
    }

    @Test
    public void testCaseInsensitiveColumnLookup() {
        PanelDataResult d = data(Collections.singletonList("AMOUNT"),
                Collections.singletonList(row("AMOUNT", 100)));
        // valueField 大小写不一致但匹配
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "amount", "first", RULE_ID);
        assertEquals(0, new BigDecimal("100").compareTo(r.value));
    }

    // ==================== 显式失败路径 ====================

    @Test
    public void testColumnNotFoundFailsExplicitly() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Collections.singletonList(row("v", 100)));
        NopException ex = assertThrows(NopException.class,
                () -> AlertAggregator.aggregate(d, "nonexistent", "first", RULE_ID));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testNonNumericValueFailsExplicitly() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Collections.singletonList(row("v", "not-a-number")));
        NopException ex = assertThrows(NopException.class,
                () -> AlertAggregator.aggregate(d, "v", "first", RULE_ID));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testNullValueFailsExplicitly() {
        Map<String, Object> rowWithNull = new HashMap<>();
        rowWithNull.put("v", null);
        PanelDataResult d = data(Collections.singletonList("v"), Collections.singletonList(rowWithNull));
        NopException ex = assertThrows(NopException.class,
                () -> AlertAggregator.aggregate(d, "v", "first", RULE_ID));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testUnsupportedAggregationFailsExplicitly() {
        PanelDataResult d = data(Collections.singletonList("v"),
                Collections.singletonList(row("v", 100)));
        NopException ex = assertThrows(NopException.class,
                () -> AlertAggregator.aggregate(d, "v", "median", RULE_ID));
        assertEquals(NopDatavErrors.ERR_DATAV_ALERT_UNSUPPORTED_AGGREGATION.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getMessage());
    }

    // ==================== 无数据行（视为条件不满足） ====================

    @Test
    public void testNoRowsReturnsNullValue() {
        PanelDataResult d = data(Collections.singletonList("v"), Collections.emptyList());
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "sum", RULE_ID);
        assertNull(r.value, "no rows → value null (condition not met)");
        assertEquals(0, r.rowCount);
        assertFalse(r.hasValue());
    }

    @Test
    public void testNoRowsCountAggregationReturnsNull() {
        PanelDataResult d = data(Collections.singletonList("v"), Collections.emptyList());
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(d, "v", "count", RULE_ID);
        assertNull(r.value, "no rows → count value null (not 0, to be treated as condition not met)");
        assertEquals(0, r.rowCount);
    }

    @Test
    public void testNullDataReturnsNullValue() {
        AlertAggregator.ScalarResult r = AlertAggregator.aggregate(null, "v", "first", RULE_ID);
        assertNull(r.value);
        assertEquals(0, r.rowCount);
    }

    @Test
    public void testScalarResultHasValue() {
        AlertAggregator.ScalarResult withValue = new AlertAggregator.ScalarResult(BigDecimal.TEN, 5);
        assertTrue(withValue.hasValue());
        AlertAggregator.ScalarResult noValue = new AlertAggregator.ScalarResult(null, 0);
        assertFalse(noValue.hasValue());
    }
}
