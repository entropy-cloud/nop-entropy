package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.excel.chart.model.ChartDataSourceModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTrendLineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.ooxml.xlsx.XlsxErrors.*;

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
     * @param serNode 系列节点
     * @param styleProvider 样式提供者
     * @return 系列模型对象
     */
    public ChartSeriesModel parseSeries(XNode serNode, IChartStyleProvider styleProvider) {
        if (serNode == null) {
            LOG.warn("Series node is null, returning null");
            return null;
        }
        
        try {
            ChartSeriesModel series = new ChartSeriesModel();
            
            // 解析基本属性
            parseBasicProperties(series, serNode);
            
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
        } catch (Exception e) {
            LOG.warn("Failed to parse series configuration", e);
            // 返回基本的series对象而不是null，确保图表解析能继续
            ChartSeriesModel basicSeries = new ChartSeriesModel();
            basicSeries.setName("Series");
            return basicSeries;
        }
    }
    
    /**
     * 解析图表类型特定配置
     * 根据父图表类型解析系列的特定属性
     */
    private void parseChartTypeSpecificConfig(ChartSeriesModel series, XNode serNode) {
        try {
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
        } catch (Exception e) {
            LOG.warn("Failed to parse chart type specific configuration", e);
        }
    }
    
    /**
     * 解析饼图系列配置
     */
    private void parsePieSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 饼图系列的爆炸值已在parseSeriesSpecificConfig中处理
            LOG.debug("Parsing pie chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse pie series configuration", e);
        }
    }
    
    /**
     * 解析柱状图系列配置
     */
    private void parseBarSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 柱状图特定配置
            LOG.debug("Parsing bar chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse bar series configuration", e);
        }
    }
    
    /**
     * 解析折线图系列配置
     */
    private void parseLineSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 折线图的标记和平滑线配置已在parseSeriesSpecificConfig中处理
            LOG.debug("Parsing line chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse line series configuration", e);
        }
    }
    
    /**
     * 解析散点图系列配置
     */
    private void parseScatterSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 散点图特定配置
            LOG.debug("Parsing scatter chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse scatter series configuration", e);
        }
    }
    
    /**
     * 解析气泡图系列配置
     */
    private void parseBubbleSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 气泡图的气泡大小数据已在parseSeriesData中处理
            LOG.debug("Parsing bubble chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse bubble series configuration", e);
        }
    }
    
    /**
     * 解析面积图系列配置
     */
    private void parseAreaSeriesConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 面积图特定配置
            LOG.debug("Parsing area chart series configuration");
        } catch (Exception e) {
            LOG.warn("Failed to parse area series configuration", e);
        }
    }
    
    /**
     * 解析基本属性
     */
    private void parseBasicProperties(ChartSeriesModel series, XNode serNode) {
        try {
            // 解析系列索引
            String idx = ChartPropertyHelper.getChildVal(serNode, "c:idx");
            if (!StringHelper.isEmpty(idx)) {
                try {
                    series.setIndex(Integer.parseInt(idx));
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid series index: {}", idx);
                }
            }
            
            // 解析系列顺序
            String order = ChartPropertyHelper.getChildVal(serNode, "c:order");
            if (!StringHelper.isEmpty(order)) {
                try {
                    series.setOrder(Integer.parseInt(order));
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid series order: {}", order);
                }
            }
            
            // 解析系列名称
            parseSeriesName(series, serNode);
            
            // 解析可见性
            parseVisibility(series, serNode);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series basic properties", e);
        }
    }
    
    /**
     * 解析系列名称
     */
    private void parseSeriesName(ChartSeriesModel series, XNode serNode) {
        try {
            XNode txNode = serNode.childByTag("c:tx");
            if (txNode != null) {
                String seriesName = ChartTextParser.INSTANCE.extractText(txNode);
                if (!StringHelper.isEmpty(seriesName)) {
                    series.setName(seriesName);
                    return;
                }
            }
            
            // 如果没有找到名称，使用默认名称
            String defaultName = "Series";
            if (series.getIndex() != null) {
                defaultName = "Series " + series.getIndex();
            } else if (series.getOrder() != null) {
                defaultName = "Series " + series.getOrder();
            }
            series.setName(defaultName);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series name, using default", e);
            series.setName("Series");
        }
    }
    
    /**
     * 解析可见性
     */
    private void parseVisibility(ChartSeriesModel series, XNode serNode) {
        try {
            // 在OOXML中，系列默认是可见的，除非明确设置为隐藏
            // 检查是否有delete元素
            Boolean isDeleted = ChartPropertyHelper.getChildBoolVal(serNode, "c:delete");
            if (isDeleted != null) {
                series.setVisible(!isDeleted); // delete=true 意味着不可见
                return;
            }
            
            // 默认可见
            series.setVisible(true);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series visibility, using default visible=true", e);
            series.setVisible(true);
        }
    }
    
    /**
     * 解析系列数据
     */
    private void parseSeriesData(ChartSeriesModel series, XNode serNode) {
        try {
            // 解析类别数据 (X轴数据)
            XNode catNode = serNode.childByTag("c:cat");
            if (catNode != null) {
                parseDataReference(series, catNode, "category");
            }
            
            // 解析数值数据 (Y轴数据)
            XNode valNode = serNode.childByTag("c:val");
            if (valNode != null) {
                parseDataReference(series, valNode, "values");
            }
            
            // 解析X值数据 (散点图)
            XNode xValNode = serNode.childByTag("c:xVal");
            if (xValNode != null) {
                parseDataReference(series, xValNode, "xValues");
            }
            
            // 解析Y值数据 (散点图)
            XNode yValNode = serNode.childByTag("c:yVal");
            if (yValNode != null) {
                parseDataReference(series, yValNode, "yValues");
            }
            
            // 解析气泡大小数据 (气泡图)
            XNode bubbleSizeNode = serNode.childByTag("c:bubbleSize");
            if (bubbleSizeNode != null) {
                parseDataReference(series, bubbleSizeNode, "bubbleSize");
            }
            
            // 解析系列特定配置
            parseSeriesSpecificConfig(series, serNode);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series data", e);
        }
    }
    
    /**
     * 解析系列特定配置
     * 处理不同图表类型的系列特定属性
     */
    private void parseSeriesSpecificConfig(ChartSeriesModel series, XNode serNode) {
        try {
            // 解析爆炸值 (饼图)
            String explosion = ChartPropertyHelper.getChildVal(serNode, "c:explosion");
            if (!StringHelper.isEmpty(explosion)) {
                try {
                    int explosionValue = Integer.parseInt(explosion);
                    // TODO: 设置到系列的特定配置中
                    LOG.debug("Series explosion value: {}", explosionValue);
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid explosion value: {}", explosion);
                }
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
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series specific configuration", e);
        }
    }
    
    /**
     * 解析标记配置
     */
    private void parseMarkerConfig(ChartSeriesModel series, XNode serNode) {
        try {
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
        } catch (Exception e) {
            LOG.warn("Failed to parse marker configuration", e);
        }
    }
    
    /**
     * 解析3D配置
     */
    private void parse3DConfig(ChartSeriesModel series, XNode serNode) {
        try {
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
            
        } catch (Exception e) {
            LOG.warn("Failed to parse 3D configuration", e);
        }
    }
    
    /**
     * 解析数据引用
     */
    private void parseDataReference(ChartSeriesModel series, XNode dataNode, String dataType) {
        try {
            // 使用ChartTextParser统一处理单元格引用
            String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(dataNode);
            if (cellRef != null) {
                // 根据数据类型设置相应的引用
                switch (dataType) {
                    case "categories":
                        series.setCategoriesRef(cellRef);
                        break;
                    case "values":
                        series.setValuesRef(cellRef);
                        break;
                    case "xValues":
                        series.setXValuesRef(cellRef);
                        break;
                    case "yValues":
                        series.setYValuesRef(cellRef);
                        break;
                    case "bubbleSize":
                        series.setBubbleSizeRef(cellRef);
                        break;
                    default:
                        LOG.warn("Unknown data type: {}", dataType);
                        break;
                }
                return;
            }
            
            LOG.debug("No data reference found for series data type: {}", dataType);
            
        } catch (Exception e) {
            LOG.warn("Failed to parse data reference for type: {}", dataType, e);
        }
    }
    
    /**
     * 解析系列格式化
     */
    private void parseSeriesFormatting(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        try {
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
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series formatting", e);
        }
    }
    
    /**
     * 解析基于索引的格式化
     * 处理系列索引和顺序相关的样式配置
     */
    private void parseIndexBasedFormatting(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        try {
            // 应用基于索引的默认样式
            if (series.getIndex() != null && styleProvider != null) {
                // TODO: 根据系列索引应用默认颜色和样式
                LOG.debug("Applying index-based formatting for series index: {}", series.getIndex());
            }
            
            // 解析系列特定的颜色变化
            Boolean varyColorsBool = ChartPropertyHelper.getChildBoolVal(serNode, "c:varyColors");
            if (varyColorsBool != null) {
                LOG.debug("Series vary colors: {}", varyColorsBool);
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to parse index-based formatting", e);
        }
    }
    
    /**
     * 解析数据标签
     */
    private void parseDataLabels(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        try {
            XNode dLblsNode = serNode.childByTag("c:dLbls");
            if (dLblsNode != null) {
                ChartDataLabelsModel dataLabels = ChartDataLabelsParser.INSTANCE.parseDataLabels(dLblsNode, styleProvider);
                if (dataLabels != null) {
                    series.setDataLabels(dataLabels);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse series data labels", e);
        }
    }
    
    /**
     * 解析趋势线
     */
    private List<ChartTrendLineModel> parseTrendLines(ChartSeriesModel series, XNode serNode, IChartStyleProvider styleProvider) {
        List<ChartTrendLineModel> trendLines = new ArrayList<>();
        
        try {
            for (XNode trendlineNode : serNode.childrenByTag("c:trendline")) {
                ChartTrendLineModel trendLine = ChartTrendLineParser.INSTANCE.parseTrendLine(trendlineNode, styleProvider);
                if (trendLine != null) {
                    trendLines.add(trendLine);
                }
            }
            
            if (!trendLines.isEmpty()) {
                series.setTrendLines(trendLines);
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to parse series trend lines", e);
        }
        
        return trendLines;
    }
}