package io.nop.chart.export.model;

import io.nop.excel.chart.model.ChartSeriesModel;

import java.util.Collections;
import java.util.List;

/**
 * Chart data set model
 */
public class ChartDataSet {
    private String name;
    private List<Object> categories = Collections.emptyList();
    private List<Number> values = Collections.emptyList();
    private List<Number> xValues = Collections.emptyList();  // 用于散点图和气泡图
    private List<Number> bubbleSizes = Collections.emptyList();  // 用于气泡图
    private List<Number> heatmapValues = Collections.emptyList();  // 用于热力图
    private ChartSeriesModel seriesModel;
    
    public ChartDataSet() {
    }
    
    public ChartDataSet(String name, List<Object> categories, List<Number> values) {
        this.name = name;
        this.categories = categories;
        this.values = values;
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<Object> getCategories() {
        return categories;
    }
    
    public void setCategories(List<Object> categories) {
        this.categories = categories;
    }
    
    public List<Number> getValues() {
        return values;
    }
    
    public void setValues(List<Number> values) {
        this.values = values;
    }
    
    public List<Number> getXValues() {
        return xValues;
    }
    
    public void setXValues(List<Number> xValues) {
        this.xValues = xValues;
    }
    
    public List<Number> getBubbleSizes() {
        return bubbleSizes;
    }
    
    public List<Number> getYValues() {
        return values; // Y values are the same as values for most chart types
    }
    
    public void setYValues(List<Number> yValues) {
        this.values = yValues;
    }
    
    public void setBubbleSizes(List<Number> bubbleSizes) {
        this.bubbleSizes = bubbleSizes;
    }
    
    public List<Number> getHeatmapValues() {
        return heatmapValues;
    }
    
    public void setHeatmapValues(List<Number> heatmapValues) {
        this.heatmapValues = heatmapValues;
    }
    
    public ChartSeriesModel getSeriesModel() {
        return seriesModel;
    }
    
    public void setSeriesModel(ChartSeriesModel seriesModel) {
        this.seriesModel = seriesModel;
    }
}