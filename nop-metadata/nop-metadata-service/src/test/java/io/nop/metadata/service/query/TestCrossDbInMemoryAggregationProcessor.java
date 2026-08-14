package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCrossDbInMemoryAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对 self-join（双 entity 端点同一实体）显式失败（ERR_AGGR_JOIN_SELF_JOIN）。 */
    @Test
    public void testExecuteWithSelfJoinThrows() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaEntity entity = new NopMetaEntity();
        entity.setMetaEntityId("e1");
        MetaJoinExecutor.Endpoint ep = MetaJoinExecutor.Endpoint.entity(entity);
        when(context.getLeftEndpoint()).thenReturn(ep);
        when(context.getRightEndpoint()).thenReturn(ep);

        CrossDbInMemoryAggregationProcessor processor = new CrossDbInMemoryAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testMemoryGroupByWithEmptyRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testMemoryGroupBySingleRow() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("AMT", "sum", "amount", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("CAT", "category", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("category", "A");
        row1.put("amount", 100);
        rows.add(row1);

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).get("CAT"));
        assertEquals(100, ((Number) result.get(0).get("AMT")).intValue());
    }

    @Test
    public void testMemoryGroupByGroupsCorrectly() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("AMT", "sum", "amount", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("CAT", "category", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("category", "A", "amount", 10));
        rows.add(mapOf("category", "A", "amount", 20));
        rows.add(mapOf("category", "B", "amount", 30));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);

        assertEquals(2, result.size());

        Map<String, Object> gA = result.get(0);
        assertEquals("A", gA.get("CAT"));
        assertEquals(30, ((Number) gA.get("AMT")).intValue());

        Map<String, Object> gB = result.get(1);
        assertEquals("B", gB.get("CAT"));
        assertEquals(30, ((Number) gB.get("AMT")).intValue());
    }

    @Test
    public void testMemoryGroupByWithAggFuncSum() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("VAL", "sum", "value", "left"));
        List<AggregationContext.CrossDbMeasureSpec> measuresCount = new ArrayList<>();
        measuresCount.add(new AggregationContext.CrossDbMeasureSpec("VAL", "count", "value", "left"));
        List<AggregationContext.CrossDbMeasureSpec> measuresAvg = new ArrayList<>();
        measuresAvg.add(new AggregationContext.CrossDbMeasureSpec("VAL", "avg", "value", "left"));
        List<AggregationContext.CrossDbMeasureSpec> measuresMin = new ArrayList<>();
        measuresMin.add(new AggregationContext.CrossDbMeasureSpec("VAL", "min", "value", "left"));
        List<AggregationContext.CrossDbMeasureSpec> measuresMax = new ArrayList<>();
        measuresMax.add(new AggregationContext.CrossDbMeasureSpec("VAL", "max", "value", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("G", "group", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("group", "X", "value", 1));
        rows.add(mapOf("group", "X", "value", 2));
        rows.add(mapOf("group", "X", "value", 3));

        List<Map<String, Object>> rSum = AggregationHelper.memoryGroupBy(rows, measures, dims);
        assertEquals(1, rSum.size());
        assertEquals(6, ((Number) rSum.get(0).get("VAL")).intValue());

        List<Map<String, Object>> rCount = AggregationHelper.memoryGroupBy(rows, measuresCount, dims);
        assertEquals(1, rCount.size());
        assertEquals(3L, rCount.get(0).get("VAL"));

        List<Map<String, Object>> rAvg = AggregationHelper.memoryGroupBy(rows, measuresAvg, dims);
        assertEquals(1, rAvg.size());

        List<Map<String, Object>> rMin = AggregationHelper.memoryGroupBy(rows, measuresMin, dims);
        assertEquals(1, rMin.size());
        assertEquals(1, ((Number) rMin.get(0).get("VAL")).intValue());

        List<Map<String, Object>> rMax = AggregationHelper.memoryGroupBy(rows, measuresMax, dims);
        assertEquals(1, rMax.size());
        assertEquals(3, ((Number) rMax.get(0).get("VAL")).intValue());
    }

    @Test
    public void testMemoryGroupByHandlesNullValues() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("AMT", "sum", "amount", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("CAT", "category", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("category", "A", "amount", null));
        rows.add(mapOf("category", "A", "amount", 5));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);
        assertEquals(1, result.size());
        assertEquals(5, ((Number) result.get(0).get("AMT")).intValue());
    }

    @Test
    public void testMemoryGroupByCountDistinct() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("VAL", "count_distinct", "value", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("value", "a"));
        rows.add(mapOf("value", "b"));
        rows.add(mapOf("value", "a"));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).get("VAL"));
    }

    // ===== AR-03（plan 2026-08-14-0707-2）：group-key 控制字符碰撞对抗测试 =====

    /**
     * 分隔符碰撞：维度值 ("a","\u0001b") 与 ("a\u0001","b") 在旧 \u0001 拼接方案下拼出相同 String key
     * → 行被错误合并为 1 组。结构性 key（List<Object>）按元素级 equals 区分 → 2 组。
     */
    @Test
    public void testMemoryGroupByControlCharDelimiterNoCollision() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("AMT", "sum", "amount", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("D1", "dim1", "left"));
        dims.add(new AggregationContext.CrossDbDimensionSpec("D2", "dim2", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf3("dim1", "a", "dim2", "\u0001b", "amount", 10));
        rows.add(mapOf3("dim1", "a\u0001", "dim2", "b", "amount", 20));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);

        assertEquals(2, result.size(),
                "rows with dimension values that collide under \\u0001 delimiter must form 2 distinct groups, got: " + result);
        // 各组 amount 独立聚合（未被错误合并）：10 与 20
        java.util.Set<Integer> amounts = new java.util.LinkedHashSet<>();
        for (Map<String, Object> g : result) {
            amounts.add(((Number) g.get("AMT")).intValue());
        }
        assertTrue(amounts.contains(10) && amounts.contains(20),
                "groups must keep independent sums {10,20}, got: " + amounts);
    }

    /**
     * null 哨兵碰撞：null 与字面量 "\u0000" 在旧方案下都映射为 "\u0000" sentinel → 合并为 1 组。
     * 结构性 key 中 null 元素与 "\u0000" String 元素 equals 返回 false → 2 组。
     */
    @Test
    public void testMemoryGroupByNullVsLiteralNulCharNoCollision() {
        List<AggregationContext.CrossDbMeasureSpec> measures = new ArrayList<>();
        measures.add(new AggregationContext.CrossDbMeasureSpec("AMT", "sum", "amount", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();
        dims.add(new AggregationContext.CrossDbDimensionSpec("D1", "dim1", "left"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("dim1", null, "amount", 10));
        rows.add(mapOf("dim1", "\u0000", "amount", 20));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);

        assertEquals(2, result.size(),
                "null and literal \"\\u0000\" must form 2 distinct groups, got: " + result);
    }

    @Test
    public void testTruncateCrossDbWithNullLimitOffset() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mapOf("k", "a"));
        items.add(mapOf("k", "b"));

        List<Map<String, Object>> result = AggregationHelper.truncateCrossDb(items, null, null);
        assertEquals(2, result.size());
    }

    @Test
    public void testTruncateCrossDbWithLimit() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mapOf("k", "a"));
        items.add(mapOf("k", "b"));
        items.add(mapOf("k", "c"));

        List<Map<String, Object>> result = AggregationHelper.truncateCrossDb(items, 2L, null);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).get("k"));
        assertEquals("b", result.get(1).get("k"));
    }

    @Test
    public void testTruncateCrossDbWithOffset() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mapOf("k", "a"));
        items.add(mapOf("k", "b"));
        items.add(mapOf("k", "c"));

        List<Map<String, Object>> result = AggregationHelper.truncateCrossDb(items, null, 1L);
        assertEquals(2, result.size());
        assertEquals("b", result.get(0).get("k"));
        assertEquals("c", result.get(1).get("k"));
    }

    @Test
    public void testCrossDbAliasOf() {
        NopMetaTableJoin join = new NopMetaTableJoin();
        assertNotNull(AggregationHelper.crossDbAliasOf(join));
    }

    @Test
    public void testMemAggAccumulatorUnsupportedFunc() {
        NopException ex = assertThrows(NopException.class,
                () -> AggregationContext.MemAggAccumulator.forFunc("UNKNOWN", "m1"));
        assertTrue(ex.getErrorCode().contains("agg-func-unsupported"));
    }

    @Test
    public void testMemAggAccumulatorSum() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("sum", "m1");
        assertNotNull(acc);
        acc.accumulate(10);
        acc.accumulate(20);
        assertEquals(30, ((Number) acc.result()).intValue());
    }

    @Test
    public void testMemAggAccumulatorSumNull() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("sum", "m1");
        acc.accumulate(null);
        acc.accumulate(5);
        assertEquals(5, ((Number) acc.result()).intValue());
    }

    @Test
    public void testMemAggAccumulatorCount() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("count", "m1");
        acc.accumulate("a");
        acc.accumulate("b");
        acc.accumulate(null);
        assertEquals(2L, acc.result());
    }

    @Test
    public void testMemAggAccumulatorAvg() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("avg", "m1");
        acc.accumulate(10);
        acc.accumulate(20);
        assertEquals(15.0, ((Number) acc.result()).doubleValue(), 1e-9);
    }

    @Test
    public void testMemAggAccumulatorMin() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("min", "m1");
        acc.accumulate(30);
        acc.accumulate(10);
        acc.accumulate(20);
        assertEquals(10, ((Number) acc.result()).intValue());
    }

    @Test
    public void testMemAggAccumulatorMax() {
        AggregationContext.MemAggAccumulator acc = AggregationContext.MemAggAccumulator.forFunc("max", "m1");
        acc.accumulate(10);
        acc.accumulate(30);
        acc.accumulate(20);
        assertEquals(30, ((Number) acc.result()).intValue());
    }

    @Test
    public void testMemAggAccumulatorAllNullReturnsNull() {
        AggregationContext.MemAggAccumulator sum = AggregationContext.MemAggAccumulator.forFunc("sum", "m1");
        sum.accumulate(null);
        assertNull(sum.result());

        AggregationContext.MemAggAccumulator min = AggregationContext.MemAggAccumulator.forFunc("min", "m1");
        min.accumulate(null);
        assertNull(min.result());

        AggregationContext.MemAggAccumulator max = AggregationContext.MemAggAccumulator.forFunc("max", "m1");
        max.accumulate(null);
        assertNull(max.result());

        AggregationContext.MemAggAccumulator avg = AggregationContext.MemAggAccumulator.forFunc("avg", "m1");
        avg.accumulate(null);
        assertNull(avg.result());
    }

    @Test
    public void testSafeAlias() {
        assertEquals("v", AggregationHelper.safeAlias(null));
        assertEquals("ABC", AggregationHelper.safeAlias("abc"));
        assertEquals("A_B", AggregationHelper.safeAlias("a b"));
        assertEquals("V_123", AggregationHelper.safeAlias("123"));
    }

    // ===== AR-10（plan 2026-08-14-1133-2）：toBigDecimal 精度无损 + String 数值覆盖 =====

    /**
     * AR-10：Long > 2^53 经 toBigDecimal 不丢精度。旧实现统一 doubleValue()，
     * Long.MAX_VALUE（2^63-1）经 double 会丢低位。
     */
    @Test
    public void testToBigDecimalLongPrecisionAbove2Pow53() {
        long bigLong = Long.MAX_VALUE; // 2^63 - 1 > 2^53
        java.math.BigDecimal bd = AggregationHelper.toBigDecimal(bigLong);
        assertNotNull(bd, "Long must convert to non-null BigDecimal");
        assertEquals(java.math.BigDecimal.valueOf(bigLong), bd,
                "Long > 2^53 must convert losslessly via longValue()");
        assertEquals(bigLong, bd.longValueExact(),
                "round-trip longValueExact must equal original Long");

        // 另一个 > 2^53 但 < Long.MAX 的值，验证非边界
        long mid = (1L << 60) + 12345L;
        assertEquals(java.math.BigDecimal.valueOf(mid), AggregationHelper.toBigDecimal(mid),
                "Long (2^60 + 12345) must convert losslessly");

        // AtomicLong 同样无损
        assertEquals(java.math.BigDecimal.valueOf(bigLong),
                AggregationHelper.toBigDecimal(new java.util.concurrent.atomic.AtomicLong(bigLong)),
                "AtomicLong > 2^53 must convert losslessly via longValue()");
    }

    /**
     * AR-10：Double 小数经 toBigDecimal 不被 longValue 截断。浮点类型保持 doubleValue()。
     */
    @Test
    public void testToBigDecimalDoubleKeepsFraction() {
        java.math.BigDecimal bd = AggregationHelper.toBigDecimal(1.5);
        assertNotNull(bd);
        assertEquals(1.5, bd.doubleValue(), 1e-9,
                "Double fraction must be preserved (not truncated by longValue)");
        assertEquals(0, new java.math.BigDecimal("1.5").compareTo(bd),
                "1.5 must round-trip as 1.5");

        // Float 同样走 doubleValue 分支
        assertNotNull(AggregationHelper.toBigDecimal(2.5f));
    }

    /**
     * AR-10：String 数值经 toBigDecimal 正确解析（不再直接 return null 被静默跳过）。
     * 部分 JDBC driver 以 String 交付数值，旧实现直接 return null → SumAcc 静默跳过 → 列 SUM/AVG 为 null。
     */
    @Test
    public void testToBigDecimalParsesNumericString() {
        assertEquals(0, new java.math.BigDecimal("123.45").compareTo(AggregationHelper.toBigDecimal("123.45")),
                "numeric String '123.45' must parse to BigDecimal(123.45)");
        assertEquals(0, new java.math.BigDecimal("123.45")
                        .compareTo(AggregationHelper.toBigDecimal("  123.45  ")),
                "numeric String with whitespace must trim-then-parse");
        assertEquals(java.math.BigDecimal.valueOf(42L), AggregationHelper.toBigDecimal("42"),
                "integer String must parse");
    }

    /** AR-10：非数值 String 仍返回 null（不抛异常打断聚合）。 */
    @Test
    public void testToBigDecimalNonNumericStringReturnsNull() {
        assertNull(AggregationHelper.toBigDecimal("abc"), "non-numeric String must return null");
        assertNull(AggregationHelper.toBigDecimal(""), "empty String must return null");
        assertNull(AggregationHelper.toBigDecimal("   "), "blank String must return null");
    }

    /** AR-10：BigInteger 已有无损分支，保持不变。BigDecimal 原样返回。 */
    @Test
    public void testToBigDecimalBigIntegerAndBigDecimalUnchanged() {
        assertEquals(new java.math.BigDecimal(java.math.BigInteger.TEN),
                AggregationHelper.toBigDecimal(java.math.BigInteger.TEN),
                "BigInteger must convert losslessly (existing branch unchanged)");
        java.math.BigDecimal orig = new java.math.BigDecimal("99999999999999999999.999");
        assertSame(orig, AggregationHelper.toBigDecimal(orig),
                "BigDecimal must return as-is");
    }

    /**
     * AR-10 接线验证：toBigDecimal 经 SumAcc.accumulate 在 cross-DB 内存聚合路径被调用——
     * Long.MAX_VALUE SUM 结果精确（不再因 doubleValue 丢精度）；String 数值不再被静默跳过。
     */
    @Test
    public void testSumAccPrecisionAndStringCoverageWired() {
        // Long > 2^53 经 SumAcc 累加精确
        AggregationContext.MemAggAccumulator accLong = AggregationContext.MemAggAccumulator.forFunc("sum", "m");
        accLong.accumulate(Long.MAX_VALUE);
        accLong.accumulate(Long.MAX_VALUE);
        java.math.BigDecimal twoMax = java.math.BigDecimal.valueOf(Long.MAX_VALUE)
                .multiply(java.math.BigDecimal.valueOf(2));
        assertEquals(0, twoMax.compareTo((java.math.BigDecimal) accLong.result()),
                "SumAcc of 2 * Long.MAX_VALUE must be lossless (2^63-1 * 2 exact)");

        // String 数值经 SumAcc 不再被静默跳过
        AggregationContext.MemAggAccumulator accStr = AggregationContext.MemAggAccumulator.forFunc("sum", "m");
        accStr.accumulate("123.45");
        accStr.accumulate("76.55");
        java.math.BigDecimal strSum = (java.math.BigDecimal) accStr.result();
        assertNotNull(strSum, "String numeric values must NOT be silently skipped (was null before AR-10)");
        assertEquals(0, new java.math.BigDecimal("200.00").compareTo(strSum),
                "String '123.45' + '76.55' must sum to 200.00");

        // String 非数值仍被跳过（不污染聚合）
        AggregationContext.MemAggAccumulator accMixed = AggregationContext.MemAggAccumulator.forFunc("sum", "m");
        accMixed.accumulate("abc");
        accMixed.accumulate(10);
        assertEquals(java.math.BigDecimal.valueOf(10), accMixed.result(),
                "non-numeric String must be skipped, numeric values still aggregated");
    }

    @Test
    public void testBuildResult() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mapOf("k", "v"));
        Map<String, Object> result = AggregationHelper.buildResult(items);
        assertSame(items, result.get("items"));
        assertTrue(result.containsKey("items"));
    }

    @Test
    public void testBuildResultNull() {
        Map<String, Object> result = AggregationHelper.buildResult(null);
        assertTrue(result.get("items") instanceof List);
        assertEquals(0, ((List<?>) result.get("items")).size());
    }

    private static Map<String, Object> mapOf(String k1, Object v1) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        return m;
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> mapOf3(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
