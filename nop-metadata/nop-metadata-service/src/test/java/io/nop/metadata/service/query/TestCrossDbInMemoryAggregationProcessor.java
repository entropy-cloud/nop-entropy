package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestCrossDbInMemoryAggregationProcessor {

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new CrossDbInMemoryAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        CrossDbInMemoryAggregationProcessor processor = new CrossDbInMemoryAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        CrossDbInMemoryAggregationProcessor processor = new CrossDbInMemoryAggregationProcessor();
        assertNotNull(processor);
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
        measures.add(new AggregationContext.CrossDbMeasureSpec("VAL", "countDistinct", "value", "left"));
        List<AggregationContext.CrossDbDimensionSpec> dims = new ArrayList<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mapOf("value", "a"));
        rows.add(mapOf("value", "b"));
        rows.add(mapOf("value", "a"));

        List<Map<String, Object>> result = AggregationHelper.memoryGroupBy(rows, measures, dims);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).get("VAL"));
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
}
