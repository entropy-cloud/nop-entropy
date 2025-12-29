package io.nop.excel.chart.model;

import io.nop.excel.chart.model._gen._ChartAxisTitleModel;
import io.nop.excel.model.ExcelFont;

public class ChartAxisTitleModel extends _ChartAxisTitleModel {
    public ChartAxisTitleModel() {

    }

    public ExcelFont getTitleFont() {
        ChartTextStyleModel textStyle = getTextStyle();
        return textStyle == null ? null : textStyle.getFont();
    }
}
