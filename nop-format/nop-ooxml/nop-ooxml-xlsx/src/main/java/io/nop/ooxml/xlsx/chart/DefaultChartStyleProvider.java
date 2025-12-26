package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.IChartStyleSupportModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.constants.ChartFillType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * DefaultChartStyleProvider - 默认图表样式提供者
 * 提供基本的主题颜色解析和样式应用功能
 * 支持外部主题文件的懒加载和缓存
 */
public class DefaultChartStyleProvider implements IChartStyleProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultChartStyleProvider.class);
    
    // 主题数据缓存
    private ThemeFileParser.ThemeData cachedThemeData;
    private boolean themeDataLoaded = false;
    
    // 默认主题颜色映射 - 扩展支持所有OOXML样本中发现的颜色
    private static final Map<String, String> DEFAULT_THEME_COLORS = new HashMap<>();
    
    static {
        // 标准强调色（在所有样本中发现）
        DEFAULT_THEME_COLORS.put("accent1", "#4472C4");
        DEFAULT_THEME_COLORS.put("accent2", "#ED7D31");
        DEFAULT_THEME_COLORS.put("accent3", "#A5A5A5");
        DEFAULT_THEME_COLORS.put("accent4", "#FFC000");
        DEFAULT_THEME_COLORS.put("accent5", "#5B9BD5");
        DEFAULT_THEME_COLORS.put("accent6", "#70AD47");
        
        // 文本和背景颜色（样本中关键的）
        DEFAULT_THEME_COLORS.put("tx1", "#000000");      // 主要文本 - 样本中最常用
        DEFAULT_THEME_COLORS.put("tx2", "#1F497D");      // 次要文本
        DEFAULT_THEME_COLORS.put("bg1", "#FFFFFF");      // 主要背景 - 样本中使用
        DEFAULT_THEME_COLORS.put("bg2", "#F2F2F2");      // 次要背景
        
        // 深色/浅色变体
        DEFAULT_THEME_COLORS.put("dk1", "#000000");
        DEFAULT_THEME_COLORS.put("lt1", "#FFFFFF");
        DEFAULT_THEME_COLORS.put("dk2", "#1F497D");
        DEFAULT_THEME_COLORS.put("lt2", "#EEECE1");
        
        // 向后兼容的别名
        DEFAULT_THEME_COLORS.put("background1", "#FFFFFF");
        DEFAULT_THEME_COLORS.put("background2", "#F2F2F2");
        DEFAULT_THEME_COLORS.put("text1", "#000000");
        DEFAULT_THEME_COLORS.put("text2", "#1F497D");
    }
    
    /**
     * 加载主题文件数据（懒加载）
     * @param stylesNode 样式文件节点（可选）
     * @param colorsNode 颜色文件节点（可选）
     */
    public void loadThemeFiles(XNode stylesNode, XNode colorsNode) {
        if (themeDataLoaded) {
            return; // 已经加载过，避免重复加载
        }
        
        try {
            ThemeFileParser.ThemeData themeData = new ThemeFileParser.ThemeData();
            
            // 解析样式文件
            if (stylesNode != null) {
                themeData = ThemeFileParser.INSTANCE.parseStylesFile(stylesNode);
                LOG.debug("Loaded theme data from styles file");
            }
            
            // 解析颜色文件（如果单独提供）
            if (colorsNode != null) {
                ThemeFileParser.ColorScheme colorScheme = ThemeFileParser.INSTANCE.parseColorsFile(colorsNode);
                if (themeData.getColorScheme() == null || themeData.getColorScheme().getAllColors().isEmpty()) {
                    themeData.setColorScheme(colorScheme);
                    LOG.debug("Loaded color scheme from colors file");
                }
            }
            
            this.cachedThemeData = themeData;
            this.themeDataLoaded = true;
            
        } catch (Exception e) {
            LOG.warn("Failed to load theme files, using default colors", e);
            this.cachedThemeData = new ThemeFileParser.ThemeData();
            this.themeDataLoaded = true;
        }
    }
    
    /**
     * 获取主题数据（懒加载）
     * @return 主题数据，如果未加载则返回空主题数据
     */
    private ThemeFileParser.ThemeData getThemeData() {
        if (!themeDataLoaded) {
            // 如果没有外部主题文件，使用默认主题数据
            this.cachedThemeData = new ThemeFileParser.ThemeData();
            this.themeDataLoaded = true;
        }
        return cachedThemeData;
    }
    
    /**
     * 设置主题数据
     * 允许外部动态设置主题数据，覆盖默认主题颜色
     * 
     * @param themeData 主题数据
     */
    public void setThemeData(ThemeFileParser.ThemeData themeData) {
        this.cachedThemeData = themeData;
        this.themeDataLoaded = true;
        LOG.debug("Theme data set externally");
    }
    
    /**
     * 添加或更新主题颜色
     * 
     * @param colorKey 颜色键（如 "accent1", "tx1" 等）
     * @param colorValue 颜色值（十六进制格式，如 "#FF0000"）
     */
    public void setThemeColor(String colorKey, String colorValue) {
        if (colorKey == null || colorValue == null) return;
        
        // 确保主题数据已初始化
        ThemeFileParser.ThemeData themeData = getThemeData();
        if (themeData.getColorScheme() == null) {
            themeData.setColorScheme(new ThemeFileParser.ColorScheme());
        }
        
        themeData.getColorScheme().addColor(colorKey.toLowerCase(), colorValue);
        LOG.debug("Theme color set: {} -> {}", colorKey, colorValue);
    }
    
    /**
     * 批量设置主题颜色
     * 
     * @param themeColors 主题颜色映射
     */
    public void setThemeColors(Map<String, String> themeColors) {
        if (themeColors == null || themeColors.isEmpty()) return;
        
        for (Map.Entry<String, String> entry : themeColors.entrySet()) {
            setThemeColor(entry.getKey(), entry.getValue());
        }
        
        LOG.debug("Batch theme colors set: {} colors", themeColors.size());
    }
    
    /**
     * 设置主题字体
     * 
     * @param majorFont 主要字体
     * @param minorFont 次要字体
     */
    public void setThemeFonts(String majorFont, String minorFont) {
        ThemeFileParser.ThemeData themeData = getThemeData();
        
        if (majorFont != null) {
            themeData.setMajorFont(majorFont);
            LOG.debug("Major font set: {}", majorFont);
        }
        
        if (minorFont != null) {
            themeData.setMinorFont(minorFont);
            LOG.debug("Minor font set: {}", minorFont);
        }
    }
    
    /**
     * 获取当前主题颜色映射（用于调试和验证）
     * 
     * @return 当前所有主题颜色的映射
     */
    public Map<String, String> getCurrentThemeColors() {
        Map<String, String> allColors = new HashMap<>(DEFAULT_THEME_COLORS);
        
        ThemeFileParser.ThemeData themeData = getThemeData();
        if (themeData != null && themeData.getColorScheme() != null) {
            // 主题文件中的颜色会覆盖默认颜色
            allColors.putAll(themeData.getColorScheme().getAllColors());
        }
        
        return allColors;
    }
    
    @Override
    public String getThemeColor(String themeColor) {
        if (themeColor == null) return null;
        
        // 首先尝试从加载的主题文件中获取颜色
        ThemeFileParser.ThemeData themeData = getThemeData();
        if (themeData != null && themeData.getColorScheme() != null) {
            String colorKey = themeColor.toLowerCase();
            String themeFileColor = themeData.getColorScheme().getColor(colorKey);
            if (themeFileColor != null) {
                return themeFileColor;
            }
        }
        
        // 回退到默认主题颜色映射
        String colorKey = themeColor.toLowerCase();
        String color = DEFAULT_THEME_COLORS.get(colorKey);
        
        if (color != null) {
            return color;
        }
        
        // 处理可能的"tx"前缀（向后兼容）
        if (colorKey.startsWith("tx")) {
            String textKey = "text" + colorKey.substring(2);
            return DEFAULT_THEME_COLORS.get(textKey);
        }
        
        // 默认返回黑色
        return "#000000";
    }
    
    @Override
    public String resolveColor(String colorRef) {
        if (colorRef == null) return null;
        
        // 如果是主题颜色引用
        if (colorRef.startsWith("theme") || colorRef.startsWith("tx") || colorRef.startsWith("bg") || 
            colorRef.startsWith("accent") || colorRef.startsWith("dk") || colorRef.startsWith("lt")) {
            return getThemeColor(colorRef);
        }
        
        // 如果是RGB颜色格式
        if (colorRef.startsWith("#")) {
            return colorRef;
        }
        
        // 如果是预定义颜色名称
        return mapColorName(colorRef);
    }
    
    @Override
    public String applyColorModifications(String baseColor, XNode colorNode) {
        if (colorNode == null || baseColor == null) {
            return baseColor;
        }
        
        try {
            // 使用ColorModificationEngine进行完整的OOXML颜色处理
            return ColorModificationEngine.applyModifications(baseColor, colorNode);
        } catch (Exception e) {
            // 如果ColorModificationEngine失败，回退到原有实现
            return applyColorModificationsLegacy(baseColor, colorNode);
        }
    }
    
    /**
     * 传统的颜色修改实现（作为备用）
     */
    private String applyColorModificationsLegacy(String baseColor, XNode colorNode) {
        if (colorNode == null || baseColor == null) {
            return baseColor;
        }
        
        // 解析基础颜色
        String currentColor = baseColor;
        
        // 如果基础颜色是主题颜色引用，先解析为RGB
        if (!currentColor.startsWith("#")) {
            currentColor = getThemeColor(currentColor);
            if (currentColor == null) {
                currentColor = "#000000"; // 默认黑色
            }
        }
        
        // 按OOXML规范顺序应用颜色修改
        // 1. 色调调整
        String hueVal = ChartPropertyHelper.getChildVal(colorNode, "a:hue");
        if (hueVal != null) {
            currentColor = applyHueShift(currentColor, parseOoxmlValue(hueVal));
        }
        
        // 2. 饱和度调整
        String satVal = ChartPropertyHelper.getChildVal(colorNode, "a:sat");
        if (satVal != null) {
            currentColor = applySaturation(currentColor, parseOoxmlValue(satVal));
        }
        
        String satModVal = ChartPropertyHelper.getChildVal(colorNode, "a:satMod");
        if (satModVal != null) {
            currentColor = applySaturationModulation(currentColor, parseOoxmlValue(satModVal));
        }
        
        // 3. 亮度调整（样本中最关键的）
        String lumModVal = ChartPropertyHelper.getChildVal(colorNode, "a:lumMod");
        if (lumModVal != null) {
            currentColor = applyLuminanceModulation(currentColor, parseOoxmlValue(lumModVal));
        }
        
        String lumOffVal = ChartPropertyHelper.getChildVal(colorNode, "a:lumOff");
        if (lumOffVal != null) {
            currentColor = applyLuminanceOffset(currentColor, parseOoxmlValue(lumOffVal));
        }
        
        // 4. 色调/阴影调整
        String tintVal = ChartPropertyHelper.getChildVal(colorNode, "a:tint");
        if (tintVal != null) {
            currentColor = applyTint(currentColor, parseOoxmlValue(tintVal));
        }
        
        String shadeVal = ChartPropertyHelper.getChildVal(colorNode, "a:shade");
        if (shadeVal != null) {
            currentColor = applyShade(currentColor, parseOoxmlValue(shadeVal));
        }
        
        // 5. 透明度调整
        String alphaVal = ChartPropertyHelper.getChildVal(colorNode, "a:alpha");
        if (alphaVal != null) {
            currentColor = applyAlpha(currentColor, parseOoxmlValue(alphaVal));
        }
        
        return currentColor;
    }
    
    /**
     * 解析OOXML值（100000 = 100%）
     */
    private double parseOoxmlValue(String value) {
        if (value == null) return 1.0;
        try {
            return Double.parseDouble(value) / 100000.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
    
    /**
     * 应用亮度调制（样本中最常用）
     */
    private String applyLuminanceModulation(String color, double lumModValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        // 将RGB转换为HSL进行亮度调整
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[2] = Math.max(0.0, Math.min(1.0, hsl[2] * lumModValue));
        
        int[] newRgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
    }
    
    /**
     * 应用亮度偏移（样本中最常用）
     */
    private String applyLuminanceOffset(String color, double lumOffValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        // 将RGB转换为HSL进行亮度调整
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[2] = Math.max(0.0, Math.min(1.0, hsl[2] + lumOffValue));
        
        int[] newRgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
    }
    
    /**
     * 应用色调
     */
    private String applyTint(String color, double tintValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        // 色调是向白色混合
        int r = (int) (rgb[0] + (255 - rgb[0]) * tintValue);
        int g = (int) (rgb[1] + (255 - rgb[1]) * tintValue);
        int b = (int) (rgb[2] + (255 - rgb[2]) * tintValue);
        
        return String.format("#%02X%02X%02X", 
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }
    
    /**
     * 应用阴影
     */
    private String applyShade(String color, double shadeValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        // 阴影是向黑色混合
        int r = (int) (rgb[0] * shadeValue);
        int g = (int) (rgb[1] * shadeValue);
        int b = (int) (rgb[2] * shadeValue);
        
        return String.format("#%02X%02X%02X", 
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }
    
    /**
     * 应用透明度（这里简化处理，实际应用中可能需要更复杂的处理）
     */
    private String applyAlpha(String color, double alphaValue) {
        // 简化处理：透明度通过调整亮度来模拟
        return applyLuminanceModulation(color, alphaValue);
    }
    
    /**
     * 应用饱和度
     */
    private String applySaturation(String color, double satValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[1] = Math.max(0.0, Math.min(1.0, satValue));
        
        int[] newRgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
    }
    
    /**
     * 应用饱和度调制
     */
    private String applySaturationModulation(String color, double satModValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[1] = Math.max(0.0, Math.min(1.0, hsl[1] * satModValue));
        
        int[] newRgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
    }
    
    /**
     * 应用色调偏移
     */
    private String applyHueShift(String color, double hueValue) {
        int[] rgb = parseRgb(color);
        if (rgb == null) return color;
        
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[0] = (hsl[0] + hueValue) % 1.0;
        if (hsl[0] < 0) hsl[0] += 1.0;
        
        int[] newRgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return String.format("#%02X%02X%02X", newRgb[0], newRgb[1], newRgb[2]);
    }
    
    /**
     * 解析RGB颜色字符串
     */
    private int[] parseRgb(String color) {
        if (color == null || !color.startsWith("#") || color.length() != 7) {
            return null;
        }
        
        try {
            int r = Integer.parseInt(color.substring(1, 3), 16);
            int g = Integer.parseInt(color.substring(3, 5), 16);
            int b = Integer.parseInt(color.substring(5, 7), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * RGB转HSL
     */
    private double[] rgbToHsl(int r, int g, int b) {
        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;
        
        double max = Math.max(Math.max(rNorm, gNorm), bNorm);
        double min = Math.min(Math.min(rNorm, gNorm), bNorm);
        
        double h = 0, s = 0, l = (max + min) / 2.0;
        
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
            
            if (max == rNorm) {
                h = (gNorm - bNorm) / d + (gNorm < bNorm ? 6 : 0);
            } else if (max == gNorm) {
                h = (bNorm - rNorm) / d + 2;
            } else {
                h = (rNorm - gNorm) / d + 4;
            }
            h /= 6.0;
        }
        
        return new double[]{h, s, l};
    }
    
    /**
     * HSL转RGB
     */
    private int[] hslToRgb(double h, double s, double l) {
        double r, g, b;
        
        if (s == 0) {
            r = g = b = l; // 无饱和度
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0/3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0/3.0);
        }
        
        return new int[]{
            (int) Math.round(r * 255),
            (int) Math.round(g * 255),
            (int) Math.round(b * 255)
        };
    }
    
    /**
     * 色调转RGB辅助方法
     */
    private double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0/6.0) return p + (q - p) * 6 * t;
        if (t < 1.0/2.0) return q;
        if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6;
        return p;
    }
    
    @Override
    public ChartShapeStyleModel getDefaultStyle(String componentType) {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 根据组件类型设置默认样式
        switch (componentType) {
            case "title":
                return createTitleDefaultStyle();
            case "legend":
                return createLegendDefaultStyle();
            case "axis":
                return createAxisDefaultStyle();
            case "series":
                return createSeriesDefaultStyle();
            case "grid":
                return createGridDefaultStyle();
            default:
                return createGenericDefaultStyle();
        }
    }
    
    /**
     * 创建标题默认样式
     */
    private ChartShapeStyleModel createTitleDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 标题通常使用透明背景
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.NONE);
        style.setFill(fill);
        
        return style;
    }
    
    /**
     * 创建图例默认样式
     */
    private ChartShapeStyleModel createLegendDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 图例使用白色背景和浅灰色边框
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#FFFFFF");
        style.setFill(fill);
        
        ChartBorderModel border = new ChartBorderModel();
        border.setColor("#CCCCCC");
        border.setWidth(1.0);
        style.setBorder(border);
        
        return style;
    }
    
    /**
     * 创建坐标轴默认样式
     */
    private ChartShapeStyleModel createAxisDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 坐标轴使用黑色线条
        ChartBorderModel border = new ChartBorderModel();
        border.setColor("#000000");
        border.setWidth(1.0);
        style.setBorder(border);
        
        return style;
    }
    
    /**
     * 创建数据系列默认样式
     */
    private ChartShapeStyleModel createSeriesDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 数据系列使用主题颜色填充
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.SOLID);
        fill.setForegroundColor("#4472C4"); // 默认accent1颜色
        style.setFill(fill);
        
        return style;
    }
    
    /**
     * 创建网格线默认样式
     */
    private ChartShapeStyleModel createGridDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 网格线使用浅灰色
        ChartBorderModel border = new ChartBorderModel();
        border.setColor("#D0D0D0");
        border.setWidth(0.5);
        style.setBorder(border);
        
        return style;
    }
    
    /**
     * 创建通用默认样式
     */
    private ChartShapeStyleModel createGenericDefaultStyle() {
        ChartShapeStyleModel style = new ChartShapeStyleModel();
        
        // 默认透明背景和黑色边框
        ChartFillModel fill = new ChartFillModel();
        fill.setType(ChartFillType.NONE);
        style.setFill(fill);
        
        ChartBorderModel border = new ChartBorderModel();
        border.setColor("#000000");
        border.setWidth(1.0);
        style.setBorder(border);
        
        return style;
    }
    
    /**
     * 映射颜色名称到十六进制值
     */
    private String mapColorName(String colorName) {
        if (colorName == null) return null;
        
        switch (colorName.toLowerCase()) {
            case "black": return "#000000";
            case "white": return "#FFFFFF";
            case "red": return "#FF0000";
            case "green": return "#00FF00";
            case "blue": return "#0000FF";
            case "yellow": return "#FFFF00";
            case "cyan": return "#00FFFF";
            case "magenta": return "#FF00FF";
            case "gray": return "#808080";
            case "darkgray": return "#404040";
            case "lightgray": return "#C0C0C0";
            case "orange": return "#FFA500";
            case "pink": return "#FFC0CB";
            case "brown": return "#A52A2A";
            case "purple": return "#800080";
            default: return "#000000"; // 默认黑色
        }
    }
    
    @Override
    public void applyTheme(String componentType, IChartStyleSupportModel model) {
        if (model == null) return;
        
        // 根据组件类型应用不同的主题样式
        if (model instanceof ChartShapeStyleModel) {
            applyShapeTheme(componentType, (ChartShapeStyleModel) model);
        }
    }
    
    /**
     * 应用形状主题
     */
    private void applyShapeTheme(String componentType, ChartShapeStyleModel shapeStyle) {
        if (shapeStyle == null) return;
        
        // 根据组件类型设置默认样式
        switch (componentType) {
            case "title":
                applyTitleTheme(shapeStyle);
                break;
            case "legend":
                applyLegendTheme(shapeStyle);
                break;
            case "axis":
                applyAxisTheme(shapeStyle);
                break;
            case "series":
                applySeriesTheme(shapeStyle);
                break;
            case "grid":
                applyGridTheme(shapeStyle);
                break;
            default:
                applyDefaultTheme(shapeStyle);
                break;
        }
    }
    
    /**
     * 应用标题主题
     */
    private void applyTitleTheme(ChartShapeStyleModel shapeStyle) {
        // 标题通常使用深色文本和透明背景
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType(ChartFillType.NONE); // 透明背景
            shapeStyle.setFill(fill);
        }
    }
    
    /**
     * 应用图例主题
     */
    private void applyLegendTheme(ChartShapeStyleModel shapeStyle) {
        // 图例通常使用浅色背景和细边框
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType(ChartFillType.SOLID);
            fill.setForegroundColor("#FFFFFF"); // 白色背景
            shapeStyle.setFill(fill);
        }
        
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#CCCCCC"); // 浅灰色边框
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用坐标轴主题
     */
    private void applyAxisTheme(ChartShapeStyleModel shapeStyle) {
        // 坐标轴通常使用黑色线条
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#000000"); // 黑色
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用数据系列主题
     */
    private void applySeriesTheme(ChartShapeStyleModel shapeStyle) {
        // 数据系列使用主题颜色，这里设置默认填充
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType(ChartFillType.SOLID);
            // 具体颜色由系列索引决定，这里设置默认值
            fill.setForegroundColor("#4472C4"); // 默认蓝色
            shapeStyle.setFill(fill);
        }
    }
    
    /**
     * 应用网格线主题
     */
    private void applyGridTheme(ChartShapeStyleModel shapeStyle) {
        // 网格线通常使用浅灰色
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#D0D0D0"); // 浅灰色
            border.setWidth(0.5);
            shapeStyle.setBorder(border);
        }
    }
    
    /**
     * 应用默认主题
     */
    private void applyDefaultTheme(ChartShapeStyleModel shapeStyle) {
        // 默认使用透明背景和黑色边框
        if (shapeStyle.getFill() == null) {
            ChartFillModel fill = new ChartFillModel();
            fill.setType(ChartFillType.NONE);
            shapeStyle.setFill(fill);
        }
        
        if (shapeStyle.getBorder() == null) {
            ChartBorderModel border = new ChartBorderModel();
            border.setColor("#000000");
            border.setWidth(1.0);
            shapeStyle.setBorder(border);
        }
    }
}