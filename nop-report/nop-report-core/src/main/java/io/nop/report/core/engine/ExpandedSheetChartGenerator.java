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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ExpandedSheetChartGenerator {
    static final Logger LOG = LoggerFactory.getLogger(ExpandedSheetChartGenerator.class);

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
        if (chartModel.getType() == null)
            return;

        ExcelChartModel chart = chartModel.cloneInstance();
        ChartPlotAreaModel plotArea = chart.getPlotArea();
        if (plotArea == null)
            return;

        if (bindings == null) {
            sheet.makeCharts().add(chart);
            return;
        }

        if (bindings.getChartTestExpr() != null) {
            boolean b = ConvertHelper.toBoolean(bindings.getChartTestExpr().call1(null, chart, xptRt.getEvalScope()));
            if (!b) {
                LOG.info("nop.chart.ignore-chart:{},loc={}", chart.getName(), chart.getLocation());
                return;
            }
        }

        sheet.makeCharts().add(chart);

        ChartTitleModel title = chart.getTitle();
        if (title != null) {
            // 处理单元格引用表达式
            String calcValue = calcChartTitleCellRef(bindings, title.getTextCellRef(), chart, xptRt);
            title.setTextCellRef(calcValue);

            if (calcValue == null) {
                // 处理直接文本表达式
                String calcText = calcChartTitleText(bindings, title.getText(), chart, xptRt);
                if (calcText != null) {
                    title.setText(calcText);
                }
            } else {
                title.setText(null);
            }
        }

        if (plotArea.getSeriesList() != null) {
            Iterator<ChartSeriesModel> it = plotArea.getSeriesList().iterator();
            while (it.hasNext()) {
                ChartSeriesModel seriesModel = it.next();
                if (!passConditions(bindings, seriesModel, chartModel, xptRt)) {
                    LOG.debug("nop.chart.ignore-series:chart={},seriesIndex={}", chart.getName(), seriesModel.getIndex());
                    it.remove();
                    continue;
                }

                String dataCellRef = seriesModel.getDataCellRef();
                dataCellRef = calcValuesCellRef(bindings, dataCellRef, seriesModel, chart, xptRt);
                seriesModel.setDataCellRef(dataCellRef);

                // 处理系列数据直接表达式（如果需要的话，这里可以扩展处理逻辑）
                // seriesDataExpr 通常用于直接提供数据数组，而不是单元格引用
                // 这种情况下可能需要在更高层次的处理中使用

                String nameCellRef = seriesModel.getNameCellRef();
                nameCellRef = calcNameCellRef(bindings, nameCellRef, seriesModel, chart, xptRt);
                seriesModel.setNameCellRef(nameCellRef);

                if (nameCellRef == null) {
                    // 处理系列名称直接文本表达式
                    String calcName = calcSeriesName(bindings, seriesModel.getName(), seriesModel, chart, xptRt);
                    if (calcName != null) {
                        seriesModel.setName(calcName);
                    }
                } else {
                    seriesModel.setName(null);
                }

                String catCellRef = seriesModel.getCatCellRef();
                String calcValue = calcCatCellRef(bindings, catCellRef, seriesModel, chart, xptRt);
                seriesModel.setCatCellRef(calcValue);

                // 处理系列分类直接表达式（如果需要的话，这里可以扩展处理逻辑）
                // seriesCatExpr 通常用于直接提供分类数组，而不是单元格引用
                // 这种情况下可能需要在更高层次的处理中使用
            }
        }

        // 处理坐标轴
        if (plotArea.getAxes() != null) {
            for (ChartAxisModel axisModel : plotArea.getAxes()) {
                String dataCellRef = axisModel.getDataCellRef();
                dataCellRef = calcAxisDataCellRef(bindings, dataCellRef, axisModel, chart, xptRt);
                axisModel.setDataCellRef(dataCellRef);

                ChartAxisTitleModel axisTitle = axisModel.getTitle();
                if (axisTitle != null) {
                    // 处理坐标轴标题单元格引用表达式
                    String calcValue = calcAxisTitleCellRef(bindings, axisTitle.getTextCellRef(), axisModel, chart, xptRt);
                    axisTitle.setTextCellRef(calcValue);


                    if (calcValue == null) {
                        // 处理坐标轴标题直接文本表达式
                        String calcText = calcAxisTitleText(bindings, axisTitle.getText(), axisModel, chart, xptRt);
                        if (calcText != null) {
                            axisTitle.setText(calcText);
                        }
                    } else {
                        axisTitle.setText(null);
                    }
                }
            }
        }
    }

    boolean passConditions(ChartDynamicBindingsModel bindings, ChartSeriesModel seriesModel,
                           ChartModel chartModel, IXptRuntime xptRt) {
        if (bindings.getSeriesTestExpr() == null)
            return true;
        return ConvertHelper.toBoolean(bindings.getSeriesTestExpr().call2(null, seriesModel, chartModel, xptRt.getEvalScope()));
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
        if (value == null)
            value = textCellRef;
        return value;
    }

    String calcChartTitleText(ChartDynamicBindingsModel bindings,
                              String text,
                              ChartModel chart,
                              IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getChartTitleExpr();
        if (fn == null)
            return text;

        String value = ConvertHelper.toString(fn.call1(null, chart, xptRt.getEvalScope()));
        return value;
    }

    String calcSeriesName(ChartDynamicBindingsModel bindings,
                          String name,
                          ChartSeriesModel series, ChartModel chart,
                          IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getSeriesNameExpr();
        if (fn == null)
            return name;

        String value = ConvertHelper.toString(fn.call2(null, series, chart, xptRt.getEvalScope()));
        return value;
    }

    String calcAxisTitleText(ChartDynamicBindingsModel bindings,
                             String text,
                             ChartAxisModel axis, ChartModel chart,
                             IXptRuntime xptRt) {
        IEvalFunction fn = bindings.getAxisTitleExpr();
        if (fn == null)
            return text;

        String value = ConvertHelper.toString(fn.call2(null, axis, chart, xptRt.getEvalScope()));
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
        if (value == null)
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
        if (value == null)
            value = textCellRef;
        return value;
    }
}
