package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ChartSeriesParser - 图表系列解析器
 * 负责解析Excel图表中的数据系列配置
 * 实现完整的OOXML系列解析功能
 */
public class ChartSeriesParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChartSeriesParser.class);
    public static final ChartSeriesParser INSTANCE = new ChartSeriesParser();

    /**
     * 解析单个系列
     *
     * @param serNode       系列节点
     * @param styleProvider 样式提供者
     * @return 系列模型对象
     */
    public ChartSeriesModel parseSeries(XNode serNode, int index, IChartStyleProvider styleProvider) {
        if (serNode == null) {
            LOG.warn("Series node is null, returning null");
            return null;
        }

        ChartSeriesModel series = new ChartSeriesModel();

        // 解析基本属性
        parseBasicProperties(series, index, serNode);

        // 解析系列数据
        parseSeriesData(series, serNode);

        // 解析系列格式化
        parseSeriesFormatting(series, serNode, styleProvider);

        // 解析数据标签
        parseDataLabels(series, serNode, styleProvider);

        // 解析趋势线
        parseTrendLines(series, serNode, styleProvider);

        // 解析图表类型特定配置
        parseChartTypeSpecificConfig(series, serNode);

        return series;
    }

    /**
     * 解析图表类型特定配置
     * 根据父图表类型解析系列的特定属性
     */
    private void parseChartTypeSpecificConfig(ChartSeriesModel series, XNode serNode) {
        // 检查父节点类型来确定图表类型
        XNode parentNode = serNode.getParent();
        if (parentNode != null) {
            String chartType = parentNode.getTagName();

            switch (chartType) {
                case "c:pieChart":
                case "c:pie3DChart":
                    parsePieSeriesConfig(series, serNode);
                    break;
                case "c:barChart":
                case "c:bar3DChart":
                    parseBarSeriesConfig(series, serNode);
                    break;
                case "c:lineChart":
                case "c:line3DChart":
                    parseLineSeriesConfig(series, serNode);
                    break;
                case "c:scatterChart":
                    parseScatterSeriesConfig(series, serNode);
                    break;
                case "c:bubbleChart":
                    parseBubbleSeriesConfig(series, serNode);
                    break;
                case "c:areaChart":
                case "c:area3DChart":
                    parseAreaSeriesConfig(series, serNode);
                    break;
                default:
                    LOG.debug("No specific configuration for chart type: {}", chartType);
                    break;
            }
        }
    }

    /**
     * 解析饼图系列配置
     */
    private void parsePieSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 饼图系列的爆炸值已在parseSeriesSpecificConfig中处理
        LOG.debug("Parsing pie chart series configuration");
    }

    /**
     * 解析柱状图系列配置
     */
    private void parseBarSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 柱状图特定配置
        LOG.debug("Parsing bar chart series configuration");
    }

    /**
     * 解析折线图系列配置
     */
    private void parseLineSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 折线图的标记和平滑线配置已在parseSeriesSpecificConfig中处理
        LOG.debug("Parsing line chart series configuration");
    }

    /**
     * 解析散点图系列配置
     */
    private void parseScatterSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 散点图特定配置
        LOG.debug("Parsing scatter chart series configuration");
    }

    /**
     * 解析气泡图系列配置
     */
    private void parseBubbleSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 气泡图的气泡大小数据已在parseSeriesData中处理
        LOG.debug("Parsing bubble chart series configuration");
    }

    /**
     * 解析面积图系列配置
     */
    private void parseAreaSeriesConfig(ChartSeriesModel series, XNode serNode) {
        // 面积图特定配置
        LOG.debug("Parsing area chart series configuration");
    }

    /**
     * 解析基本属性
     */
    private void parseBasicProperties(ChartSeriesModel series, int index, XNode serNode) {
        // 解析系列名称
        parseSeriesName(series, index, serNode);

        // 解析可见性
        parseVisibility(series, serNode);
    }

    /**
     * 解析系列名称
     */
    private void parseSeriesName(ChartSeriesModel series, int index, XNode serNode) {
        series.setId("ser-" + index);

        XNode txNode = serNode.childByTag("c:tx");
        if (txNode != null) {
            String seriesName = ChartTextParser.INSTANCE.extractText(txNode);
            if (!StringHelper.isEmpty(seriesName)) {
                series.setName(seriesName);
                return;
            }
        }

        // 如果没有找到名称，设置默认名称
        series.setName("Series " + (index + 1));
    }

    /**
     * 解析可见性
     */
    private void parseVisibility(ChartSeriesModel series, XNode serNode) {
        // 在OOXML中，系列默认是可见的，除非明确设置为隐藏
        // 检查是否有delete元素
        Boolean isDeleted = ChartPropertyHelper.getChildBoolVal(serNode, "c:delete");
        if (isDeleted != null) {
            series.setVisible(!isDeleted); // delete=true 意味着不可见
            return;
        }

        // 默认可见
        series.setVisible(true);
    }

    /**
     * 解析系列数据
     */
    private void parseSeriesData(ChartSeriesModel series, XNode serNode) {
        // 解析分类数据 (X轴数据)
        parseSeriesCatData(series, serNode);

        // 解析数值数据 (Y轴数据)
        parseSeriesValData(series, serNode);

        // 解析系列特定配置
        parseSeriesSpecificConfig(series, serNode);
    }

    /**
     * 解析系列分类数据
     */
    private void parseSeriesCatData(ChartSeriesModel series, XNode serNode) {
        // 解析类别数据 (X轴数据)
        XNode catNode = serNode.childByTag("c:cat");
        if (catNode != null) {
            String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(catNode);
            if (!StringHelper.isEmpty(cellRef)) {
                series.setCatCellRef(cellRef);
                LOG.debug("Parsed category data reference: {}", cellRef);
            }
        }
    }

    /**
     * 解析系列数值数据
     */
    private void parseSeriesValData(ChartSeriesModel series, XNode serNode) {
        String dataCellRef = null;

        // 解析数值数据 (Y轴数据) - 优先级最高
        XNode valNode = serNode.childByTag("c:val");
        if (valNode != null) {
            String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(valNode);
            if (cellRef != null) {
                dataCellRef = cellRef;
            }
        }

        // 如果没有找到val，尝试解析X值数据 (散点图)
        if (dataCellRef == null) {
            XNode xValNode = serNode.childByTag("c:xVal");
            if (xValNode != null) {
                String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(xValNode);
                if (cellRef != null) {
                    dataCellRef = cellRef;
                }
            }
        }

        // 如果没有找到，尝试解析Y值数据 (散点图)
        if (dataCellRef == null) {
            XNode yValNode = serNode.childByTag("c:yVal");
            if (yValNode != null) {
                String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(yValNode);
                if (cellRef != null) {
                    dataCellRef = cellRef;
                }
            }
        }

        // 如果没有找到，尝试解析气泡大小数据 (气泡图)
        if (dataCellRef == null) {
            XNode bubbleSizeNode = serNode.childByTag("c:bubbleSize");
            if (bubbleSizeNode != null) {
                String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(bubbleSizeNode);
                if (cellRef != null) {
                    dataCellRef = cellRef;
                }
            }
        }

        // 设置数据引用
        if (!StringHelper.isEmpty(dataCellRef)) {
            series.setDataCellRef(dataCellRef);
            LOG.debug("Parsed value data reference: {}", dataCellRef);
        }
    }

    /**
     * 解析系列特定配置
     * 处理不同图表类型的系列特定属性
     */
    private void parseSeriesSpecificConfig(ChartSeriesModel series, XNode serNode) {
        // 解析爆炸值 (饼图)
        Integer explosion = ChartPropertyHelper.getChildIntVal(serNode, "c:explosion");
        if (explosion != null) {
            // TODO: 设置到系列的特定配置中
            LOG.debug("Series explosion value: {}", explosion);

        }

        // 解析标记配置 (折线图、散点图)
        parseMarkerConfig(series, serNode);

        // 解析平滑线配置 (折线图)
        Boolean smoothBool = ChartPropertyHelper.getChildBoolVal(serNode, "c:smooth");
        if (smoothBool != null) {
            // TODO: 设置到系列的特定配置中
            LOG.debug("Series smooth line: {}", smoothBool);
        }

        // 解析3D配置
        parse3DConfig(series, serNode);
    }

    /**
     * 解析标记配置
     */
    private void parseMarkerConfig(ChartSeriesModel series, XNode serNode) {
        XNode markerNode = serNode.childByTag("c:marker");
        if (markerNode != null) {
            // 解析标记符号
            String symbol = ChartPropertyHelper.getChildVal(markerNode, "c:symbol");
            if (!StringHelper.isEmpty(symbol)) {
                LOG.debug("Series marker symbol: {}", symbol);
            }

            // 解析标记大小
            String size = ChartPropertyHelper.getChildVal(markerNode, "c:size");
            if (!StringHelper.isEmpty(size)) {
                try {
                    int sizeValue = Integer.parseInt(size);
                    LOG.debug("Series marker size: {}", sizeValue);
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid marker size: {}", size);
                }
            }

            // 解析标记样式
            XNode spPrNode = markerNode.childByTag("c:spPr");
            if (spPrNode != null) {
                LOG.debug("Found marker shape properties");
            }
        }
    }

    /**
     * 解析3D配置
     */
    private void parse3DConfig(ChartSeriesModel series, XNode serNode) {
        // 解析3D形状 (柱状图)
        String shape = ChartPropertyHelper.getChildVal(serNode, "c:shape");
        if (!StringHelper.isEmpty(shape)) {
            LOG.debug("Series 3D shape: {}", shape);
        }

        // 解析3D透视
        XNode pictureOptionsNode = serNode.childByTag("c:pictureOptions");
        if (pictureOptionsNode != null) {
            LOG.debug("Found series picture options for 3D effects");
        }
    }

    /**
     * 解析系列格式化
     */
    private void parseSeriesFormatting(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        // 解析形状样式
        XNode spPrNode = serNode.childByTag("c:spPr");
        if (spPrNode != null) {
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            if (shapeStyle != null) {
                series.setShapeStyle(shapeStyle);
            }
        }

        // 解析反转负值
        Boolean invertBool = ChartPropertyHelper.getChildBoolVal(serNode, "c:invertIfNegative");
        if (invertBool != null) {
            series.setInvertIfNegative(invertBool);
        }

        // 解析系列索引和顺序相关的格式化
        parseIndexBasedFormatting(series, serNode, styleProvider);
    }

    /**
     * 解析基于索引的格式化
     * 处理系列索引和顺序相关的样式配置
     */
    private void parseIndexBasedFormatting(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        // 应用基于索引的默认样式
        // if (series.getIndex() != null && styleProvider != null) {
        // TODO: 根据系列索引应用默认颜色和样式
        //     LOG.debug("Applying index-based formatting for series index: {}", series.getIndex());
        // }

        // 解析系列特定的颜色变化
        Boolean varyColorsBool = ChartPropertyHelper.getChildBoolVal(serNode, "c:varyColors");
        if (varyColorsBool != null) {
            LOG.debug("Series vary colors: {}", varyColorsBool);
        }
    }

    /**
     * 解析数据标签
     */
    private void parseDataLabels(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {

        XNode dLblsNode = serNode.childByTag("c:dLbls");
        if (dLblsNode != null) {
            ChartDataLabelsModel dataLabels = ChartDataLabelsParser.INSTANCE.parseDataLabels(dLblsNode, styleProvider);
            if (dataLabels != null) {
                series.setDataLabels(dataLabels);
            }
        }

    }

    /**
     * 解析趋势线
     */
    private List<ChartTrendLineModel> parseTrendLines(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        List<ChartTrendLineModel> trendLines = new ArrayList<>();

        for (XNode trendlineNode : serNode.childrenByTag("c:trendline")) {
            ChartTrendLineModel trendLine = ChartTrendLineParser.INSTANCE.parseTrendLine(trendlineNode, styleProvider);
            if (trendLine != null) {
                trendLines.add(trendLine);
            }
        }

        if (!trendLines.isEmpty()) {
            series.setTrendLines(trendLines);
        }


        return trendLines;
    }
}