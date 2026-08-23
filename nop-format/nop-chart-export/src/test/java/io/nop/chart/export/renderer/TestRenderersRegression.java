package io.nop.chart.export.renderer;

import io.nop.excel.chart.model.ChartHeatmapConfigModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.util.ChartDataSet;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.data.xy.XYZDataset;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归：null 数据点不能再以 (0,0) 占位渲染；最小化模型（无 plotArea 配置）不能 NPE；
 * 热力图必须设置 PaintScale
 */
public class TestRenderersRegression {

    @Test
    public void testBubbleSkipsNullPoints() {
        BubbleChartRenderer renderer = new BubbleChartRenderer();
        ChartDataSet ds = new ChartDataSet();
        ds.setXValues(Arrays.asList(1.0, null, 3.0));
        ds.setValues(Arrays.asList(10.0, 20.0, null));
        ds.setBubbleSizes(Arrays.asList(1.0, 2.0, 3.0));

        JFreeChart chart = renderer.createChart(new ChartModel(), Collections.singletonList(ds), null);
        XYZDataset dataset = (XYZDataset) chart.getXYPlot().getDataset();
        // 只有第0个点三值均非null，其余null点必须被跳过而非以0.0占位
        assertEquals(1, dataset.getItemCount(0));
        assertEquals(1.0, dataset.getXValue(0, 0), 1e-9);
        assertEquals(10.0, dataset.getYValue(0, 0), 1e-9);
    }

    @Test
    public void testHeatmapSkipsNullPointsAndSetsPaintScale() {
        HeatmapChartRenderer renderer = new HeatmapChartRenderer();
        ChartDataSet ds = new ChartDataSet();
        ds.setXValues(Arrays.asList(1.0, null, 3.0));
        ds.setValues(Arrays.asList(10.0, 20.0, 30.0));
        ds.setHeatmapValues(Arrays.asList(100.0, 200.0, 300.0));

        ChartModel model = new ChartModel();
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        plotArea.setHeatmapConfig(new ChartHeatmapConfigModel());
        model.setPlotArea(plotArea);

        JFreeChart chart = renderer.createChart(model, Collections.singletonList(ds), null);
        XYPlot plot = chart.getXYPlot();
        XYZDataset dataset = (XYZDataset) plot.getDataset();
        // x值为null的第2个点被跳过，其余2个点保留
        assertEquals(2, dataset.getItemCount(0));

        // 修复前从不设置 PaintScale，所有热块渲染为同一默认颜色
        XYBlockRenderer blockRenderer = (XYBlockRenderer) plot.getRenderer();
        assertNotNull(blockRenderer.getPaintScale());
        LookupPaintScale scale = (LookupPaintScale) blockRenderer.getPaintScale();
        assertEquals(300.0, scale.getUpperBound(), 1e-9);
    }

    @Test
    public void testBarMinimalModelNoNpe() {
        BarChartRenderer renderer = new BarChartRenderer();
        ChartDataSet ds = new ChartDataSet();
        ds.setCategories(Arrays.asList("A", "B"));
        ds.setValues(Arrays.asList(1.0, 2.0));

        // 修复前 getPlotArea() 直接解引用抛 NPE
        JFreeChart chart = renderer.createChart(new ChartModel(), Collections.singletonList(ds), null);
        assertNotNull(chart.getCategoryPlot().getDataset());
        assertEquals(2, chart.getCategoryPlot().getDataset().getColumnCount());
    }
}
