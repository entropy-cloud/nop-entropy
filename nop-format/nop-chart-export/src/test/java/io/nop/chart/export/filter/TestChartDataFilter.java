package io.nop.chart.export.filter;

import io.nop.excel.chart.model.ChartFiltersModel;
import io.nop.excel.chart.model.ChartTopNFilterModel;
import io.nop.excel.chart.model.ChartValueFilterModel;
import io.nop.excel.chart.util.ChartDataSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChartDataFilter 的数据正确性回归：
 * 1. min未配置时缺省下界不能是Double.MIN_VALUE（最小正数），否则0和负值全部被过滤
 * 2. TopN必须按值降序取最大N个（此前是桩实现，返回原始顺序前N个）
 * 3. 过滤结果xValues必须保持非null（渲染器直接调用xValues.size()）
 */
public class TestChartDataFilter {

    private static ChartDataSet dataSet(List<Object> categories, List<Number> values) {
        ChartDataSet ds = new ChartDataSet();
        ds.setName("s1");
        ds.setCategories(categories);
        ds.setValues(values);
        return ds;
    }

    @Test
    public void testValueFilterKeepsZeroAndNegativesWhenMinUnset() {
        ChartValueFilterModel valueFilter = new ChartValueFilterModel();
        valueFilter.setEnabled(true);
        valueFilter.setMax(100.0);
        ChartFiltersModel filters = new ChartFiltersModel();
        filters.setValueFilter(valueFilter);

        List<ChartDataSet> result = new ChartDataFilter().applyFilters(
                Arrays.asList(dataSet(Arrays.asList("a", "b", "c"), Arrays.asList(-5.0, 0.0, 50.0))), filters);

        // 修复前min缺省为Double.MIN_VALUE，-5和0被过滤，只剩50
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getValues().size());
        assertEquals(-5.0, result.get(0).getValues().get(0).doubleValue(), 1e-9);
        assertEquals(0.0, result.get(0).getValues().get(1).doubleValue(), 1e-9);
        // xValues不能被置null
        assertNotNull(result.get(0).getXValues());
    }

    @Test
    public void testValueFilterHonorsConfiguredRange() {
        ChartValueFilterModel valueFilter = new ChartValueFilterModel();
        valueFilter.setEnabled(true);
        valueFilter.setMin(0.0);
        valueFilter.setMax(100.0);
        ChartFiltersModel filters = new ChartFiltersModel();
        filters.setValueFilter(valueFilter);

        List<ChartDataSet> result = new ChartDataFilter().applyFilters(
                Arrays.asList(dataSet(Arrays.asList("a", "b", "c"), Arrays.asList(-5.0, 50.0, 200.0))), filters);

        assertEquals(1, result.size());
        assertEquals(Arrays.asList(50.0), result.get(0).getValues());
        assertEquals(Arrays.asList("b"), result.get(0).getCategories());
    }

    @Test
    public void testTopNReturnsLargestValues() {
        ChartTopNFilterModel topNFilter = new ChartTopNFilterModel();
        topNFilter.setEnabled(true);
        topNFilter.setN(2);
        ChartFiltersModel filters = new ChartFiltersModel();
        filters.setTopNFilter(topNFilter);

        List<ChartDataSet> result = new ChartDataFilter().applyFilters(
                Arrays.asList(dataSet(Arrays.asList("a", "b", "c", "d", "e"),
                        Arrays.asList(1.0, 50.0, 2.0, 99.0, 3.0))), filters);

        // 修复前桩实现返回原始顺序前2个[1,50]，期望按值最大的2个[99,50]且类别对应
        assertEquals(1, result.size());
        assertEquals(Arrays.asList(99.0, 50.0), result.get(0).getValues());
        assertEquals(Arrays.asList("d", "b"), result.get(0).getCategories());
        assertNotNull(result.get(0).getXValues());
    }
}
