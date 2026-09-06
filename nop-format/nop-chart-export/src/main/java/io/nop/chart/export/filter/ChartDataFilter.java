package io.nop.chart.export.filter;

import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.chart.model.ChartFiltersModel;
import io.nop.excel.chart.model.ChartValueFilterModel;
import io.nop.excel.chart.model.ChartTopNFilterModel;
import io.nop.excel.chart.model.ChartCategoryFilterModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Chart data filter for filtering chart data based on various criteria
 */
public class ChartDataFilter {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataFilter.class);
    
    /**
     * 应用过滤器到数据集
     * @param dataSets 原始数据集
     * @param filters 过滤器配置
     * @return 过滤后的数据集
     */
    public List<ChartDataSet> applyFilters(List<ChartDataSet> dataSets, ChartFiltersModel filters) {
        if (filters == null) {
            return dataSets;
        }
        
        LOG.debug("Applying filters to {} data sets", dataSets.size());
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>(dataSets);
        
        // 应用值过滤器
        ChartValueFilterModel valueFilter = filters.getValueFilter();
        if (valueFilter != null && Boolean.TRUE.equals(valueFilter.getEnabled())) {
            filteredDataSets = applyValueFilter(filteredDataSets, valueFilter);
        }
        
        // 应用类别过滤器
        ChartCategoryFilterModel categoryFilter = filters.getCategoryFilter();
        if (categoryFilter != null && Boolean.TRUE.equals(categoryFilter.getEnabled())) {
            filteredDataSets = applyCategoryFilter(filteredDataSets, categoryFilter);
        }
        
        // 应用顶部N过滤器
        ChartTopNFilterModel topNFilter = filters.getTopNFilter();
        if (topNFilter != null && Boolean.TRUE.equals(topNFilter.getEnabled())) {
            filteredDataSets = applyTopNFilter(filteredDataSets, topNFilter);
        }
        
        LOG.debug("Filtered data sets: {} -> {}", dataSets.size(), filteredDataSets.size());
        return filteredDataSets;
    }
    
    /**
     * 应用值过滤器
     * @param dataSets 数据集
     * @param filter 值过滤器配置
     * @return 过滤后的数据集
     */
    private List<ChartDataSet> applyValueFilter(List<ChartDataSet> dataSets, ChartValueFilterModel filter) {
        LOG.debug("Applying value range filter");

        // Double.MIN_VALUE是最小正数而非负无穷，作为缺省下界会把0和负值全部过滤掉
        double minValue = filter.getMin() != null ? filter.getMin() : Double.NEGATIVE_INFINITY;
        double maxValue = filter.getMax() != null ? filter.getMax() : Double.MAX_VALUE;
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = filterDataSetByValueRange(dataSet, minValue, maxValue);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    /**
     * 应用顶部N过滤器
     * @param dataSets 数据集
     * @param filter 顶部N过滤器配置
     * @return 过滤后的数据集
     */
    private List<ChartDataSet> applyTopNFilter(List<ChartDataSet> dataSets, ChartTopNFilterModel filter) {
        LOG.debug("Applying top N filter");
        
        int n = filter.getN() != null ? filter.getN() : 10;
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = getTopNFromDataSet(dataSet, n);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    /**
     * 应用类别过滤器
     * @param dataSets 数据集
     * @param filter 类别过滤器配置
     * @return 过滤后的数据集
     */
    private List<ChartDataSet> applyCategoryFilter(List<ChartDataSet> dataSets, ChartCategoryFilterModel filter) {
        LOG.debug("Applying category filter");
        
        List<String> allowedCategories = filter.getCategories();
        if (allowedCategories == null) {
            allowedCategories = Collections.emptyList();
        }
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = filterDataSetByCategories(dataSet, allowedCategories);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    /**
     * 根据值范围过滤数据集
     * @param dataSet 数据集
     * @param minValue 最小值
     * @param maxValue 最大值
     * @return 过滤后的数据集
     */
    private ChartDataSet filterDataSetByValueRange(ChartDataSet dataSet, double minValue, double maxValue) {
        ChartDataSet filteredDataSet = new ChartDataSet();
        filteredDataSet.setName(dataSet.getName());
        filteredDataSet.setSeriesModel(dataSet.getSeriesModel());
        
        List<Object> filteredCategories = new ArrayList<>();
        List<Number> filteredValues = new ArrayList<>();
        List<Number> filteredXValues = new ArrayList<>();
        
        List<Object> categories = dataSet.getCategories();
        List<Number> values = dataSet.getValues();
        List<Number> xValues = dataSet.getXValues();
        
        for (int i = 0; i < values.size(); i++) {
            Number value = values.get(i);
            if (value != null) {
                double doubleValue = value.doubleValue();
                if (doubleValue >= minValue && doubleValue <= maxValue) {
                    filteredValues.add(value);
                    
                    if (categories != null && i < categories.size()) {
                        filteredCategories.add(categories.get(i));
                    }
                    
                    if (xValues != null && i < xValues.size()) {
                        filteredXValues.add(xValues.get(i));
                    }
                }
            }
        }
        
        filteredDataSet.setCategories(filteredCategories);
        filteredDataSet.setValues(filteredValues);
        // ChartDataSet约定xValues非null（渲染器直接调用xValues.size()），不能置null
        filteredDataSet.setXValues(filteredXValues);

        return filteredDataSet;
    }

    /**
     * 获取数据集中的前N个最大值
     * @param dataSet 数据集
     * @param n 数量
     * @return 过滤后的数据集
     */
    private ChartDataSet getTopNFromDataSet(ChartDataSet dataSet, int n) {
        // 获取前N个最大值
        ChartDataSet sortedDataSet = sortDataSetByValue(dataSet, false); // 降序
        return limitDataSetSize(sortedDataSet, n);
    }
    
    /**
     * 根据类别过滤数据集
     * @param dataSet 数据集
     * @param allowedCategories 允许的类别列表
     * @return 过滤后的数据集
     */
    private ChartDataSet filterDataSetByCategories(ChartDataSet dataSet, List<String> allowedCategories) {
        ChartDataSet filteredDataSet = new ChartDataSet();
        filteredDataSet.setName(dataSet.getName());
        filteredDataSet.setSeriesModel(dataSet.getSeriesModel());
        
        List<Object> filteredCategories = new ArrayList<>();
        List<Number> filteredValues = new ArrayList<>();
        List<Number> filteredXValues = new ArrayList<>();
        
        List<Object> categories = dataSet.getCategories();
        List<Number> values = dataSet.getValues();
        List<Number> xValues = dataSet.getXValues();
        
        if (categories != null) {
            for (int i = 0; i < categories.size(); i++) {
                Object category = categories.get(i);
                if (category != null && allowedCategories.contains(category.toString())) {
                    filteredCategories.add(category);
                    
                    if (values != null && i < values.size()) {
                        filteredValues.add(values.get(i));
                    }
                    
                    if (xValues != null && i < xValues.size()) {
                        filteredXValues.add(xValues.get(i));
                    }
                }
            }
        }
        
        filteredDataSet.setCategories(filteredCategories);
        filteredDataSet.setValues(filteredValues);
        // ChartDataSet约定xValues非null（渲染器直接调用xValues.size()），不能置null
        filteredDataSet.setXValues(filteredXValues);

        return filteredDataSet;
    }

    /**
     * 按值排序数据集
     * @param dataSet 数据集
     * @param ascending 是否升序
     * @return 排序后的数据集
     */
    private ChartDataSet sortDataSetByValue(ChartDataSet dataSet, boolean ascending) {
        List<Object> categories = dataSet.getCategories();
        List<Number> values = dataSet.getValues();
        List<Number> xValues = dataSet.getXValues();

        // 按值排序并保持categories/values/xValues的对应关系；null值排在最后
        Integer[] indices = new Integer[values.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> {
            Number va = values.get(a);
            Number vb = values.get(b);
            if (va == null && vb == null)
                return 0;
            if (va == null)
                return 1;
            if (vb == null)
                return -1;
            int cmp = Double.compare(va.doubleValue(), vb.doubleValue());
            return ascending ? cmp : -cmp;
        });

        ChartDataSet sortedDataSet = new ChartDataSet();
        sortedDataSet.setName(dataSet.getName());
        sortedDataSet.setSeriesModel(dataSet.getSeriesModel());

        List<Object> sortedCategories = new ArrayList<>(categories == null ? 0 : categories.size());
        List<Number> sortedValues = new ArrayList<>(values.size());
        List<Number> sortedXValues = new ArrayList<>(xValues == null ? 0 : xValues.size());
        List<Number> sortedBubbleSizes = new ArrayList<>();
        List<Number> sortedHeatmapValues = new ArrayList<>();
        List<Number> bubbleSizes = dataSet.getBubbleSizes();
        List<Number> heatmapValues = dataSet.getHeatmapValues();
        for (Integer index : indices) {
            sortedValues.add(values.get(index));
            if (categories != null && index < categories.size()) {
                sortedCategories.add(categories.get(index));
            }
            if (xValues != null && index < xValues.size()) {
                sortedXValues.add(xValues.get(index));
            }
            if (bubbleSizes != null && index < bubbleSizes.size()) {
                sortedBubbleSizes.add(bubbleSizes.get(index));
            }
            if (heatmapValues != null && index < heatmapValues.size()) {
                sortedHeatmapValues.add(heatmapValues.get(index));
            }
        }

        sortedDataSet.setCategories(sortedCategories);
        sortedDataSet.setValues(sortedValues);
        sortedDataSet.setXValues(sortedXValues);
        if (!sortedBubbleSizes.isEmpty())
            sortedDataSet.setBubbleSizes(sortedBubbleSizes);
        if (!sortedHeatmapValues.isEmpty())
            sortedDataSet.setHeatmapValues(sortedHeatmapValues);
        return sortedDataSet;
    }
    
    /**
     * 限制数据集大小
     * @param dataSet 数据集
     * @param maxSize 最大大小
     * @return 限制大小后的数据集
     */
    private ChartDataSet limitDataSetSize(ChartDataSet dataSet, int maxSize) {
        if (dataSet.getValues().size() <= maxSize) {
            return dataSet;
        }
        
        ChartDataSet limitedDataSet = new ChartDataSet();
        limitedDataSet.setName(dataSet.getName());
        limitedDataSet.setSeriesModel(dataSet.getSeriesModel());
        
        List<Object> categories = dataSet.getCategories();
        List<Number> values = dataSet.getValues();
        List<Number> xValues = dataSet.getXValues();
        
        limitedDataSet.setValues(values.subList(0, maxSize));
        
        if (categories != null && categories.size() > maxSize) {
            limitedDataSet.setCategories(categories.subList(0, maxSize));
        } else {
            limitedDataSet.setCategories(categories);
        }
        
        if (xValues != null && xValues.size() > maxSize) {
            limitedDataSet.setXValues(xValues.subList(0, maxSize));
        } else {
            limitedDataSet.setXValues(xValues);
        }
        
        return limitedDataSet;
    }
    
    
}