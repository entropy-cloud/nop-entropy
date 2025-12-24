/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.model.ExcelImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Result structure for drawing parsing that separates images and charts.
 * Provides backward compatibility with existing image-only parsing while
 * supporting new chart parsing functionality.
 */
public class DrawingParseResult {
    private List<ExcelImage> images = new ArrayList<>();
    private List<ChartModel> charts = new ArrayList<>();
    
    public DrawingParseResult() {
    }
    
    /**
     * Get the list of parsed images
     * @return list of ExcelImage objects
     */
    public List<ExcelImage> getImages() {
        return images;
    }
    
    /**
     * Set the list of images
     * @param images list of ExcelImage objects
     */
    public void setImages(List<ExcelImage> images) {
        this.images = images != null ? images : new ArrayList<>();
    }
    
    /**
     * Add a single image to the result
     * @param image ExcelImage to add
     */
    public void addImage(ExcelImage image) {
        if (image != null) {
            this.images.add(image);
        }
    }
    
    /**
     * Get the list of parsed charts
     * @return list of ChartModel objects
     */
    public List<ChartModel> getCharts() {
        return charts;
    }
    
    /**
     * Set the list of charts
     * @param charts list of ChartModel objects
     */
    public void setCharts(List<ChartModel> charts) {
        this.charts = charts != null ? charts : new ArrayList<>();
    }
    
    /**
     * Add a single chart to the result
     * @param chart ChartModel to add
     */
    public void addChart(ChartModel chart) {
        if (chart != null) {
            this.charts.add(chart);
        }
    }
    
    /**
     * Check if the result contains any images
     * @return true if images list is not empty
     */
    public boolean hasImages() {
        return !images.isEmpty();
    }
    
    /**
     * Check if the result contains any charts
     * @return true if charts list is not empty
     */
    public boolean hasCharts() {
        return !charts.isEmpty();
    }
    
    /**
     * Check if the result is empty (no images or charts)
     * @return true if both images and charts lists are empty
     */
    public boolean isEmpty() {
        return images.isEmpty() && charts.isEmpty();
    }
    
    /**
     * Get the total count of all drawing elements (images + charts)
     * @return total count of images and charts
     */
    public int getTotalCount() {
        return images.size() + charts.size();
    }
    
    /**
     * Get the count of images
     * @return number of images
     */
    public int getImageCount() {
        return images.size();
    }
    
    /**
     * Get the count of charts
     * @return number of charts
     */
    public int getChartCount() {
        return charts.size();
    }
    
    /**
     * Backward compatibility method - returns only the images list.
     * This allows existing code that expects List&lt;ExcelImage&gt; to continue working.
     * @return list of ExcelImage objects
     */
    public List<ExcelImage> getImagesOnly() {
        return getImages();
    }
    
    /**
     * Clear all images and charts from the result
     */
    public void clear() {
        images.clear();
        charts.clear();
    }
    
    @Override
    public String toString() {
        return "DrawingParseResult{" +
                "images=" + images.size() +
                ", charts=" + charts.size() +
                '}';
    }
}