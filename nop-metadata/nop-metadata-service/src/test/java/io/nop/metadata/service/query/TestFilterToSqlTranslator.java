/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TreeBean filter→SQL WHERE 翻译器（架构基线 §4.4 D1，复用 §2.7.1 D3 注入防护）：
 * 标准叶子条件 / 组合条件 / 参数绑定顺序 / 标识符白名单（注入防护） / 不支持的 op 显式失败。
 */
public class TestFilterToSqlTranslator {

    private final FilterToSqlTranslator translator = new FilterToSqlTranslator();

    @Test
    public void testNullFilterProducesNoWhere() {
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(null);
        assertNull(tf.getSql(), "null filter → no WHERE fragment");
        assertTrue(tf.getParams().isEmpty());
    }

    @Test
    public void testEqComparison() {
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(FilterBeans.eq("amount", 10));
        assertEquals("amount = ?", tf.getSql());
        assertEquals(Collections.singletonList(10), tf.getParams());
    }

    @Test
    public void testAllComparisonOps() {
        // gt/ge/lt/le/ne
        assertEquals("x > ?", translator.translate(FilterBeans.gt("x", 1)).getSql());
        assertEquals("x >= ?", translator.translate(FilterBeans.ge("x", 1)).getSql());
        assertEquals("x < ?", translator.translate(FilterBeans.lt("x", 1)).getSql());
        assertEquals("x <= ?", translator.translate(FilterBeans.le("x", 1)).getSql());
        assertEquals("x <> ?", translator.translate(FilterBeans.ne("x", 1)).getSql());
    }

    @Test
    public void testLike() {
        TreeBean like = new TreeBean("like").attr("name", "name").attr("value", "a%");
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(like);
        assertEquals("name LIKE ?", tf.getSql());
        assertEquals(Collections.singletonList("a%"), tf.getParams());
    }

    @Test
    public void testIn() {
        TreeBean in = new TreeBean("in").attr("name", "id").attr("value", Arrays.asList(1, 2, 3));
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(in);
        assertEquals("id IN (?,?,?)", tf.getSql());
        assertEquals(Arrays.asList(1, 2, 3), tf.getParams());
    }

    @Test
    public void testInEmptySetAlwaysFalse() {
        TreeBean in = new TreeBean("in").attr("name", "id").attr("value", Collections.emptyList());
        assertEquals("1=0", translator.translate(in).getSql());
    }

    @Test
    public void testBetweenMinMax() {
        TreeBean between = new TreeBean("between").attr("name", "amount").attr("min", 0).attr("max", 100);
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(between);
        assertEquals("amount BETWEEN ? AND ?", tf.getSql());
        assertEquals(Arrays.asList(0, 100), tf.getParams());
    }

    @Test
    public void testBetweenOnlyMin() {
        TreeBean between = new TreeBean("between").attr("name", "amount").attr("min", 5);
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(between);
        assertEquals("amount >= ?", tf.getSql());
        assertEquals(Collections.singletonList(5), tf.getParams());
    }

    @Test
    public void testNullChecks() {
        TreeBean isNull = new TreeBean("isNull").attr("name", "amount");
        assertEquals("amount IS NULL", translator.translate(isNull).getSql());
        TreeBean notNull = new TreeBean("notNull").attr("name", "amount");
        assertEquals("amount IS NOT NULL", translator.translate(notNull).getSql());
    }

    @Test
    public void testAndOrCombination() {
        TreeBean and = FilterBeans.and(FilterBeans.eq("a", 1), FilterBeans.gt("b", 2));
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(and);
        assertEquals("(a = ? AND b > ?)", tf.getSql());
        assertEquals(Arrays.asList(1, 2), tf.getParams());
    }

    @Test
    public void testNot() {
        TreeBean not = new TreeBean("not");
        not.setChildren(Collections.singletonList(FilterBeans.eq("a", 1)));
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(not);
        assertEquals("NOT (a = ?)", tf.getSql());
        assertEquals(Collections.singletonList(1), tf.getParams());
    }

    /** 嵌套 and+or，参数绑定顺序与 ? 出现顺序一致（深度优先）。 */
    @Test
    public void testNestedParamsOrder() {
        TreeBean root = FilterBeans.and(
                FilterBeans.eq("a", 1),
                FilterBeans.or(FilterBeans.gt("b", 2), FilterBeans.le("c", 3)));
        FilterToSqlTranslator.TranslatedFilter tf = translator.translate(root);
        assertEquals("(a = ? AND (b > ? OR c <= ?))", tf.getSql());
        assertEquals(Arrays.asList(1, 2, 3), tf.getParams());
    }

    // ===== 注入防护：标识符白名单（§2.7.1 D3）=====

    /** 字段名含非法字符（如 SQL 注入尝试）→ 显式失败。 */
    @Test
    public void testInvalidIdentifierRejected() {
        TreeBean bad = new TreeBean("eq").attr("name", "a; DROP TABLE t").attr("value", 1);
        assertThrows(NopException.class, () -> translator.translate(bad));
    }

    /** 字段名为注入尝试（带空格/引号）→ 显式失败。 */
    @Test
    public void testIdentifierWithSpaceRejected() {
        TreeBean bad = new TreeBean("eq").attr("name", "a OR 1=1").attr("value", 1);
        assertThrows(NopException.class, () -> translator.translate(bad));
    }

    /** 合法标识符（下划线/数字）通过。 */
    @Test
    public void testValidIdentifierAccepted() {
        assertDoesNotThrow(() -> translator.translate(FilterBeans.eq("order_id_2", 1)));
    }

    // ===== 不支持的 op / 缺字段 显式失败 =====

    @Test
    public void testUnsupportedOpThrows() {
        TreeBean bad = new TreeBean("contains").attr("name", "x").attr("value", "a");
        NopException ex = assertThrows(NopException.class, () -> translator.translate(bad));
        assertEquals(FilterToSqlTranslator.ERR_FILTER_UNSUPPORTED_OP.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testMissingFieldNameThrows() {
        TreeBean bad = new TreeBean("eq").attr("value", 1);
        assertThrows(NopException.class, () -> translator.translate(bad));
    }

    @Test
    public void testMissingValueThrows() {
        TreeBean bad = new TreeBean("eq").attr("name", "x");
        assertThrows(NopException.class, () -> translator.translate(bad));
    }
}
