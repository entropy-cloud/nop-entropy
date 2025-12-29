package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartAxisTitleModel;
import io.nop.excel.chart.model.ChartDynamicBindingsModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.report.core.model.ExpandedSheet;

import java.util.List;

public class ExpandedSheetChartGenerator {
    public static ExpandedSheetChartGenerator INSTANCE = new ExpandedSheetChartGenerator();

    public void generateCharts(ExpandedSheet sheet, ExcelSheet sheetTpl, IXptRuntime xptRt) {
        List<ExcelChartModel> charts = sheetTpl.getCharts();
        if (charts == null || charts.isEmpty())
            return;

        for (ExcelChartModel chartModel : charts) {
            generateChart(sheet, chartModel, xptRt);
        }
    }

    void generateChart(ExpandedSheet sheet, ExcelChartModel chartModel, IXptRuntime xptRt) {
        ChartDynamicBindingsModel bindings = chartModel.getDynamicBindings();
        if (bindings == null || chartModel.getType() == null)
            return;

        ExcelChartModel chart = chartModel.cloneInstance();
        ChartPlotAreaModel plotArea = chart.getPlotArea();
        if (plotArea == null)
            return;

        sheet.makeCharts().add(chart);

        ChartTitleModel title = chart.getTitle();
        if(title != null && title.getTextCellRef() != null){
            String calcValue = calcChartTitleCellRef(bindings, title.getTextCellRef(), chart, xptRt);
            title.setTextCellRef(calcValue);
        }

        if (plotArea.getSeriesList() != null) {
            for (ChartSeriesModel seriesModel : plotArea.getSeriesList()) {
                String dataCellRef = seriesModel.getDataCellRef();
                if (dataCellRef != null) {
                    String calcValue = calcValuesCellRef(bindings, dataCellRef, seriesModel, chart, xptRt);
                    seriesModel.setDataCellRef(calcValue);
                }

                String nameCellRef = seriesModel.getNameCellRef();
                if (nameCellRef != null) {
                    String calcValue = calcNameCellRef(bindings, nameCellRef, seriesModel, chart, xptRt);
                    seriesModel.setNameCellRef(calcValue);
                }

                String catCellRef = seriesModel.getCatCellRef();
                if (catCellRef != null) {
                    String calcValue = calcCatCellRef(bindings, catCellRef, seriesModel, chart, xptRt);
                    seriesModel.setCatCellRef(calcValue);
                }
            }
        }

        // 处理坐标轴
        if (plotArea.getAxes() != null) {
            for (ChartAxisModel axisModel : plotArea.getAxes()) {
                String dataCellRef = axisModel.getDataCellRef();
                if (dataCellRef != null) {
                    String calcValue = calcAxisDataCellRef(bindings, dataCellRef, axisModel, chart, xptRt);
                    axisModel.setDataCellRef(calcValue);
                }

                ChartAxisTitleModel axisTitle = axisModel.getTitle();
                if (axisTitle != null && axisTitle.getTextCellRef() != null) {
                    String calcValue = calcAxisTitleCellRef(bindings, axisTitle.getTextCellRef(), axisModel, chart, xptRt);
                    axisTitle.setTextCellRef(calcValue);
                }
            }
        }
    }

    String calcValuesCellRef(ChartDynamicBindingsModel bindings,
                             String dataCellRef,
                             ChartSeriesModel series, ChartModel chart,
                             IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getSeriesDataCellRefExpr();
        if (fn == null)
            return dataCellRef;

        String value = ConvertHelper.toString(fn.call2(null, series, chart, xptRt.getEvalScope()));
        if (value == null)
            value = dataCellRef;
        return value;
    }

    String calcNameCellRef(ChartDynamicBindingsModel bindings,
                           String nameCellRef,
                           ChartSeriesModel series, ChartModel chart,
                           IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getSeriesNameCellRefExpr();
        if (fn == null)
            return nameCellRef;

        String value = ConvertHelper.toString(fn.call2(null, series, chart, xptRt.getEvalScope()));
        if (value == null)
            value = nameCellRef;
        return value;
    }

    String calcCatCellRef(ChartDynamicBindingsModel bindings,
                          String catCellRef,
                          ChartSeriesModel series, ChartModel chart,
                          IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getSeriesCatCellRefExpr();
        if (fn == null)
            return catCellRef;

        String value = ConvertHelper.toString(fn.call2(null, series, chart, xptRt.getEvalScope()));
        if (value == null)
            value = catCellRef;
        return value;
    }

    String calcChartTitleCellRef(ChartDynamicBindingsModel bindings,
                                 String textCellRef,
                                 ChartModel chart,
                                 IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getChartTitleCellRefExpr();
        if (fn == null)
            return textCellRef;

        String value = ConvertHelper.toString(fn.call1(null, chart, xptRt.getEvalScope()));
        if(value == null)
            value = textCellRef;
        return value;
    }

    String calcAxisDataCellRef(ChartDynamicBindingsModel bindings,
                               String dataCellRef,
                               ChartAxisModel axis, ChartModel chart,
                               IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getAxisDataCellRefExpr();
        if (fn == null)
            return dataCellRef;

        String value = ConvertHelper.toString(fn.call2(null, axis, chart, xptRt.getEvalScope()));
        if(value == null)
            value = dataCellRef;
        return value;
    }

    String calcAxisTitleCellRef(ChartDynamicBindingsModel bindings,
                                String textCellRef,
                                ChartAxisModel axis, ChartModel chart,
                                IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getAxisTitleCellRefExpr();
        if (fn == null)
            return textCellRef;

        String value = ConvertHelper.toString(fn.call2(null, axis, chart, xptRt.getEvalScope()));
        if(value == null)
            value = textCellRef;
        return value;
    }
}
