package io.nop.excel.resolver;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.commons.util.StringHelper;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chart data resolver for converting cell references to actual data
 */
public class ChartDataResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataResolver.class);

    public static ChartDataResolver INSTANCE = new ChartDataResolver();

    /**
     * 解析系列数据
     *
     * @param plotArea 绘图区域模型
     * @param resolver 数据解析器
     * @return 数据集列表
     */
    public List<ChartDataSet> resolveSeriesData(ChartPlotAreaModel plotArea, ICellRefResolver resolver) {
        if (plotArea == null || plotArea.getSeriesList() == null) {
            return Collections.emptyList();
        }

        List<ChartDataSet> dataSets = new ArrayList<>();

        for (ChartSeriesModel series : plotArea.getSeriesList()) {
            ChartDataSet dataSet = resolveSeriesData(series, resolver);
            dataSets.add(dataSet);
        }

        return dataSets;
    }

    /**
     * 解析单个系列的数据
     *
     * @param series   系列模型
     * @param resolver 数据解析器
     * @return 数据集
     */
    public ChartDataSet resolveSeriesData(ChartSeriesModel series, ICellRefResolver resolver) {
        ChartDataSet dataSet = new ChartDataSet();
        dataSet.setSeriesModel(series);

        // 解析系列名称
        String name = resolveSeriesName(series, resolver);
        dataSet.setName(name);

        // 解析分类数据
        List<Object> categories = resolveCategories(series.getCatCellRef(), resolver);
        dataSet.setCategories(categories);

        // 解析数值数据
        List<Number> values = resolveValues(series.getDataCellRef(), resolver);
        dataSet.setValues(values);

        // 对于散点图和气泡图，dataCellRef对应yVal，catCellRef对应xVal
        if (isScatterOrBubbleChart(series)) {
            dataSet.setXValues(convertToNumbers(categories));
            dataSet.setCategories(null); // 散点图不需要分类
        }

        return dataSet;
    }

    /**
     * 解析分类数据
     *
     * @param catCellRef 分类单元格引用
     * @param resolver   数据解析器
     * @return 分类列表
     */
    public List<Object> resolveCategories(String catCellRef, ICellRefResolver resolver) {
        if (StringHelper.isEmpty(catCellRef)) {
            return Collections.emptyList();
        }

        List<Object> values = resolver.getValues(catCellRef);
        return values != null ? values : Collections.emptyList();
    }

    /**
     * 解析数值数据
     *
     * @param dataCellRef 数据单元格引用
     * @param resolver    数据解析器
     * @return 数值列表
     */
    public List<Number> resolveValues(String dataCellRef, ICellRefResolver resolver) {
        if (StringHelper.isEmpty(dataCellRef)) {
            return Collections.emptyList();
        }

        List<Object> rawValues = resolver.getValues(dataCellRef);
        return convertToNumbers(rawValues);
    }

    /**
     * 解析系列名称
     *
     * @param series   系列模型
     * @param resolver 数据解析器
     * @return 系列名称
     */
    private String resolveSeriesName(ChartSeriesModel series, ICellRefResolver resolver) {
        // 尝试从单元格引用解析
        if (StringHelper.isNotEmpty(series.getNameCellRef())) {
            Object value = resolver.getValue(series.getNameCellRef());
            if (value != null) {
                return value.toString();
            }
        } else if (StringHelper.isNotEmpty(series.getName())) {
            return series.getName();
        }

        // 使用系列ID作为默认名称
        return StringHelper.toString(series.getId(), "Series " + series.getIndex());
    }

    /**
     * 转换对象列表为数值列表
     *
     * @param values 对象列表
     * @return 数值列表
     */
    private List<Number> convertToNumbers(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<Number> numbers = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                numbers.add(null);
            } else {
                Number number = ConvertHelper.toNumber(value, NopException::new);
                numbers.add(number);
            }
        }

        return numbers;
    }

    /**
     * 检查是否为散点图或气泡图
     *
     * @param series 系列模型
     * @return true如果是散点图或气泡图
     */
    private boolean isScatterOrBubbleChart(ChartSeriesModel series) {
        if (series.getType() != null) {
            return series.getType() == ChartType.SCATTER || series.getType() == ChartType.BUBBLE;
        }
        return false;
    }
}