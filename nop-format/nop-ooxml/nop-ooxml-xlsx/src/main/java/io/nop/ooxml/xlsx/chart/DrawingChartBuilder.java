package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartTitleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DrawingChartBuilder - Excel图表构建器
 * 负责将ChartModel对象转换为OOXML chartSpace XML结构
 * 协调各个子构建器完成完整的图表XML生成
 */
public class DrawingChartBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingChartBuilder.class);
    public static final DrawingChartBuilder INSTANCE = new DrawingChartBuilder();

    /**
     * 构建图表XML
     *
     * @param chartModel 图表模型
     * @return 完整的chartSpace XML节点
     */
    public XNode build(ChartModel chartModel) {
        if (chartModel == null) {
            LOG.warn("Chart model is null, generating minimal chartSpace");
            return buildMinimalChartSpace();
        }


        return buildChartSpace(chartModel);

    }

    /**
     * 构建完整的chartSpace结构
     *
     * @param chartModel 图表模型
     * @return chartSpace XML节点
     */
    public XNode buildChartSpace(ChartModel chartModel) {
        if (chartModel == null) {
            LOG.warn("Chart model is null, generating minimal chartSpace");
            return buildMinimalChartSpace();
        }


        // 创建根chartSpace节点
        XNode chartSpaceNode = createChartSpaceRoot();
        chartSpaceNode.makeChild("c:roundedCorners")
                .withAttr("val", Boolean.TRUE.equals(chartModel.getRoundedCorners()) ? 1 : 0);

        // 添加语言设置
        addLanguageSettings(chartSpaceNode, chartModel.getLang());

        // 添加样式设置
        addStyleSettings(chartSpaceNode, chartModel);

        // 创建chart节点
        XNode chartNode = chartSpaceNode.addChild("c:chart");

        // 构建图表组件
        buildChartComponents(chartNode, chartModel);

        LOG.debug("Successfully built chartSpace XML for chart type: {}", chartModel.getType());
        return chartSpaceNode;

    }

    /**
     * 创建chartSpace根节点并设置命名空间
     */
    private XNode createChartSpaceRoot() {
        XNode chartSpaceNode = XNode.make("c:chartSpace");

        // 设置OOXML命名空间
        chartSpaceNode.setAttr("xmlns:c", "http://schemas.openxmlformats.org/drawingml/2006/chart");
        chartSpaceNode.setAttr("xmlns:a", "http://schemas.openxmlformats.org/drawingml/2006/main");
        chartSpaceNode.setAttr("xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");

        chartSpaceNode.addChild("c:date1904").withAttr("val", "0");
        return chartSpaceNode;
    }

    /**
     * 添加语言设置
     */
    private void addLanguageSettings(XNode chartSpaceNode, String lang) {

        XNode langNode = chartSpaceNode.addChild("c:lang");
        langNode.setAttr("val", StringHelper.toString(lang, "en-US"));

    }

    /**
     * 添加样式设置
     */
    private void addStyleSettings(XNode chartSpaceNode, ChartModel chartModel) {

        String styleId = chartModel.getStyleId();
        if (StringHelper.isEmpty(styleId)) {
            styleId = "2"; // 默认样式ID
        }

        XNode styleNode = chartSpaceNode.addChild("c:style");
        styleNode.setAttr("val", styleId);

    }

    /**
     * 构建图表组件
     */
    private void buildChartComponents(XNode chartNode, ChartModel chartModel) {

        // 构建标题
        buildTitle(chartNode, chartModel.getTitle());

        // 构建图例
        buildLegend(chartNode, chartModel.getLegend());

        // 构建绘图区域
        buildPlotArea(chartNode, chartModel.getPlotArea());

        // 添加基本属性
        addBasicProperties(chartNode, chartModel);

    }

    /**
     * 构建标题
     */
    private void buildTitle(XNode chartNode, ChartTitleModel title) {
        if (title == null || !title.isVisible()) {
            LOG.debug("Title is null or not visible, skipping title generation");
            return;
        }


        XNode titleNode = ChartTitleBuilder.INSTANCE.buildTitle(title);
        if (titleNode != null) {
            chartNode.appendChild(titleNode);
            LOG.debug("Built title: {}", title.getText());
        }

    }

    /**
     * 构建图例
     */
    private void buildLegend(XNode chartNode, ChartLegendModel legend) {
        if (legend == null || !legend.isVisible()) {
            LOG.debug("Legend is null or not visible, skipping legend generation");
            return;
        }


        XNode legendNode = ChartLegendBuilder.INSTANCE.buildLegend(legend);
        if (legendNode != null) {
            chartNode.appendChild(legendNode);
            LOG.debug("Built legend with position: {}", legend.getPosition());
        }

    }

    /**
     * 构建绘图区域
     */
    private void buildPlotArea(XNode chartNode, ChartPlotAreaModel plotArea) {
        if (plotArea == null) {
            LOG.debug("PlotArea is null, skipping plot area generation");
            return;
        }


        XNode plotAreaNode = ChartPlotAreaBuilder.INSTANCE.buildPlotArea(plotArea);
        if (plotAreaNode != null) {
            chartNode.appendChild(plotAreaNode);
            LOG.debug("Built plot area with {} series and {} axes",
                    plotArea.getSeriesList() != null ? plotArea.getSeriesList().size() : 0,
                    plotArea.getAxes() != null ? plotArea.getAxes().size() : 0);
        }

    }

    /**
     * 添加基本属性
     */
    private void addBasicProperties(XNode chartNode, ChartModel chartModel) {

    }

    /**
     * 生成最小的chartSpace结构
     */
    private XNode buildMinimalChartSpace() {

        XNode chartSpaceNode = createChartSpaceRoot();
        addLanguageSettings(chartSpaceNode, null);

        // 添加默认样式
        XNode styleNode = chartSpaceNode.addChild("c:style");
        styleNode.setAttr("val", "2");

        // 添加基本chart节点
        XNode chartNode = chartSpaceNode.addChild("c:chart");
        XNode plotAreaNode = chartNode.addChild("c:plotArea");

        LOG.debug("Generated minimal chartSpace structure");
        return chartSpaceNode;

    }

    /**
     * 构建简单的图表（便利方法）
     *
     * @param title     图表标题
     * @param chartType 图表类型
     * @return chartSpace XML节点
     */
    public XNode buildSimpleChart(String title, ChartType chartType) {

        ChartModel chartModel = new ChartModel();
        chartModel.setType(chartType);

        // 添加标题
        if (!StringHelper.isEmpty(title)) {
            ChartTitleModel titleModel = new ChartTitleModel();
            titleModel.setText(title);
            titleModel.setVisible(true);
            chartModel.setTitle(titleModel);
        }

        // 添加基本绘图区域
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        chartModel.setPlotArea(plotArea);

        return buildChartSpace(chartModel);

    }

    /**
     * 构建带有数据的图表（便利方法）
     *
     * @param chartModel 完整的图表模型
     * @return chartSpace XML节点
     */
    public XNode buildChart(ChartModel chartModel) {
        return build(chartModel);
    }

    /**
     * 验证图表模型的完整性
     *
     * @param chartModel 图表模型
     * @return 验证结果
     */
    public boolean validateChartModel(ChartModel chartModel) {
        if (chartModel == null) {
            LOG.warn("Chart model is null");
            return false;
        }


        // 检查基本属性
        if (chartModel.getType() == null) {
            LOG.warn("Chart type is not specified");
        }

        // 检查绘图区域
        if (chartModel.getPlotArea() == null) {
            LOG.warn("Plot area is not specified");
            return false;
        }

        // 检查系列数据
        if (chartModel.getPlotArea().getSeriesList() == null ||
                chartModel.getPlotArea().getSeriesList().isEmpty()) {
            LOG.warn("No series data found in plot area");
        }

        return true;

    }

    /**
     * 构建图表锚点的图表内容部分
     * 此方法专门用于与DrawingBuilder配合，生成嵌入到锚点中的图表XML
     *
     * @param chartModel     图表模型
     * @param relationshipId 关系ID
     * @return 图表引用节点
     */
    public XNode buildChartReference(ChartModel chartModel, String relationshipId) {

        // 创建图表引用节点
        XNode chartRef = XNode.make("c:chart");
        chartRef.setAttr("xmlns:c", "http://schemas.openxmlformats.org/drawingml/2006/chart");
        chartRef.setAttr("xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");
        chartRef.setAttr("r:id", relationshipId);

        LOG.debug("Built chart reference with relationship ID: {}", relationshipId);
        return chartRef;

    }
}
