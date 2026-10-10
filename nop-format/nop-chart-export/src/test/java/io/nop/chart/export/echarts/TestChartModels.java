package io.nop.chart.export.echarts;

import io.nop.chart.export.IChartOptionBuilder;
import io.nop.excel.chart.constants.ChartAxisType;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.util.ChartDataSet;
import io.nop.excel.resolver.ICellRefResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for building minimal ChartModel trees in adapter tests.
 */
final class TestChartModels {

    static final ICellRefResolver NOOP_RESOLVER = new ICellRefResolver() {
        @Override
        public Object getValue(String cellRef) {
            return cellRef;
        }

        @Override
        public List<Object> getValues(String cellRangeRef) {
            return new ArrayList<>();
        }

        @Override
        public boolean isValidRef(String cellRef) {
            return true;
        }
    };

    private TestChartModels() {
    }

    @SafeVarargs
    static ChartModel chart(ChartType type, ChartSeriesModel... series) {
        ChartModel model = new ChartModel();
        model.setType(type);
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        List<ChartSeriesModel> list = new ArrayList<>(Arrays.asList(series));
        for (int i = 0; i < list.size(); i++)
            list.get(i).setIndex(i);
        plotArea.setSeriesList(io.nop.commons.collections.KeyedList.fromList(list, s -> String.valueOf(s.getIndex())));
        model.setPlotArea(plotArea);
        return model;
    }

    static ChartSeriesModel series(String name) {
        ChartSeriesModel s = new ChartSeriesModel();
        s.setName(name);
        return s;
    }

    static ChartDataSet data(String name, List<Object> categories, List<Number> values) {
        return new ChartDataSet(name, categories, values);
    }

    static ChartAxisModel axis(String id, ChartAxisType type) {
        ChartAxisModel axis = new ChartAxisModel();
        axis.setId(id);
        axis.setType(type);
        axis.setVisible(true);
        return axis;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> option(IChartOptionBuilder builder, ChartModel chart, ChartDataSet... data) {
        List<ChartDataSet> dataSets = new ArrayList<>(Arrays.asList(data));
        // Link datasets to their series by index, mirroring ChartDataResolver output.
        List<ChartSeriesModel> seriesModels = chart.getPlotArea() != null
                ? chart.getPlotArea().getSeriesList() : new ArrayList<>();
        for (int i = 0; i < dataSets.size() && i < seriesModels.size(); i++)
            dataSets.get(i).setSeriesModel(seriesModels.get(i));
        return (Map<String, Object>) builder.buildOption(chart, NOOP_RESOLVER, dataSets);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> seriesList(Map<String, Object> option) {
        return (List<Map<String, Object>>) option.get("series");
    }

    static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
            map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
