package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
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

        // 添加根级别的样式配置
        addRootStyles(chartSpaceNode, chartModel);

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
    protected void addStyleSettings(XNode chartSpaceNode, ChartModel chartModel) {
        // 创建 mc:AlternateContent 节点
        XNode alternateContentNode = chartSpaceNode.addChild("mc:AlternateContent");
        alternateContentNode.setAttr("xmlns:mc", "http://schemas.openxmlformats.org/markup-compatibility/2006");

        // 创建 mc:Choice 节点
        XNode choiceNode = alternateContentNode.addChild("mc:Choice");
        choiceNode.setAttr("Requires", "c14");
        choiceNode.setAttr("xmlns:c14", "http://schemas.microsoft.com/office/drawing/2007/8/2/chart");

        // 创建 c14:style 节点
        XNode c14StyleNode = choiceNode.addChild("c14:style");
        c14StyleNode.setAttr("val", "102");

        // 创建 mc:Fallback 节点
        XNode fallbackNode = alternateContentNode.addChild("mc:Fallback");

        // 创建 c:style 节点
        XNode cStyleNode = fallbackNode.addChild("c:style");
        cStyleNode.setAttr("val", "2");
    }

    /**
     * 添加根级别的样式配置
     * 对应chart.xdef中根节点的shapeStyle和textStyle
     */
    private void addRootStyles(XNode chartSpaceNode, ChartModel chartModel) {
        // 添加chartSpace级别的形状样式
        if (chartModel.getShapeStyle() != null) {
            XNode spPrNode = ChartShapeStyleBuilder.INSTANCE.buildShapeStyle(chartModel.getShapeStyle());
            if (spPrNode != null) {
                chartSpaceNode.appendChild(spPrNode.withTagName("c:spPr"));
                LOG.debug("Added root level shape style");
            }
        }

        // 添加chartSpace级别的文本样式
        if (chartModel.getTextStyle() != null) {
            XNode txPrNode = ChartTextStyleBuilder.INSTANCE.buildTextStyle(chartModel.getTextStyle());
            if (txPrNode != null) {
                chartSpaceNode.appendChild(txPrNode.withTagName("c:txPr"));
                LOG.debug("Added root level text style");
            }
        }
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
        if (title == null) {
            ChartPropertyHelper.setChildVal(chartNode, "c:autoTitleDeleted", "1");
            return;
        }

        ChartPropertyHelper.setChildVal(chartNode, "c:autoTitleDeleted", title.isVisible() ? "0" : "1");

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
        if(chartModel.getPlotVisOnly() != null){
            ChartPropertyHelper.setChildBoolVal(chartNode, "c:plotVisOnly", chartModel.getPlotVisOnly());
        }
        if (chartModel.getDispBlanksAs() != null) {
            ChartPropertyHelper.setChildVal(chartNode, "c:dispBlanksAs", chartModel.getDispBlanksAs().toString());
        }

        if (chartModel.getShowLabelsOverMax() != null) {
            ChartPropertyHelper.setChildBoolVal(chartNode, "c:showDLblsOverMax",
                    chartModel.getShowLabelsOverMax());
        }
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
