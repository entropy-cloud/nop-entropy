package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.commons.util.StringHelper;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Chart data validator for data integrity checks
 */
public class ChartDataValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataValidator.class);
    
    /**
     * 验证图表模型
     * @param chartModel 图表模型
     */
    public void validateChartModel(ChartModel chartModel) {
        if (chartModel == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "null");
        }
        
        if (chartModel.getType() == null) {
            throw new NopException(ChartExportErrors.ERR_UNSUPPORTED_CHART_TYPE)
                .param(ChartExportErrors.ARG_CHART_TYPE, "null");
        }
        
        // 验证尺寸
        if (chartModel.getWidth() != null && chartModel.getWidth() <= 0) {
            throw new NopException(ChartExportErrors.ERR_INVALID_DIMENSIONS)
                .param(ChartExportErrors.ARG_WIDTH, chartModel.getWidth())
                .param(ChartExportErrors.ARG_HEIGHT, chartModel.getHeight());
        }
        
        if (chartModel.getHeight() != null && chartModel.getHeight() <= 0) {
            throw new NopException(ChartExportErrors.ERR_INVALID_DIMENSIONS)
                .param(ChartExportErrors.ARG_WIDTH, chartModel.getWidth())
                .param(ChartExportErrors.ARG_HEIGHT, chartModel.getHeight());
        }
        
        LOG.debug("Chart model validation passed for type: {}", chartModel.getType());
    }
    
    /**
     * 验证系列数据
     * @param series 系列模型
     * @param dataSet 数据集
     */
    public void validateSeriesData(ChartSeriesModel series, ChartDataSet dataSet) {
        if (series == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "series cannot be null");
        }
        
        if (dataSet == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "dataSet cannot be null");
        }
        
        // 验证系列ID
        if (StringHelper.isEmpty(series.getId())) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "series id cannot be empty");
        }
        
        // 验证数据完整性
        List<Number> values = dataSet.getValues();
        if (values == null || values.isEmpty()) {
            LOG.warn("Series {} has no data values", series.getId());
            return;
        }
        
        // 检查数据一致性
        List<Object> categories = dataSet.getCategories();
        if (categories != null && !categories.isEmpty()) {
            if (categories.size() != values.size()) {
                LOG.warn("Series {} has mismatched categories ({}) and values ({}) count", 
                        series.getId(), categories.size(), values.size());
            }
        }
        
        LOG.debug("Series data validation passed for: {}", series.getId());
    }
    
    /**
     * 验证坐标轴数据
     * @param axis 坐标轴模型
     * @param data 数据列表
     */
    public void validateAxisData(ChartAxisModel axis, List<Object> data) {
        if (axis == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "axis cannot be null");
        }
        
        if (StringHelper.isEmpty(axis.getId())) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "axis id cannot be empty");
        }
        
        // 验证坐标轴类型
        if (axis.getType() == null) {
            throw new NopException(ChartExportErrors.ERR_INVALID_CHART_MODEL)
                .param(ChartExportErrors.ARG_CHART_MODEL, "axis type cannot be null");
        }
        
        // 验证数据范围
        if (data != null && !data.isEmpty()) {
            LOG.debug("Axis {} has {} data points", axis.getId(), data.size());
        }
        
        LOG.debug("Axis data validation passed for: {}", axis.getId());
    }
    
    /**
     * 检查数据量限制
     * @param dataSet 数据集
     * @param maxSize 最大数据量
     */
    public void checkDataVolumeLimit(ChartDataSet dataSet, int maxSize) {
        if (dataSet == null) {
            return;
        }
        
        List<Number> values = dataSet.getValues();
        if (values != null && values.size() > maxSize) {
            throw new NopException(ChartExportErrors.ERR_DATA_VOLUME_EXCEEDED)
                .param(ChartExportErrors.ARG_ACTUAL_SIZE, values.size())
                .param(ChartExportErrors.ARG_MAX_SIZE, maxSize);
        }
        
        List<Object> categories = dataSet.getCategories();
        if (categories != null && categories.size() > maxSize) {
            throw new NopException(ChartExportErrors.ERR_DATA_VOLUME_EXCEEDED)
                .param(ChartExportErrors.ARG_ACTUAL_SIZE, categories.size())
                .param(ChartExportErrors.ARG_MAX_SIZE, maxSize);
        }
    }
    
    /**
     * 处理缺失数据
     * @param dataSet 数据集
     */
    public void handleMissingData(ChartDataSet dataSet) {
        if (dataSet == null) {
            return;
        }
        
        List<Number> values = dataSet.getValues();
        if (values != null) {
            int nullCount = 0;
            for (Number value : values) {
                if (value == null) {
                    nullCount++;
                }
            }
            
            if (nullCount > 0) {
                LOG.debug("Data set {} has {} null values out of {}", 
                         dataSet.getName(), nullCount, values.size());
            }
        }
        
        List<Object> categories = dataSet.getCategories();
        if (categories != null) {
            int nullCount = 0;
            for (Object category : categories) {
                if (category == null || StringHelper.isEmpty(category.toString())) {
                    nullCount++;
                }
            }
            
            if (nullCount > 0) {
                LOG.debug("Data set {} has {} empty categories out of {}", 
                         dataSet.getName(), nullCount, categories.size());
            }
        }
    }
}