/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.mdx;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.commons.collections.MutableIntArray;
import io.nop.orm.dao.DaoQueryHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MdxQueryExecutor} 的维度键构造与子查询过滤条件生成。
 *
 * <p>这两处是主/子表合并的正确性与性能边界：
 * <ul>
 *     <li>维度键必须容忍主子表关联列的数据类型差异，同时不能把 SQL NULL 与空字符串混为一谈。</li>
 *     <li>子查询过滤条件只影响扫描范围，不能因为NULL或列表长度而失效。</li>
 * </ul>
 */
class TestMdxQueryExecutor {

    private MdxQueryExecutor newExecutor() {
        return new MdxQueryExecutor(null);
    }

    private static MutableIntArray indexes(int... idx) {
        MutableIntArray ret = new MutableIntArray();
        for (int i : idx)
            ret.add(i);
        return ret;
    }

    // ---------------------------------------------------------------- 维度键

    /**
     * 关联列在主子表两侧可能返回不同的Java类型，键必须归一化后才能相等。
     */
    @Test
    void testDimValueNormalizesCrossTypeValues() {
        MdxQueryExecutor executor = newExecutor();
        MutableIntArray idx = indexes(0);

        assertEquals("1", executor.buildDimValue(new Object[]{Integer.valueOf(1)}, idx));
        assertEquals("1", executor.buildDimValue(new Object[]{Long.valueOf(1L)}, idx));
        assertEquals("1", executor.buildDimValue(new Object[]{"1"}, idx));
    }

    /**
     * SQL NULL 必须保留为null。早期实现把它转成空字符串，会导致：
     * <ul>
     *     <li>NULL 与真正的空字符串共享同一个维度键，合并到错误的行；</li>
     *     <li>空字符串被写进IN列表，数值列直接报 invalid input syntax。</li>
     * </ul>
     */
    @Test
    void testDimValueKeepsNullDistinctFromEmptyString() {
        MdxQueryExecutor executor = newExecutor();
        MutableIntArray idx = indexes(0);

        assertNull(executor.buildDimValue(new Object[]{null}, idx));
        assertEquals("", executor.buildDimValue(new Object[]{""}, idx));
    }

    @Test
    void testCompositeDimValueKeepsNullPerPosition() {
        MdxQueryExecutor executor = newExecutor();
        MutableIntArray idx = indexes(0, 1);

        assertEquals(Arrays.asList("a", null), executor.buildDimValue(new Object[]{"a", null}, idx));
        assertEquals(Arrays.asList(null, "b"), executor.buildDimValue(new Object[]{null, "b"}, idx));
        assertEquals(Arrays.asList("", ""), executor.buildDimValue(new Object[]{"", ""}, idx));
    }

    /**
     * 复合维度键必须是可变List之外的稳定值语义：equals/hashCode基于内容，
     * 否则HashMap查不到对应的行。
     */
    @Test
    void testCompositeDimValueEqualsByContent() {
        MdxQueryExecutor executor = newExecutor();
        MutableIntArray idx = indexes(0, 1);

        Object a = executor.buildDimValue(new Object[]{"x", "y"}, idx);
        Object b = executor.buildDimValue(new Object[]{"x", "y"}, idx);

        Set<Object> set = new LinkedHashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(1, set.size(), "相同维度内容必须落到同一个键");
    }

    // ---------------------------------------------------------------- 子查询过滤条件

    private TreeBean filter(List<String> dimFields, Object... dimValues) {
        return newExecutor().buildDimFilter(dimFields, Arrays.asList(dimValues));
    }

    @Test
    void testSingleDimFilterProducesIn() {
        TreeBean f = filter(List.of("deptId"), "a", "b");

        assertEquals("in", f.getTagName());
        assertEquals("deptId", f.getAttr("name"));
        assertEquals(Arrays.asList("a", "b"), f.getAttr("value"));
    }

    /**
     * 出现NULL维度值时必须用 isNull，而不是把空字符串塞进IN列表。
     */
    @Test
    void testSingleDimFilterWithNullUsesIsNull() {
        TreeBean f = filter(List.of("deptId"), "a", null);

        assertEquals("or", f.getTagName());
        assertEquals(2, f.getChildCount());
        assertEquals("in", f.getChildren().get(0).getTagName());
        assertEquals(Arrays.asList("a"), f.getChildren().get(0).getAttr("value"));
        assertEquals("isNull", f.getChildren().get(1).getTagName());
        assertEquals("deptId", f.getChildren().get(1).getAttr("name"));
    }

    @Test
    void testSingleDimFilterWithOnlyNull() {
        TreeBean f = filter(List.of("deptId"), (Object) null);

        assertEquals("isNull", f.getTagName());
    }

    /**
     * 单维度分支与复合维度分支对NULL的处理必须一致（都用 isNull），
     * 否则同一个语义会因维度个数不同而得到不同的结果集。
     */
    @Test
    void testNullHandlingIsConsistentAcrossDimArities() {
        TreeBean single = filter(List.of("deptId"), (Object) null);
        TreeBean composite = filter(List.of("a", "b"), Arrays.asList(null, null));

        assertEquals("isNull", single.getTagName());
        assertEquals("and", composite.getTagName());
        assertEquals(2, composite.getChildCount());
        assertEquals("isNull", composite.getChildren().get(0).getTagName());
        assertEquals("isNull", composite.getChildren().get(1).getTagName());
    }

    @Test
    void testCompositeDimFilterProducesOrOfAnd() {
        TreeBean f = filter(List.of("a", "b"),
                Arrays.asList("1", "2"),
                Arrays.asList("3", "4"));

        assertEquals("or", f.getTagName());
        assertEquals(2, f.getChildCount());
        assertEquals("and", f.getChildren().get(0).getTagName());
        assertEquals(2, f.getChildren().get(0).getChildCount());
    }

    /**
     * IN 列表超过 {@code nop.core.sql.in-list-max-size}（默认1000）会被
     * SQL.spreadParams 全局拦截并抛 {@code nop.err.sql.in-list-too-large}。
     * 主表行数不受分页约束，必须分片。
     */
    @Test
    void testLargeDimValueListIsChunked() {
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < 2500; i++)
            values.add("v" + i);

        TreeBean f = newExecutor().buildDimFilter(List.of("deptId"), values);

        assertEquals("or", f.getTagName());
        int total = 0;
        for (TreeBean child : f.getChildren()) {
            assertEquals("in", child.getTagName());
            Collection<?> chunk = (Collection<?>) child.getAttr("value");
            assertTrue(chunk.size() <= 1000, "单个IN片段不得超过上限，实际=" + chunk.size());
            total += chunk.size();
        }
        assertEquals(3, f.getChildCount(), "2500个值应被切成3片");
        assertEquals(2500, total);

        // 生成SQL不得触发IN列表长度门禁
        MdxQueryBean sub = new MdxQueryBean();
        sub.setSourceName("SubEntity");
        sub.setDimFields(List.of("deptId"));
        sub.setFields(List.of(QueryFieldBean.forField("deptId")));
        sub.addFilter(f);
        DaoQueryHelper.queryToSelectFieldsSql(null, sub, null).end();
    }

    @Test
    void testEmptyDimValueSetProducesAlwaysFalse() {
        TreeBean f = newExecutor().buildDimFilter(List.of("deptId"), List.of());

        assertEquals("alwaysFalse", f.getTagName());
    }

    @Test
    void testNonNullSingleDimFilterIsNotAlwaysFalse() {
        TreeBean f = filter(List.of("deptId"), "only");

        assertFalse("alwaysFalse".equals(f.getTagName()));
    }
}