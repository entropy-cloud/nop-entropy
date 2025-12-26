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

import java.util.Map;

/**
 * DrawingChartParser - Excel图表解析器
 * 负责解析OOXML chartSpace结构，协调各个子解析器
 * 修复OOXML结构解析和错误处理
 */
public class DrawingChartParser {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingChartParser.class);
    public static final DrawingChartParser INSTANCE = new DrawingChartParser();

    /**
     * 解析图表，遵循OOXML chartSpace结构规范
     *
     * @param chartRefNode 图表引用节点
     * @param pkg          Excel包
     * @param drawingPart  绘图部分
     * @param excelChart   目标ChartModel对象
     */
    public void parseChartRef(XNode chartRefNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart, ChartModel excelChart) {
        XNode chartNode = getChartSpaceNode(chartRefNode, pkg, drawingPart);
        if (chartNode == null) {
            LOG.warn("Chart node is null, cannot parse chart");
            return;
        }

        // 解析样式ID
        String styleId = parseStyleId(chartNode);

        // 创建样式提供者
        IChartStyleProvider styleProvider = createStyleProvider(pkg, drawingPart, styleId);
        parseChartSpace(chartNode, styleProvider, excelChart);
    }

    /**
     * 获取图表节点
     * 从图表引用节点中提取r:id，通过关系解析获取实际的图表XML节点
     *
     * @param chartRefNode 图表引用节点 (c:chart)
     * @param pkg          Excel包
     * @param drawingPart  绘图部分
     * @return 图表XML节点 (chartSpace)
     */
    private XNode getChartSpaceNode(XNode chartRefNode, ExcelOfficePackage pkg, IOfficePackagePart drawingPart) {
        try {
            // 从图表引用节点获取关系ID
            String rId = chartRefNode.attrText("r:id");
            if (StringHelper.isEmpty(rId)) {
                LOG.warn("Chart reference node missing r:id attribute");
                return null;
            }

            // 通过关系ID获取图表文件
            IOfficePackagePart chartPart = pkg.getRelPart(drawingPart, rId);
            if (chartPart == null) {
                LOG.warn("Chart part not found for relationship id: {}", rId);
                return null;
            }

            // 加载图表XML
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

        // 验证chartSpace结构
        validateChartStructure(chartSpaceNode);


        // 查找实际的chart节点 (c:chartSpace/c:chart)
        XNode actualChartNode = findActualChartNode(chartSpaceNode);
        if (actualChartNode == null) {
            LOG.warn("No actual chart node found in chartSpace, using chartSpace as chart node");
            actualChartNode = chartSpaceNode;
        }

        // 解析基础属性
        parseBasicProperties(excelChart, actualChartNode);

        // 解析图表组件
        parseTitle(excelChart, actualChartNode, styleProvider);
        parseLegend(excelChart, actualChartNode, styleProvider);
        parsePlotArea(excelChart, actualChartNode, styleProvider);

    }

    /**
     * 验证图表结构
     */
    private void validateChartStructure(XNode chartNode) {
        String tagName = chartNode.getTagName();
        if (!"c:chartSpace".equals(tagName) && !"c:chart".equals(tagName)) {
            LOG.warn("Unexpected chart root node: {}, expected c:chartSpace or c:chart", tagName);
        }
    }

    /**
     * 创建样式提供者
     * 根据styleId和drawingPart加载关联的style1.xml和colors1.xml文件
     * 
     * @param pkg Excel包
     * @param drawingPart 绘图部分
     * @param styleId 样式ID
     * @return 配置好的样式提供者
     */
    private IChartStyleProvider createStyleProvider(ExcelOfficePackage pkg, IOfficePackagePart drawingPart, String styleId) {
        try {
            // 如果没有styleId，使用默认样式提供者
            if (StringHelper.isEmpty(styleId)) {
                LOG.debug("No style ID provided, using default style provider");
                return new DefaultChartStyleProvider();
            }
            
            // 尝试加载主题文件
            ThemeFileParser.ThemeData themeData = loadThemeFiles(pkg, drawingPart, styleId);
            
            // 创建增强的样式提供者
            DefaultChartStyleProvider styleProvider = new DefaultChartStyleProvider();
            
            // 如果成功加载主题数据，应用到样式提供者
            if (themeData != null && themeData.getColorScheme() != null) {
                applyThemeDataToProvider(styleProvider, themeData);
                LOG.debug("Successfully applied theme data for style ID: {}", styleId);
            } else {
                LOG.debug("No theme data loaded for style ID: {}, using default colors", styleId);
            }
            
            return styleProvider;
            
        } catch (Exception e) {
            LOG.warn("Failed to create style provider for style ID: {}, using default", styleId, e);
            return new DefaultChartStyleProvider();
        }
    }
    
    /**
     * 加载主题文件（style1.xml和colors1.xml）
     * 
     * @param pkg Excel包
     * @param drawingPart 绘图部分
     * @param styleId 样式ID
     * @return 主题数据，如果加载失败返回null
     */
    private ThemeFileParser.ThemeData loadThemeFiles(ExcelOfficePackage pkg, IOfficePackagePart drawingPart, String styleId) {
        try {
            // 构建主题文件路径
            // 通常主题文件位于 xl/theme/ 目录下
            String themeBasePath = "xl/theme/";
            String stylesPath = themeBasePath + "style" + styleId + ".xml";
            String colorsPath = themeBasePath + "colors" + styleId + ".xml";
            
            // 尝试加载样式文件
            IOfficePackagePart stylesPart = null;
            IOfficePackagePart colorsPart = null;
            
            try {
                stylesPart = pkg.getFile(stylesPath);
            } catch (Exception e) {
                LOG.debug("Style file not found at: {}", stylesPath);
            }
            
            try {
                colorsPart = pkg.getFile(colorsPath);
            } catch (Exception e) {
                LOG.debug("Colors file not found at: {}", colorsPath);
            }
            
            // 如果都没找到，尝试默认主题文件
            if (stylesPart == null && colorsPart == null) {
                return loadDefaultThemeFiles(pkg);
            }
            
            ThemeFileParser.ThemeData themeData = new ThemeFileParser.ThemeData();
            
            // 解析样式文件
            if (stylesPart != null) {
                XNode stylesNode = stylesPart.loadXml();
                if (stylesNode != null) {
                    ThemeFileParser.ThemeData stylesThemeData = ThemeFileParser.INSTANCE.parseStylesFile(stylesNode);
                    if (stylesThemeData != null) {
                        // 合并样式数据
                        mergeThemeData(themeData, stylesThemeData);
                        LOG.debug("Successfully loaded styles from: {}", stylesPath);
                    }
                }
            }
            
            // 解析颜色文件
            if (colorsPart != null) {
                XNode colorsNode = colorsPart.loadXml();
                if (colorsNode != null) {
                    ThemeFileParser.ColorScheme colorScheme = ThemeFileParser.INSTANCE.parseColorsFile(colorsNode);
                    if (colorScheme != null) {
                        themeData.setColorScheme(colorScheme);
                        LOG.debug("Successfully loaded colors from: {}", colorsPath);
                    }
                }
            }
            
            return themeData;
            
        } catch (Exception e) {
            LOG.warn("Failed to load theme files for style ID: {}", styleId, e);
            return null;
        }
    }
    
    /**
     * 加载默认主题文件
     * 尝试加载 xl/theme/theme1.xml 等默认主题文件
     * 
     * @param pkg Excel包
     * @return 主题数据，如果加载失败返回null
     */
    private ThemeFileParser.ThemeData loadDefaultThemeFiles(ExcelOfficePackage pkg) {
        try {
            // 尝试加载默认主题文件
            String[] defaultThemePaths = {
                "xl/theme/theme1.xml",
                "xl/theme/theme.xml"
            };
            
            for (String themePath : defaultThemePaths) {
                try {
                    IOfficePackagePart themePart = pkg.getFile(themePath);
                    if (themePart != null) {
                        XNode themeNode = themePart.loadXml();
                        if (themeNode != null) {
                            ThemeFileParser.ThemeData themeData = ThemeFileParser.INSTANCE.parseStylesFile(themeNode);
                            if (themeData != null) {
                                LOG.debug("Successfully loaded default theme from: {}", themePath);
                                return themeData;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Default theme file not found at: {}", themePath);
                }
            }
            
            LOG.debug("No default theme files found");
            return null;
            
        } catch (Exception e) {
            LOG.warn("Failed to load default theme files", e);
            return null;
        }
    }
    
    /**
     * 合并主题数据
     * 
     * @param target 目标主题数据
     * @param source 源主题数据
     */
    private void mergeThemeData(ThemeFileParser.ThemeData target, ThemeFileParser.ThemeData source) {
        if (source == null) return;
        
        // 合并颜色方案
        if (source.getColorScheme() != null) {
            if (target.getColorScheme() == null) {
                target.setColorScheme(source.getColorScheme());
            } else {
                // 合并颜色映射
                for (Map.Entry<String, String> entry : source.getColorScheme().getAllColors().entrySet()) {
                    target.getColorScheme().addColor(entry.getKey(), entry.getValue());
                }
            }
        }
        
        // 合并字体信息
        if (source.getMajorFont() != null && target.getMajorFont() == null) {
            target.setMajorFont(source.getMajorFont());
        }
        if (source.getMinorFont() != null && target.getMinorFont() == null) {
            target.setMinorFont(source.getMinorFont());
        }
        if (source.getFormatSchemeName() != null && target.getFormatSchemeName() == null) {
            target.setFormatSchemeName(source.getFormatSchemeName());
        }
    }
    
    /**
     * 将主题数据应用到样式提供者
     * 
     * @param styleProvider 样式提供者
     * @param themeData 主题数据
     */
    private void applyThemeDataToProvider(DefaultChartStyleProvider styleProvider, ThemeFileParser.ThemeData themeData) {
        try {
            if (themeData == null) {
                LOG.debug("No theme data to apply");
                return;
            }
            
            // 直接设置完整的主题数据到样式提供者
            styleProvider.setThemeData(themeData);
            
            // 应用主题颜色
            if (themeData.getColorScheme() != null) {
                Map<String, String> themeColors = themeData.getColorScheme().getAllColors();
                if (!themeColors.isEmpty()) {
                    styleProvider.setThemeColors(themeColors);
                    LOG.debug("Applied {} theme colors to style provider", themeColors.size());
                    
                    // 记录关键主题颜色用于调试
                    logKeyThemeColors(themeColors);
                } else {
                    LOG.debug("No theme colors found in color scheme");
                }
            } else {
                LOG.debug("No color scheme found in theme data");
            }
            
            // 应用主题字体
            String majorFont = themeData.getMajorFont();
            String minorFont = themeData.getMinorFont();
            if (majorFont != null || minorFont != null) {
                styleProvider.setThemeFonts(majorFont, minorFont);
                LOG.debug("Applied theme fonts - Major: {}, Minor: {}", majorFont, minorFont);
            }
            
            // 记录格式方案名称（如果有）
            if (themeData.getFormatSchemeName() != null) {
                LOG.debug("Theme format scheme: {}", themeData.getFormatSchemeName());
            }
            
            LOG.debug("Successfully applied theme data to style provider");
            
        } catch (Exception e) {
            LOG.warn("Failed to apply theme data to style provider", e);
        }
    }
    
    /**
     * 记录关键主题颜色用于调试
     * 
     * @param themeColors 主题颜色映射
     */
    private void logKeyThemeColors(Map<String, String> themeColors) {
        // 记录最常用的主题颜色
        String[] keyColors = {"tx1", "tx2", "bg1", "bg2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6"};
        
        for (String colorKey : keyColors) {
            String colorValue = themeColors.get(colorKey);
            if (colorValue != null) {
                LOG.debug("Key theme color: {} -> {}", colorKey, colorValue);
            }
        }
    }

    /**
     * 查找实际的图表节点
     * OOXML结构: c:chartSpace/c:chart 或直接是 c:chart
     */
    private XNode findActualChartNode(XNode chartNode) {
        if ("c:chartSpace".equals(chartNode.getTagName())) {
            XNode chartChild = chartNode.childByTag("c:chart");
            if (chartChild != null) {
                return chartChild;
            }
        }
        return chartNode;
    }

    /**
     * 解析基础属性
     *
     * @param chart     图表模型
     * @param chartNode 图表节点
     */
    private void parseBasicProperties(ChartModel chart, XNode chartNode) {
        try {
            // 解析图表类型
            XNode plotAreaNode = chartNode.childByTag("c:plotArea");
            if (plotAreaNode != null) {
                parseChartType(chart, plotAreaNode);
            }

            // 解析其他基础属性
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

    /**
     * 解析图表类型
     *
     * @param chart        图表模型
     * @param plotAreaNode 绘图区域节点
     */
    private void parseChartType(ChartModel chart, XNode plotAreaNode) {
        try {
            // 检查图表类型
            for (XNode child : plotAreaNode.getChildren()) {
                String tagName = child.getTagName();
                if (tagName.startsWith("c:")) {
                    String chartType = tagName.substring(2); // 去掉"c:"前缀

                    // 映射到ChartType枚举
                    ChartType type = mapChartType(chartType);
                    if (type != null) {
                        chart.setType(type);
                        // 设置3D标志
                        chart.setIs3D(is3DChartType(chartType));
                        return;
                    }
                }
            }

            // 如果没有找到图表类型，设置默认类型
            LOG.warn("No recognized chart type found in plot area, using default COLUMN");
            chart.setType(ChartType.COLUMN);
            chart.setIs3D(false);

        } catch (Exception e) {
            LOG.warn("Failed to parse chart type, using default COLUMN", e);
            chart.setType(ChartType.COLUMN);
            chart.setIs3D(false);
        }
    }

    /**
     * 映射图表类型字符串到枚举
     * 修复3D图表类型映射，因为ChartType枚举中没有3D类型
     *
     * @param chartType 图表类型字符串
     * @return 对应的ChartType枚举
     */
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
                    return ChartType.HEATMAP; // 映射曲面图到热力图
                case "stockChart":
                case "ofPieChart":
                    return ChartType.COMBO; // 复合图表类型
                default:
                    LOG.warn("Unknown chart type: {}, returning null", chartType);
                    return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to map chart type: {}, returning null", chartType, e);
            return null;
        }
    }

    /**
     * 检查图表类型是否为3D
     *
     * @param chartType OOXML图表类型字符串
     * @return 是否为3D图表
     */
    private boolean is3DChartType(String chartType) {
        return chartType != null && chartType.endsWith("3DChart");
    }

    /**
     * 解析标题
     *
     * @param chart         图表模型
     * @param chartNode     图表节点
     * @param styleProvider 样式提供者
     */
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

    /**
     * 解析图例
     *
     * @param chart         图表模型
     * @param chartNode     图表节点
     * @param styleProvider 样式提供者
     */
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

    /**
     * 解析样式ID，支持2007版本的AlternateContent结构
     * 处理 mc:AlternateContent 中的 c14:style 和 c:style 元素
     * 
     * @param chartNode 图表节点
     * @return 样式ID字符串，如果未找到则返回null
     */
    public String parseStyleId(XNode chartNode) {
        try {
            // 首先查找 mc:AlternateContent 结构
            XNode alternateContentNode = chartNode.childByTag("mc:AlternateContent");
            if (alternateContentNode != null) {
                return parseAlternateContentStyleId(alternateContentNode);
            }
            
            // 如果没有 AlternateContent，直接查找 c:style 或 c14:style
            String styleId = parseDirectStyleId(chartNode);
            if (styleId != null) {
                return styleId;
            }
            
            LOG.debug("No style ID found in chart node");
            return null;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse style ID", e);
            return null;
        }
    }
    
    /**
     * 解析 AlternateContent 结构中的样式ID
     * 优先使用 c14:style (2007+)，回退到 c:style (兼容模式)
     * 
     * @param alternateContentNode mc:AlternateContent 节点
     * @return 样式ID字符串
     */
    private String parseAlternateContentStyleId(XNode alternateContentNode) {
        try {
            // 优先查找 mc:Choice 中的 c14:style (2007+ 版本)
            XNode choiceNode = alternateContentNode.childByTag("mc:Choice");
            if (choiceNode != null) {
                String requires = choiceNode.attrText("Requires");
                if ("c14".equals(requires)) {
                    XNode c14StyleNode = choiceNode.childByTag("c14:style");
                    if (c14StyleNode != null) {
                        String styleId = c14StyleNode.attrText("val");
                        if (!StringHelper.isEmpty(styleId)) {
                            LOG.debug("Found c14:style ID: {}", styleId);
                            return styleId;
                        }
                    }
                }
            }
            
            // 回退到 mc:Fallback 中的 c:style (兼容模式)
            XNode fallbackNode = alternateContentNode.childByTag("mc:Fallback");
            if (fallbackNode != null) {
                XNode cStyleNode = fallbackNode.childByTag("c:style");
                if (cStyleNode != null) {
                    String styleId = cStyleNode.attrText("val");
                    if (!StringHelper.isEmpty(styleId)) {
                        LOG.debug("Found fallback c:style ID: {}", styleId);
                        return styleId;
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse AlternateContent style ID", e);
            return null;
        }
    }
    
    /**
     * 直接解析样式ID（非 AlternateContent 结构）
     * 
     * @param chartNode 图表节点
     * @return 样式ID字符串
     */
    private String parseDirectStyleId(XNode chartNode) {
        try {
            // 查找 c14:style
            XNode c14StyleNode = chartNode.childByTag("c14:style");
            if (c14StyleNode != null) {
                String styleId = c14StyleNode.attrText("val");
                if (!StringHelper.isEmpty(styleId)) {
                    LOG.debug("Found direct c14:style ID: {}", styleId);
                    return styleId;
                }
            }
            
            // 查找 c:style
            XNode cStyleNode = chartNode.childByTag("c:style");
            if (cStyleNode != null) {
                String styleId = cStyleNode.attrText("val");
                if (!StringHelper.isEmpty(styleId)) {
                    LOG.debug("Found direct c:style ID: {}", styleId);
                    return styleId;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse direct style ID", e);
            return null;
        }
    }

    /**
     * 解析绘图区域
     *
     * @param chart         图表模型
     * @param chartNode     图表节点
     * @param styleProvider 样式提供者
     */
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
}