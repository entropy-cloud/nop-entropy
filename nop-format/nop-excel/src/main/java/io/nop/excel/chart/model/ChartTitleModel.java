package io.nop.excel.chart.model;

import io.nop.excel.chart.model._gen._ChartTitleModel;
import io.nop.excel.model.ExcelFont;

public class ChartTitleModel extends _ChartTitleModel {
    public ChartTitleModel() {

    }

    public ExcelFont getTitleFont() {
        ChartTextStyleModel textStyle = getTextStyle();
        return textStyle == null ? null : textStyle.getFont();
    }
}
