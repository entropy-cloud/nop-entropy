package io.nop.chart.export.filter;

import io.nop.chart.export.model.ChartDataSet;
import io.nop.excel.chart.model.ChartFilterModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Chart data filter for filtering chart data based on various criteria
 */
public class ChartDataFilter {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataFilter.class);
    
    /**
     * 应用过滤器到数据集
     * @param dataSets 原始数据集
     * @param filters 过滤器配置列表
     * @return 过滤后的数据集
     */
    public List<ChartDataSet> applyFilters(List<ChartDataSet> dataSets, List<ChartFilterModel> filters) {
        if (filters == null || filters.isEmpty()) {
            return dataSets;
        }
        
        LOG.debug("Applying {} filters to {} data sets", filters.size(), dataSets.size());
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>(dataSets);
        
        for (ChartFilterModel filter : filters) {
            filteredDataSets = applyFilter(filteredDataSets, filter);
        }
        
        LOG.debug("Filtered data sets: {} -> {}", dataSets.size(), filteredDataSets.size());
        return filteredDataSets;
    }
    
    /**
     * 应用单个过滤器
     * @param dataSets 数据集
     * @param filter 过滤器配置
     * @return 过滤后的数据集
     */
    public List<ChartDataSet> applyFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        if (filter == null || !filter.isEnabled()) {
            return dataSets;
        }
        
        LOG.debug("Applying filter: type={}", filter.getType());
        
        FilterType filterType = getFilterType(filter);
        
        switch (filterType) {
            case VALUE_RANGE:
                return applyValueRangeFilter(dataSets, filter);
            case TOP_N:
                return applyTopNFilter(dataSets, filter);
            case BOTTOM_N:
                return applyBottomNFilter(dataSets, filter);
            case CATEGORY:
                return applyCategoryFilter(dataSets, filter);
            case CUSTOM:
                return applyCustomFilter(dataSets, filter);
            default:
                LOG.warn("Unsupported filter type: {}", filter.getType());
                return dataSets;
        }
    }
    
    private List<ChartDataSet> applyValueRangeFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        LOG.debug("Applying value range filter");
        
        double minValue = getFilterMinValue(filter);
        double maxValue = getFilterMaxValue(filter);
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = filterDataSetByValueRange(dataSet, minValue, maxValue);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    private List<ChartDataSet> applyTopNFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        LOG.debug("Applying top N filter");
        
        int n = getFilterCount(filter);
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = getTopNFromDataSet(dataSet, n);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    private List<ChartDataSet> applyBottomNFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        LOG.debug("Applying bottom N filter");
        
        int n = getFilterCount(filter);
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = getBottomNFromDataSet(dataSet, n);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    private List<ChartDataSet> applyCategoryFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        LOG.debug("Applying category filter");
        
        List<String> allowedCategories = getFilterCategories(filter);
        
        List<ChartDataSet> filteredDataSets = new ArrayList<>();
        
        for (ChartDataSet dataSet : dataSets) {
            ChartDataSet filteredDataSet = filterDataSetByCategories(dataSet, allowedCategories);
            if (filteredDataSet != null && !filteredDataSet.getValues().isEmpty()) {
                filteredDataSets.add(filteredDataSet);
            }
        }
        
        return filteredDataSets;
    }
    
    private List<ChartDataSet> applyCustomFilter(List<ChartDataSet> dataSets, ChartFilterModel filter) {
        LOG.debug("Applying custom filter");
        
        // 自定义过滤器实现
        // 这里可以根据具体需求实现复杂的过滤逻辑
        
        return dataSets;
    }
    
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
        filteredDataSet.setXValues(filteredXValues.isEmpty() ? null : filteredXValues);
        
        return filteredDataSet;
    }
    
    private ChartDataSet getTopNFromDataSet(ChartDataSet dataSet, int n) {
        // 获取前N个最大值
        ChartDataSet sortedDataSet = sortDataSetByValue(dataSet, false); // 降序
        return limitDataSetSize(sortedDataSet, n);
    }
    
    private ChartDataSet getBottomNFromDataSet(ChartDataSet dataSet, int n) {
        // 获取前N个最小值
        ChartDataSet sortedDataSet = sortDataSetByValue(dataSet, true); // 升序
        return limitDataSetSize(sortedDataSet, n);
    }
    
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
        filteredDataSet.setXValues(filteredXValues.isEmpty() ? null : filteredXValues);
        
        return filteredDataSet;
    }
    
    private ChartDataSet sortDataSetByValue(ChartDataSet dataSet, boolean ascending) {
        // 简化实现：返回原数据集
        // 实际实现需要根据值排序并保持类别和值的对应关系
        return dataSet;
    }
    
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
    
    private FilterType getFilterType(ChartFilterModel filter) {
        if (filter.getType() != null) {
            String type = filter.getType().toLowerCase();
            switch (type) {
                case "valuerange":
                case "value_range":
                    return FilterType.VALUE_RANGE;
                case "topn":
                case "top_n":
                    return FilterType.TOP_N;
                case "bottomn":
                case "bottom_n":
                    return FilterType.BOTTOM_N;
                case "category":
                    return FilterType.CATEGORY;
                case "custom":
                    return FilterType.CUSTOM;
                default:
                    return FilterType.VALUE_RANGE;
            }
        }
        return FilterType.VALUE_RANGE;
    }
    
    private double getFilterMinValue(ChartFilterModel filter) {
        // 从过滤器配置中获取最小值
        // 这里需要根据实际的ChartFilterModel结构来实现
        return Double.MIN_VALUE;
    }
    
    private double getFilterMaxValue(ChartFilterModel filter) {
        // 从过滤器配置中获取最大值
        // 这里需要根据实际的ChartFilterModel结构来实现
        return Double.MAX_VALUE;
    }
    
    private int getFilterCount(ChartFilterModel filter) {
        // 从过滤器配置中获取数量
        // 这里需要根据实际的ChartFilterModel结构来实现
        return 10; // 默认值
    }
    
    private List<String> getFilterCategories(ChartFilterModel filter) {
        // 从过滤器配置中获取允许的类别列表
        // 这里需要根据实际的ChartFilterModel结构来实现
        return new ArrayList<>();
    }
    
    /**
     * 过滤器类型枚举
     */
    public enum FilterType {
        VALUE_RANGE,
        TOP_N,
        BOTTOM_N,
        CATEGORY,
        CUSTOM
    }
}