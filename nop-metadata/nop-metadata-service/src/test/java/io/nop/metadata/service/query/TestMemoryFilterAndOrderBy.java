package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MemoryFilterEvaluator + MemoryOrderByComparator 独立单元测试（plan 2026-07-18-0900-2 Phase 3：#25）。
 *
 * <p>覆盖 op 集合 + 类型强转（Long/Integer/BigDecimal/Double）+ 嵌套 and/or/not + case-insensitive name 匹配 +
 * null 策略 + 多键排序 + desc/nullsFirst 生效。不经端到端 BizModel，直接测试新组件。
 */
public class TestMemoryFilterAndOrderBy {

    private static NopMetaTable table() {
        NopMetaTable t = new NopMetaTable();
        t.setMetaTableId("meta-table-test");
        return t;
    }

    private static Map<String, String> nameToAlias(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ============ MemoryFilterEvaluator 测试 ============

    @Test
    public void testEqComparison() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        // Long vs Integer 等值
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.eq("total", 30), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30L)));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.eq("total", 30), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30)));
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.eq("total", 30), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 31)));
    }

    @Test
    public void testGtLtComparisonWithTypeCoercion() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        // Long vs Integer 比较（类型强转 BigDecimal）
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.gt("total", 29), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30L)));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.ge("total", 30), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30L)));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.lt("total", 31), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30L)));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.le("total", 30), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30L)));
    }

    @Test
    public void testBigDecimalVsDouble() {
        Map<String, String> names = nameToAlias("avg", "AVG");
        // BigDecimal vs Double 类型强转
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.gt("avg", 1.5), names, table(), Arrays.asList("avg"), Arrays.asList(),
                row("AVG", new BigDecimal("2.0"))));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.lt("avg", 3.0), names, table(), Arrays.asList("avg"), Arrays.asList(),
                row("AVG", new BigDecimal("2.5"))));
    }

    @Test
    public void testBetween() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        TreeBean between = FilterBeans.between("total", 10, 50);
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                between, names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30)));
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                between, names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 60)));
    }

    @Test
    public void testIn() {
        Map<String, String> names = nameToAlias("cat", "CAT");
        TreeBean in = FilterBeans.in("cat", Arrays.asList("A", "B"));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                in, names, table(), Arrays.asList(), Arrays.asList("cat"),
                row("CAT", "A")));
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                in, names, table(), Arrays.asList(), Arrays.asList("cat"),
                row("CAT", "C")));
    }

    @Test
    public void testIsNullAndNotNull() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.isNull("total"), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", null)));
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.isNull("total"), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30)));
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.notNull("total"), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30)));
    }

    @Test
    public void testLike() {
        Map<String, String> names = nameToAlias("cat", "CAT");
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.like("cat", "Cat%"), names, table(), Arrays.asList(), Arrays.asList("cat"),
                row("CAT", "Category A")));
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.like("cat", "Z%"), names, table(), Arrays.asList(), Arrays.asList("cat"),
                row("CAT", "Category A")));
    }

    @Test
    public void testNestedAndOrNot() {
        Map<String, String> names = nameToAlias("total", "TOTAL", "cat", "CAT");
        // (total > 20 AND cat = 'A') OR NOT(total < 10)
        TreeBean tree = FilterBeans.or(
                FilterBeans.and(
                        FilterBeans.gt("total", 20),
                        FilterBeans.eq("cat", "A")),
                FilterBeans.not(FilterBeans.lt("total", 10)));
        // total=30, cat=A → 第一支 true
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                tree, names, table(), Arrays.asList("total"), Arrays.asList("cat"),
                row("TOTAL", 30, "CAT", "A")));
        // total=5, cat=B → 第一支 false (total<=20)，not(total<10)=false → false
        assertFalse(MemoryFilterEvaluator.evaluateForTest(
                tree, names, table(), Arrays.asList("total"), Arrays.asList("cat"),
                row("TOTAL", 5, "CAT", "B")));
        // total=50, cat=B → 第一支 false (cat!=A)，not(total<10)=true → true
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                tree, names, table(), Arrays.asList("total"), Arrays.asList("cat"),
                row("TOTAL", 50, "CAT", "B")));
    }

    @Test
    public void testCaseInsensitiveAliasMatch() {
        // safeAlias 大写化 alias，但 row 的 key 可能是大小写混合；求值须 case-insensitive 匹配
        Map<String, String> names = nameToAlias("total", "TOTAL");
        assertTrue(MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.gt("total", 20), names, table(), Arrays.asList("total"), Arrays.asList(),
                row("total", 30)));  // 小写 key
    }

    @Test
    public void testUnknownNameFailsExplicitly() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        // having 引用未选定 name 'unknown_measure' → 显式失败
        assertThrows(NopException.class, () -> MemoryFilterEvaluator.evaluateForTest(
                FilterBeans.gt("unknown_measure", 20), names, table(),
                Arrays.asList("total"), Arrays.asList(),
                row("TOTAL", 30)));
    }

    @Test
    public void testFilterList() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("TOTAL", 10),
                row("TOTAL", 30),
                row("TOTAL", 50));
        List<Map<String, Object>> filtered = MemoryFilterEvaluator.filterForTest(
                FilterBeans.gt("total", 20), names, table(), Arrays.asList("total"), Arrays.asList(),
                rows);
        assertEquals(2, filtered.size(), "should filter out TOTAL=10");
    }

    // ============ MemoryOrderByComparator 测试 ============

    @Test
    public void testSortBySingleFieldAsc() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("TOTAL", 30),
                row("TOTAL", 10),
                row("TOTAL", 50));
        List<Map<String, Object>> sorted = MemoryOrderByComparator.sortForTest(
                rows, Arrays.asList(OrderFieldBean.asc("total")), names, table(),
                Arrays.asList("total"), Arrays.asList());
        assertEquals(10, ((Number) sorted.get(0).get("TOTAL")).intValue());
        assertEquals(30, ((Number) sorted.get(1).get("TOTAL")).intValue());
        assertEquals(50, ((Number) sorted.get(2).get("TOTAL")).intValue());
    }

    @Test
    public void testSortBySingleFieldDesc() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("TOTAL", 30),
                row("TOTAL", 10),
                row("TOTAL", 50));
        List<Map<String, Object>> sorted = MemoryOrderByComparator.sortForTest(
                rows, Arrays.asList(OrderFieldBean.desc("total")), names, table(),
                Arrays.asList("total"), Arrays.asList());
        assertEquals(50, ((Number) sorted.get(0).get("TOTAL")).intValue());
        assertEquals(30, ((Number) sorted.get(1).get("TOTAL")).intValue());
        assertEquals(10, ((Number) sorted.get(2).get("TOTAL")).intValue());
    }

    @Test
    public void testSortNullsFirst() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("TOTAL", 30),
                row("TOTAL", null),
                row("TOTAL", 10));
        OrderFieldBean f = OrderFieldBean.asc("total");
        f.setNullsFirst(true);
        List<Map<String, Object>> sorted = MemoryOrderByComparator.sortForTest(
                rows, Arrays.asList(f), names, table(), Arrays.asList("total"), Arrays.asList());
        // null 排前
        assertFalse(sorted.get(0).containsKey("TOTAL") && sorted.get(0).get("TOTAL") != null);
        assertEquals(10, ((Number) sorted.get(1).get("TOTAL")).intValue());
        assertEquals(30, ((Number) sorted.get(2).get("TOTAL")).intValue());
    }

    @Test
    public void testSortMultiKey() {
        Map<String, String> names = nameToAlias("cat", "CAT", "total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("CAT", "B", "TOTAL", 10),
                row("CAT", "A", "TOTAL", 30),
                row("CAT", "A", "TOTAL", 10));
        // 先按 cat ASC，再按 total DESC
        List<Map<String, Object>> sorted = MemoryOrderByComparator.sortForTest(
                rows, Arrays.asList(OrderFieldBean.asc("cat"), OrderFieldBean.desc("total")),
                names, table(), Arrays.asList("total"), Arrays.asList("cat"));
        assertEquals("A", sorted.get(0).get("CAT"));
        assertEquals(30, ((Number) sorted.get(0).get("TOTAL")).intValue());
        assertEquals("A", sorted.get(1).get("CAT"));
        assertEquals(10, ((Number) sorted.get(1).get("TOTAL")).intValue());
        assertEquals("B", sorted.get(2).get("CAT"));
    }

    @Test
    public void testSortTypeCoercion() {
        // 混合 Long/Integer/BigDecimal 排序
        Map<String, String> names = nameToAlias("total", "TOTAL");
        List<Map<String, Object>> rows = Arrays.asList(
                row("TOTAL", new BigDecimal("20.0")),
                row("TOTAL", 10),
                row("TOTAL", 30L));
        List<Map<String, Object>> sorted = MemoryOrderByComparator.sortForTest(
                rows, Arrays.asList(OrderFieldBean.asc("total")), names, table(),
                Arrays.asList("total"), Arrays.asList());
        assertEquals(10, ((Number) sorted.get(0).get("TOTAL")).intValue());
        assertEquals(20, ((Number) sorted.get(1).get("TOTAL")).intValue());
        assertEquals(30, ((Number) sorted.get(2).get("TOTAL")).intValue());
    }

    @Test
    public void testSortUnknownNameFailsExplicitly() {
        Map<String, String> names = nameToAlias("total", "TOTAL");
        assertThrows(NopException.class, () -> MemoryOrderByComparator.sortForTest(
                Arrays.asList(row("TOTAL", 30)),
                Arrays.asList(OrderFieldBean.asc("unknown")), names, table(),
                Arrays.asList("total"), Arrays.asList()));
    }
}
