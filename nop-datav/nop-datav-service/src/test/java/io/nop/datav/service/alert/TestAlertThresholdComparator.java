package io.nop.datav.service.alert;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.service.NopDatavErrors;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AlertThresholdComparator} 纯单元测试（D5-2 Phase 4）。
 *
 * <p>不依赖 IoC/数据库，直接构造 BigDecimal 验证 operator 比较契约。
 * 覆盖：gt/gte/lt/lte/eq/neq/between 各路径 + thresholdValue2 缺失 + null 参数 + 未知 operator。</p>
 */
public class TestAlertThresholdComparator {

    private static final String RULE_ID = "test-rule";

    // ==================== operator 各路径 ====================

    @Test
    public void testGt() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("101"), "gt",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("100"), "gt",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("99"), "gt",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testGte() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("101"), "gte",
                new BigDecimal("100"), null, RULE_ID));
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("100"), "gte",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("99"), "gte",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testLt() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("99"), "lt",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("100"), "lt",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testLte() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("99"), "lte",
                new BigDecimal("100"), null, RULE_ID));
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("100"), "lte",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("101"), "lte",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testEq() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("100"), "eq",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("101"), "eq",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testEqUsesCompareNotEquals() {
        // 100.00 compareTo 100 == 0（scale 不影响 eq 判定）
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("100.00"), "eq",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testNeq() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("101"), "neq",
                new BigDecimal("100"), null, RULE_ID));
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("100"), "neq",
                new BigDecimal("100"), null, RULE_ID));
    }

    @Test
    public void testBetweenInclusiveBounds() {
        BigDecimal t1 = new BigDecimal("100");
        BigDecimal t2 = new BigDecimal("500");
        // 下界
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("100"), "between", t1, t2, RULE_ID));
        // 上界
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("500"), "between", t1, t2, RULE_ID));
        // 中间
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("300"), "between", t1, t2, RULE_ID));
        // 低于下界
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("99"), "between", t1, t2, RULE_ID));
        // 高于上界
        assertFalse(AlertThresholdComparator.compare(new BigDecimal("501"), "between", t1, t2, RULE_ID));
    }

    @Test
    public void testOperatorCaseInsensitive() {
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("101"), "GT",
                new BigDecimal("100"), null, RULE_ID));
        assertTrue(AlertThresholdComparator.compare(new BigDecimal("300"), "Between",
                new BigDecimal("100"), new BigDecimal("500"), RULE_ID));
    }

    // ==================== 显式失败路径 ====================

    @Test
    public void testBetweenWithoutThreshold2FailsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> AlertThresholdComparator.compare(new BigDecimal("300"), "between",
                        new BigDecimal("100"), null, RULE_ID));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testUnsupportedOperatorFailsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> AlertThresholdComparator.compare(new BigDecimal("100"), "contains",
                        new BigDecimal("1"), null, RULE_ID));
        assertEquals(NopDatavErrors.ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testNullCurrentFailsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> AlertThresholdComparator.compare(null, "gt",
                        new BigDecimal("100"), null, RULE_ID));
        assertEquals(NopDatavErrors.ERR_DATAV_ALERT_VALUE_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    public void testNullThresholdFailsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> AlertThresholdComparator.compare(new BigDecimal("100"), "gt",
                        null, null, RULE_ID));
        assertEquals(NopDatavErrors.ERR_DATAV_ALERT_VALUE_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getMessage());
    }
}
