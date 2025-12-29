package io.nop.excel.chart.model;

import io.nop.excel.chart.model._gen._ChartPlotAreaModel;

import java.util.stream.Collectors;

public class ChartPlotAreaModel extends _ChartPlotAreaModel {
    public ChartPlotAreaModel() {

    }

    @Override
    public ChartPlotAreaModel cloneInstance() {
        ChartPlotAreaModel instance = super.cloneInstance();

        if (this.getSeriesList() != null) {
            instance.setSeriesList(this.getSeriesList().stream()
                    .map(ChartSeriesModel::cloneInstance).collect(Collectors.toList()));
        }
        if (this.getAxes() != null) {
            instance.setAxes(this.getAxes().stream()
                    .map(ChartAxisModel::cloneInstance).collect(Collectors.toList()));
        }
        return instance;
    }
}
