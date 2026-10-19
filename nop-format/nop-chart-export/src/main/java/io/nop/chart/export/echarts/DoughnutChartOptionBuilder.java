package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartType;

/**
 * Doughnut chart option builder: pie family with inner/outer radius (hole size).
 */
public class DoughnutChartOptionBuilder extends PieChartOptionBuilder {

    public DoughnutChartOptionBuilder() {
        super(true);
    }

    @Override
    public ChartType getSupportedType() {
        return ChartType.DOUGHNUT;
    }
}
