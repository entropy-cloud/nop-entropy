package io.nop.excel.model;

import io.nop.api.core.util.INeedInit;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.model._gen._ExcelChartModel;

import java.util.List;

public class ExcelChartModel extends _ExcelChartModel implements INeedInit {
    public ExcelChartModel() {

    }

    public ExcelChartModel cloneInstance() {
        ExcelChartModel instance = super.cloneInstance();
        if (instance.getTitle() != null) {
            instance.setTitle(instance.getTitle().cloneInstance());
        }
        if (getPlotArea() != null) {
            instance.setPlotArea(getPlotArea().cloneInstance());
        }
        return instance;
    }

    @Override
    public void init() {
        ChartPlotAreaModel plotArea = getPlotArea();
        if (plotArea != null) {
            List<ChartSeriesModel> series = plotArea.getSeriesList();
            if (series != null) {
                int index = 0;
                for (ChartSeriesModel seriesModel : series) {
                    seriesModel.setIndex(index++);
                }
            }
        }
    }
}
