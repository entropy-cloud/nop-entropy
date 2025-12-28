package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DrawingChartParser - Excel图表解析器
 * 负责解析OOXML chartSpace结构，协调各个子解析器
 * 修复OOXML结构解析和错误处理
 */
public class DrawingChartParser {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingChartParser.class);
    public static final DrawingChartParser INSTANCE = new DrawingChartParser();

    public void parseChartRef(XNode chartRefNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart, ChartModel excelChart) {
        // 解析 description 信息
        parseDescription(chartRefNode, excelChart);
        
        XNode chartNode = getChartSpaceNode(chartRefNode, pkg, drawingPart);
        if (chartNode == null) {
            LOG.warn("Chart node is null, cannot parse chart");
            return;
        }

        String styleId = parseStyleId(chartNode);
        IChartStyleProvider styleProvider = ChartStyleProviderFactory.INSTANCE.createStyleProvider(pkg, drawingPart, styleId);
        parseChartSpace(chartNode, styleProvider, excelChart);
    }

    private XNode getChartSpaceNode(XNode chartRefNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart) {
        try {
            String rId = chartRefNode.attrText("r:id");
            if (StringHelper.isEmpty(rId)) {
                LOG.warn("Chart reference node missing r:id attribute");
                return null;
            }

            IOfficePackagePart chartPart = pkg.getRelPart(drawingPart, rId);
            if (chartPart == null) {
                LOG.warn("Chart part not found for relationship id: {}", rId);
                return null;
            }

            XNode chartNode = chartPart.loadXml();
            if (chartNode == null) {
                LOG.warn("Failed to load chart XML from part: {}", chartPart.getPath());
                return null;
            }

            LOG.debug("Successfully loaded chart node from: {}", chartPart.getPath());
            return chartNode;

        } catch (Exception e) {
            LOG.warn("Failed to get chart node", e);
            return null;
        }
    }

    public void parseChartSpace(XNode chartSpaceNode, IChartStyleProvider styleProvider, ChartModel excelChart) {
        validateChartStructure(chartSpaceNode);

        String lang = (String)chartSpaceNode.childAttr("c:lang","val");
        excelChart.setLang(lang);

        Boolean roundedCorners = "1".equals(chartSpaceNode.childAttr("c:roundedCorners","val"));
        excelChart.setRoundedCorners(roundedCorners);

        XNode actualChartNode = findActualChartNode(chartSpaceNode);
        if (actualChartNode == null) {
            LOG.warn("No actual chart node found in chartSpace, using chartSpace as chart node");
            actualChartNode = chartSpaceNode;
        }

        parseBasicProperties(excelChart, actualChartNode);
        parseTitle(excelChart, actualChartNode, styleProvider);
        parseLegend(excelChart, actualChartNode, styleProvider);
        parsePlotArea(excelChart, actualChartNode, styleProvider);
    }

    private void validateChartStructure(XNode chartNode) {
        String tagName = chartNode.getTagName();
        if (!"c:chartSpace".equals(tagName) && !"c:chart".equals(tagName)) {
            LOG.warn("Unexpected chart root node: {}, expected c:chartSpace or c:chart", tagName);
        }
    }

    private XNode findActualChartNode(XNode chartNode) {
        if ("c:chartSpace".equals(chartNode.getTagName())) {
            XNode chartChild = chartNode.childByTag("c:chart");
            if (chartChild != null) {
                return chartChild;
            }
        }
        return chartNode;
    }

    private void parseBasicProperties(ChartModel chart, XNode chartNode) {
        try {
            XNode plotAreaNode = chartNode.childByTag("c:plotArea");
            if (plotAreaNode != null) {
                parseChartType(chart, plotAreaNode);
            }

            String roundedCorners = chartNode.attrText("roundedCorners");
            if (!StringHelper.isEmpty(roundedCorners)) {
                Boolean roundedCornersBool = ChartPropertyHelper.convertToBoolean(roundedCorners);
                if (roundedCornersBool != null) {
                    chart.setRoundedCorners(roundedCornersBool);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart basic properties", e);
        }
    }

    private void parseChartType(ChartModel chart, XNode plotAreaNode) {
        try {
            for (XNode child : plotAreaNode.getChildren()) {
                String tagName = child.getTagName();
                if (tagName.startsWith("c:")) {
                    String chartType = tagName.substring(2);

                    ChartType type = mapChartType(chartType);
                    if (type != null) {
                        chart.setType(type);
                        return;
                    }
                }
            }

            LOG.warn("No recognized chart type found in plot area, using default COLUMN");
            chart.setType(ChartType.COLUMN);

        } catch (Exception e) {
            LOG.warn("Failed to parse chart type, using default COLUMN", e);
            chart.setType(ChartType.COLUMN);
        }
    }

    private ChartType mapChartType(String chartType) {
        try {
            switch (chartType) {
                case "areaChart":
                case "area3DChart":
                    return ChartType.AREA;
                case "barChart":
                case "bar3DChart":
                    return ChartType.BAR;
                case "lineChart":
                case "line3DChart":
                    return ChartType.LINE;
                case "pieChart":
                case "pie3DChart":
                    return ChartType.PIE;
                case "doughnutChart":
                    return ChartType.DOUGHNUT;
                case "scatterChart":
                    return ChartType.SCATTER;
                case "bubbleChart":
                    return ChartType.BUBBLE;
                case "radarChart":
                    return ChartType.RADAR;
                case "surfaceChart":
                case "surface3DChart":
                    return ChartType.HEATMAP;
                case "stockChart":
                case "ofPieChart":
                    return ChartType.COMBO;
                default:
                    LOG.warn("Unknown chart type: {}, returning null", chartType);
                    return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map chart type: {}, returning null", chartType, e);
            return null;
        }
    }

    private boolean is3DChartType(String chartType) {
        return chartType != null && chartType.endsWith("3DChart");
    }

    private void parseTitle(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode titleNode = chartNode.childByTag("c:title");
            if (titleNode != null) {
                ChartTitleModel title = ChartTitleParser.INSTANCE.parseTitle(titleNode, styleProvider);
                if (title != null) {
                    chart.setTitle(title);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart title", e);
        }
    }

    private void parseLegend(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode legendNode = chartNode.childByTag("c:legend");
            if (legendNode != null) {
                ChartLegendModel legend = ChartLegendParser.INSTANCE.parseLegend(legendNode, styleProvider);
                if (legend != null) {
                    chart.setLegend(legend);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart legend", e);
        }
    }

    private void parsePlotArea(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider) {
        try {
            XNode plotAreaNode = chartNode.childByTag("c:plotArea");
            if (plotAreaNode != null) {
                ChartPlotAreaModel plotArea = ChartPlotAreaParser.INSTANCE.parsePlotArea(plotAreaNode, styleProvider);
                if (plotArea != null) {
                    chart.setPlotArea(plotArea);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart plot area", e);
        }
    }

    private String parseStyleId(XNode chartNode) {
        try {
            XNode styleNode = chartNode.childByTag("c:style");
            if (styleNode != null) {
                String styleId = styleNode.attrText("val");
                if (!StringHelper.isEmpty(styleId)) {
                    LOG.debug("Found chart style ID: {}", styleId);
                    return styleId;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse style ID", e);
        }
        return null;
    }

    private void parseDescription(XNode chartRefNode, ChartModel excelChart) {
        try {
            // 查找 xdr:nvGraphicFramePr/xdr:cNvPr 节点
            XNode nvGraphicFramePr = chartRefNode.childByTag("xdr:nvGraphicFramePr");
            if (nvGraphicFramePr != null) {
                XNode cNvPr = nvGraphicFramePr.childByTag("xdr:cNvPr");
                if (cNvPr != null) {
                    String descr = cNvPr.attrText("descr");
                    if (!StringHelper.isEmpty(descr)) {
                        excelChart.setDescription(descr);
                        LOG.debug("Parsed chart description: {}", descr);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse chart description", e);
        }
    }
}
