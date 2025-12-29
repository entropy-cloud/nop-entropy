package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartBarDirection;
import io.nop.excel.chart.constants.ChartBarGrouping;
import io.nop.excel.chart.model.ChartAreaConfigModel;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.excel.chart.model.ChartBubbleConfigModel;
import io.nop.excel.chart.model.ChartDoughnutConfigModel;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.excel.chart.model.ChartPieConfigModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartRadarConfigModel;
import io.nop.excel.chart.model.ChartScatterConfigModel;
import io.nop.excel.chart.model.ChartStockConfigModel;
import io.nop.excel.chart.model.ChartSurfaceConfigModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartTypeConfigParser - 图表类型特定配置解析器
 * 负责解析各种图表类型的特定配置，如柱状图、饼图、折线图等的专有属性
 */
public class ChartTypeConfigParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartTypeConfigParser.class);
    public static final ChartTypeConfigParser INSTANCE = new ChartTypeConfigParser();

    /**
     * 解析图表类型特定配置
     *
     * @param plotArea     绘图区域模型
     * @param plotAreaNode 绘图区域节点
     */
    public void parseChartTypeSpecificConfig(ChartPlotAreaModel plotArea, XNode plotAreaNode) {
        if (plotArea == null || plotAreaNode == null) {
            return;
        }

        try {
            // 检测并解析柱状图配置
            XNode barChartNode = plotAreaNode.childByTag("c:barChart");
            if (barChartNode == null) {
                barChartNode = plotAreaNode.childByTag("c:bar3DChart");
            }
            if (barChartNode != null) {
                parseBarChartConfig(plotArea, barChartNode);
                return;
            }

            // 检测并解析饼图配置
            XNode pieChartNode = plotAreaNode.childByTag("c:pieChart");
            if (pieChartNode == null) {
                pieChartNode = plotAreaNode.childByTag("c:pie3DChart");
            }
            if (pieChartNode != null) {
                parsePieChartConfig(plotArea, pieChartNode);
                return;
            }

            // 检测并解析折线图配置
            XNode lineChartNode = plotAreaNode.childByTag("c:lineChart");
            if (lineChartNode == null) {
                lineChartNode = plotAreaNode.childByTag("c:line3DChart");
            }
            if (lineChartNode != null) {
                parseLineChartConfig(plotArea, lineChartNode);
                return;
            }

            // 检测并解析面积图配置
            XNode areaChartNode = plotAreaNode.childByTag("c:areaChart");
            if (areaChartNode == null) {
                areaChartNode = plotAreaNode.childByTag("c:area3DChart");
            }
            if (areaChartNode != null) {
                parseAreaChartConfig(plotArea, areaChartNode);
                return;
            }

            // 其他图表类型的处理
            parseOtherChartTypes(plotArea, plotAreaNode);

        } catch (Exception e) {
            LOG.warn("Failed to parse chart type specific configuration", e);
        }
    }

    /**
     * 解析柱状图特定配置
     */
    private void parseBarChartConfig(ChartPlotAreaModel plotArea, XNode barChartNode) {

        ChartBarConfigModel barConfig = new ChartBarConfigModel();

        // 解析柱状图方向
        String barDir = ChartPropertyHelper.getChildVal(barChartNode, "c:barDir");
        if (!StringHelper.isEmpty(barDir)) {
            ChartBarDirection direction = ChartBarDirection.fromValue(barDir);
            if (direction != null) {
                barConfig.setDir(direction);
                LOG.debug("Bar chart direction: {}", direction);
            }
        }

        // 解析分组方式
        String grouping = ChartPropertyHelper.getChildVal(barChartNode, "c:grouping");
        if (!StringHelper.isEmpty(grouping)) {
            ChartBarGrouping groupingEnum = ChartBarGrouping.fromValue(grouping);
            if (groupingEnum != null) {
                barConfig.setGrouping(groupingEnum);
                LOG.debug("Bar chart grouping: {}", groupingEnum);
            }
        }

        // 解析间隙宽度
        Integer gapWidth = ChartPropertyHelper.getChildIntVal(barChartNode, "c:gapWidth");
        if (gapWidth != null) {
            barConfig.setGapWidth(gapWidth);
            LOG.debug("Bar chart gap width: {}%", gapWidth);
        }

        // 解析重叠比例
        Integer overlap = ChartPropertyHelper.getChildIntVal(barChartNode, "c:overlap");
        if (overlap != null) {
            barConfig.setOverlap(overlap);
            LOG.debug("Bar chart overlap: {}%", overlap);
        }

        // 设置到plotArea
        plotArea.setBarConfig(barConfig);

    }

    /**
     * 解析饼图特定配置
     */
    private void parsePieChartConfig(ChartPlotAreaModel plotArea, XNode pieChartNode) {

        ChartPieConfigModel pieConfig = new ChartPieConfigModel();

        // 解析饼图是否分离 - 注意：varyColors不是饼图配置的一部分，而是系列级别的属性
        Boolean varyColorsBool = ChartPropertyHelper.getChildBoolVal(pieChartNode, "c:varyColors");
        if (varyColorsBool != null) {
            LOG.debug("Pie chart vary colors: {} (note: this is a series-level property)", varyColorsBool);
        }

        // 解析第一个扇区角度
        Double firstSliceAng = ChartPropertyHelper.getChildAngleVal(pieChartNode, "c:firstSliceAng");
        if (firstSliceAng != null) {
            pieConfig.setStartAngle(firstSliceAng);
            LOG.debug("Pie chart first slice angle: {}°", firstSliceAng);
        }

        // 解析饼图分离程度 - 这通常是系列级别的属性，但记录下来
        String explosion = ChartPropertyHelper.getChildVal(pieChartNode, "c:explosion");
        if (!StringHelper.isEmpty(explosion)) {
            LOG.debug("Pie chart explosion: {} (note: this is typically a series-level property)", explosion);
        }

        // 设置到plotArea
        plotArea.setPieConfig(pieConfig);

    }

    /**
     * 解析折线图特定配置
     */
    private void parseLineChartConfig(ChartPlotAreaModel plotArea, XNode lineChartNode) {

        ChartLineConfigModel lineConfig = new ChartLineConfigModel();

        // 解析分组方式
        String grouping = ChartPropertyHelper.getChildVal(lineChartNode, "c:grouping");
        if (!StringHelper.isEmpty(grouping)) {
            ChartBarGrouping groupingEnum = ChartBarGrouping.fromValue(grouping);
            if (groupingEnum != null) {
                lineConfig.setGrouping(groupingEnum);
                LOG.debug("Line chart grouping: {}", groupingEnum);
            }
        }

        // 解析是否显示标记
        Boolean marker = ChartPropertyHelper.getChildBoolVal(lineChartNode, "c:marker");
        if (marker != null) {
            lineConfig.setMarker(marker);
            LOG.debug("Line chart marker: {}", marker);
        }

        // 解析是否平滑曲线
        Boolean smooth = ChartPropertyHelper.getChildBoolVal(lineChartNode, "c:smooth");
        if (smooth != null) {
            lineConfig.setSmooth(smooth);
            LOG.debug("Line chart smooth: {}", smooth);
        }

        // 解析是否显示垂直线到X轴
        Boolean dropLines = ChartPropertyHelper.getChildBoolVal(lineChartNode, "c:dropLines");
        if (dropLines != null) {
            lineConfig.setDropLines(dropLines);
            LOG.debug("Line chart drop lines: {}", dropLines);
        }

        // 解析是否显示高低线
        Boolean hiLowLines = ChartPropertyHelper.getChildBoolVal(lineChartNode, "c:hiLowLines");
        if (hiLowLines != null) {
            lineConfig.setHiLowLines(hiLowLines);
            LOG.debug("Line chart hi-low lines: {}", hiLowLines);
        }

        // 解析是否显示涨跌柱
        Boolean upDownBars = ChartPropertyHelper.getChildBoolVal(lineChartNode, "c:upDownBars");
        if (upDownBars != null) {
            lineConfig.setUpDownBars(upDownBars);
            LOG.debug("Line chart up-down bars: {}", upDownBars);
        }

        // 设置到plotArea
        plotArea.setLineConfig(lineConfig);

    }

    /**
     * 解析面积图特定配置
     */
    private void parseAreaChartConfig(ChartPlotAreaModel plotArea, XNode areaChartNode) {

        ChartAreaConfigModel areaConfig = new ChartAreaConfigModel();

        // 解析分组方式
        String grouping = ChartPropertyHelper.getChildVal(areaChartNode, "c:grouping");
        if (!StringHelper.isEmpty(grouping)) {
            ChartBarGrouping groupingEnum = ChartBarGrouping.fromValue(grouping);
            if (groupingEnum != null) {
                areaConfig.setGrouping(groupingEnum);
                LOG.debug("Area chart grouping: {}", groupingEnum);
            }
        }

        // 解析是否显示垂直线到X轴
        Boolean dropLines = ChartPropertyHelper.getChildBoolVal(areaChartNode, "c:dropLines");
        if (dropLines != null) {
            areaConfig.setDropLines(dropLines);
            LOG.debug("Area chart drop lines: {}", dropLines);
        }

        // 设置到plotArea
        plotArea.setAreaConfig(areaConfig);

    }

    /**
     * 解析其他图表类型
     */
    private void parseOtherChartTypes(ChartPlotAreaModel plotArea, XNode plotAreaNode) {

        // 散点图
        XNode scatterNode = plotAreaNode.childByTag("c:scatterChart");
        if (scatterNode != null) {
            parseScatterChartConfig(plotArea, scatterNode);
            return;
        }

        // 气泡图
        XNode bubbleNode = plotAreaNode.childByTag("c:bubbleChart");
        if (bubbleNode != null) {
            parseBubbleChartConfig(plotArea, bubbleNode);
            return;
        }

        // 雷达图
        XNode radarNode = plotAreaNode.childByTag("c:radarChart");
        if (radarNode != null) {
            parseRadarChartConfig(plotArea, radarNode);
            return;
        }

        // 环形图 - 使用饼图配置模型
        XNode doughnutNode = plotAreaNode.childByTag("c:doughnutChart");
        if (doughnutNode != null) {
            parseDoughnutChartConfig(plotArea, doughnutNode);
            return;
        }

        // 曲面图
        XNode surfaceNode = plotAreaNode.childByTag("c:surfaceChart");
        if (surfaceNode == null) {
            surfaceNode = plotAreaNode.childByTag("c:surface3DChart");
        }
        if (surfaceNode != null) {
            parseSurfaceChartConfig(plotArea, surfaceNode);
            return;
        }

        // 股价图
        XNode stockNode = plotAreaNode.childByTag("c:stockChart");
        if (stockNode != null) {
            parseStockChartConfig(plotArea, stockNode);
            return;
        }

    }

    /**
     * 解析散点图特定配置
     */
    private void parseScatterChartConfig(ChartPlotAreaModel plotArea, XNode scatterNode) {

        ChartScatterConfigModel scatterConfig = new ChartScatterConfigModel();

        // 解析散点图样式
        String scatterStyle = ChartPropertyHelper.getChildVal(scatterNode, "c:scatterStyle");
        if (!StringHelper.isEmpty(scatterStyle)) {
            scatterConfig.setScatterStyle(scatterStyle);
            LOG.debug("Scatter chart style: {}", scatterStyle);
        }

        // 解析是否显示标记点
        Boolean showMarkers = ChartPropertyHelper.getChildBoolVal(scatterNode, "c:marker");
        if (showMarkers != null) {
            scatterConfig.setShowMarkers(showMarkers);
            LOG.debug("Scatter chart show markers: {}", showMarkers);
        }

        // 设置到plotArea
        plotArea.setScatterConfig(scatterConfig);

    }

    /**
     * 解析气泡图特定配置
     */
    private void parseBubbleChartConfig(ChartPlotAreaModel plotArea, XNode bubbleNode) {

        ChartBubbleConfigModel bubbleConfig = new ChartBubbleConfigModel();

        // 解析气泡大小表示
        Boolean bubble3D = ChartPropertyHelper.getChildBoolVal(bubbleNode, "c:bubble3D");
        if (bubble3D != null) {
            bubbleConfig.setBubble3D(bubble3D);
            LOG.debug("Bubble chart 3D: {}", bubble3D);
        }

        // 解析气泡缩放
        Double bubbleScale = ChartPropertyHelper.getChildDoubleVal(bubbleNode, "c:bubbleScale");
        if (bubbleScale != null) {
            bubbleConfig.setBubbleScale(bubbleScale);
            LOG.debug("Bubble chart scale: {}", bubbleScale);
        }

        // 解析气泡大小表示方式
        String sizeRepresents = ChartPropertyHelper.getChildVal(bubbleNode, "c:sizeRepresents");
        if (!StringHelper.isEmpty(sizeRepresents)) {
            bubbleConfig.setSizeRepresents(sizeRepresents);
            LOG.debug("Bubble chart size represents: {}", sizeRepresents);
        }

        // 设置到plotArea
        plotArea.setBubbleConfig(bubbleConfig);

    }

    /**
     * 解析雷达图特定配置
     */
    private void parseRadarChartConfig(ChartPlotAreaModel plotArea, XNode radarNode) {

        ChartRadarConfigModel radarConfig = new ChartRadarConfigModel();

        // 解析雷达图样式
        String radarStyle = ChartPropertyHelper.getChildVal(radarNode, "c:radarStyle");
        if (!StringHelper.isEmpty(radarStyle)) {
            LOG.debug("Radar chart style: {}", radarStyle);
        }

        // 解析起始角度
        Double startAngle = ChartPropertyHelper.getChildAngleVal(radarNode, "c:startAngle");
        if (startAngle != null) {
            radarConfig.setStartAngle(startAngle);
            LOG.debug("Radar chart start angle: {}°", startAngle);
        }

        // 解析结束角度
        Double endAngle = ChartPropertyHelper.getChildAngleVal(radarNode, "c:endAngle");
        if (endAngle != null) {
            radarConfig.setEndAngle(endAngle);
            LOG.debug("Radar chart end angle: {}°", endAngle);
        }

        // 解析半径
        Double radius = ChartPropertyHelper.getChildDoubleVal(radarNode, "c:radius");
        if (radius != null) {
            radarConfig.setRadius(radius);
            LOG.debug("Radar chart radius: {}%", radius);
        }

        // 设置到plotArea
        plotArea.setRadarConfig(radarConfig);

    }

    /**
     * 解析环形图特定配置
     */
    private void parseDoughnutChartConfig(ChartPlotAreaModel plotArea, XNode doughnutNode) {

        ChartDoughnutConfigModel doughnutConfig = new ChartDoughnutConfigModel();

        // 检测是否为3D环形图
        Boolean is3D = doughnutNode.childByTag("c:doughnut3DChart") != null;
        if (is3D) {
            doughnutConfig.setIs3D(true);
            LOG.debug("Doughnut chart is 3D");
        }

        // 解析颜色变化
        Boolean varyColors = ChartPropertyHelper.getChildBoolVal(doughnutNode, "c:varyColors");
        if (varyColors != null) {
            doughnutConfig.setVaryColors(varyColors);
            LOG.debug("Doughnut chart vary colors: {}", varyColors);
        }

        // 解析第一个扇区角度
        Double firstSliceAng = ChartPropertyHelper.getChildAngleVal(doughnutNode, "c:firstSliceAng");
        if (firstSliceAng != null) {
            doughnutConfig.setStartAngle(firstSliceAng);
            LOG.debug("Doughnut chart first slice angle: {}°", firstSliceAng);
        }

        // 解析内半径
        Double holeSize = ChartPropertyHelper.getChildDoubleVal(doughnutNode, "c:holeSize");
        if (holeSize != null) {
            doughnutConfig.setHoleSize(holeSize);
            LOG.debug("Doughnut chart hole size: {}%", holeSize);
        }

        // 设置到plotArea
        plotArea.setDoughnutConfig(doughnutConfig);

    }

    /**
     * 解析曲面图特定配置
     */
    private void parseSurfaceChartConfig(ChartPlotAreaModel plotArea, XNode surfaceNode) {

        ChartSurfaceConfigModel surfaceConfig = new ChartSurfaceConfigModel();

        // 解析线框模式
        Boolean wireframe = ChartPropertyHelper.getChildBoolVal(surfaceNode, "c:wireframe");
        if (wireframe != null) {
            LOG.debug("Surface chart wireframe: {}", wireframe);
        }

        // 设置到plotArea
        plotArea.setSurfaceConfig(surfaceConfig);

    }

    /**
     * 解析股价图特定配置
     */
    private void parseStockChartConfig(ChartPlotAreaModel plotArea, XNode stockNode) {

        ChartStockConfigModel stockConfig = new ChartStockConfigModel();

        // 解析高低线
        Boolean hiLowLines = ChartPropertyHelper.getChildBoolVal(stockNode, "c:hiLowLines");
        if (hiLowLines != null) {
            LOG.debug("Stock chart hi-low lines: {}", hiLowLines);
        }

        // 解析涨跌线
        Boolean upDownBars = ChartPropertyHelper.getChildBoolVal(stockNode, "c:upDownBars");
        if (upDownBars != null) {
            LOG.debug("Stock chart up-down bars: {}", upDownBars);
        }

        // 设置到plotArea
        plotArea.setStockConfig(stockConfig);

    }
}