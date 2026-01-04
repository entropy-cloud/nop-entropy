package io.nop.excel.chart.model;

import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.model._gen._ChartBarConfigModel;

public class ChartBarConfigModel extends _ChartBarConfigModel {
    public ChartBarConfigModel() {

    }

    public boolean isStackedChart() {
        return getGrouping() == ChartBarGrouping.STACKED || getGrouping() == ChartBarGrouping.PERCENT_STACKED;
    }
}
