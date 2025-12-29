package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ColorModificationEngine - OOXML颜色变换引擎
 * <p>
 * 实现OOXML规范中的颜色修改功能，支持：
 * - lumMod (亮度调制) - 最关键，样本中最常用
 * - lumOff (亮度偏移) - 最关键，样本中最常用
 * - tint (色调) - 变亮
 * - shade (阴影) - 变暗
 * - alpha (透明度)
 * - sat (饱和度)
 * - satMod (饱和度调制)
 * - hue (色相偏移)
 * <p>
 * 基于图表样本分析，优先实现lumMod和lumOff，这是样本中最常见的变换：
 * - tx1 with lumMod=65000, lumOff=35000 (文本颜色)
 * - tx1 with lumMod=15000, lumOff=85000 (网格线和边框)
 */
public class ColorModificationEngine {
    private static final Logger LOG = LoggerFactory.getLogger(ColorModificationEngine.class);

    // OOXML颜色修改值的标准比例：100000 = 100%
    private static final double OOXML_SCALE = 100000.0;

    /**
     * 应用OOXML颜色修改
     * 处理样本中发现的最常见模式
     *
     * @param baseColor 基础颜色 (RGB hex 或主题颜色名称)
     * @param colorNode 包含修改元素的XML节点
     * @return 变换后的颜色 (RGB hex格式)
     */
    public static String applyModifications(String baseColor, XNode colorNode) {
        if (StringHelper.isEmpty(baseColor) || colorNode == null) {
            LOG.warn("Invalid input for color modifications: baseColor={}, colorNode={}", baseColor, colorNode);
            return baseColor;
        }


        // 解析颜色节点，提取基础颜色和修改列表
        ColorWithModifications colorInfo = parseColorNode(colorNode);
        if (colorInfo == null) {
            return baseColor;
        }

        // 使用解析出的基础颜色，如果有的话
        String workingColor = baseColor;

        // 转换为RGB颜色对象进行处理
        Color color = parseColor(workingColor);
        if (color == null) {
            LOG.warn("Failed to parse base color: {}", workingColor);
            return baseColor;
        }

        // 按OOXML规范顺序应用修改
        color = applyModificationsInOrder(color, colorInfo.modifications);

        // 转换回hex格式
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());

    }

    /**
     * 解析OOXML颜色节点，提取基础颜色和修改列表
     * 处理schemeClr和srgbClr两种模式
     */
    public static ColorWithModifications parseColorNode(XNode colorNode) {
        if (colorNode == null) {
            return null;
        }


        ColorWithModifications result = new ColorWithModifications();
        result.modifications = new ArrayList<>();

        // 检查是否是schemeClr节点
        if ("a:schemeClr".equals(colorNode.getTagName())) {
            String val = colorNode.attrText("val");
            if (!StringHelper.isEmpty(val)) {
                result.baseColor = val; // 主题颜色名称
            }
        }
        // 检查是否是srgbClr节点
        else if ("a:srgbClr".equals(colorNode.getTagName())) {
            String val = colorNode.attrText("val");
            if (!StringHelper.isEmpty(val)) {
                result.baseColor = "#" + val; // RGB颜色值
            }
        }

        // 解析所有修改子元素
        for (XNode child : colorNode.getChildren()) {
            ColorModification mod = parseModificationElement(child);
            if (mod != null) {
                result.modifications.add(mod);
            }
        }

        return result;

    }

    /**
     * 解析单个修改元素
     */
    private static ColorModification parseModificationElement(XNode element) {
        if (element == null) {
            return null;
        }

        String tagName = element.getTagName();
        String valStr = element.attrText("val");

        if (StringHelper.isEmpty(valStr)) {
            return null;
        }

        try {
            double value = Double.parseDouble(valStr) / OOXML_SCALE;

            switch (tagName) {
                case "a:lumMod":
                    return new ColorModification(ColorModificationType.LUMINANCE_MODULATION, value);
                case "a:lumOff":
                    return new ColorModification(ColorModificationType.LUMINANCE_OFFSET, value);
                case "a:tint":
                    return new ColorModification(ColorModificationType.TINT, value);
                case "a:shade":
                    return new ColorModification(ColorModificationType.SHADE, value);
                case "a:alpha":
                    return new ColorModification(ColorModificationType.ALPHA, value);
                case "a:sat":
                    return new ColorModification(ColorModificationType.SATURATION, value);
                case "a:satMod":
                    return new ColorModification(ColorModificationType.SATURATION_MODULATION, value);
                case "a:hue":
                    return new ColorModification(ColorModificationType.HUE_SHIFT, value);
                default:
                    LOG.debug("Unknown color modification element: {}", tagName);
                    return null;
            }
        } catch (NumberFormatException e) {
            LOG.warn("Invalid color modification value: {} for element: {}", valStr, tagName);
            return null;
        }
    }

    /**
     * 按OOXML规范顺序应用颜色修改
     */
    private static Color applyModificationsInOrder(Color color, List<ColorModification> modifications) {
        if (modifications == null || modifications.isEmpty()) {
            return color;
        }

        Color result = color;

        // 按OOXML规范顺序应用修改：
        // 1. 色相调整
        // 2. 饱和度调整  
        // 3. 亮度调整 (lumMod, lumOff) - 样本中最关键
        // 4. 色调/阴影调整
        // 5. 透明度调整

        for (ColorModification mod : modifications) {
            switch (mod.type) {
                case HUE_SHIFT:
                    result = applyHueShift(result, mod.value);
                    break;
                case SATURATION:
                    result = applySaturation(result, mod.value);
                    break;
                case SATURATION_MODULATION:
                    result = applySaturationModulation(result, mod.value);
                    break;
                case LUMINANCE_MODULATION:
                    result = applyLuminanceModulation(result, mod.value);
                    break;
                case LUMINANCE_OFFSET:
                    result = applyLuminanceOffset(result, mod.value);
                    break;
                case TINT:
                    result = applyTint(result, mod.value);
                    break;
                case SHADE:
                    result = applyShade(result, mod.value);
                    break;
                case ALPHA:
                    result = applyAlpha(result, mod.value);
                    break;
            }
        }

        return result;
    }

    /**
     * 应用亮度调制 (lumMod)
     * 最关键的变换，样本中最常用
     * 将颜色的亮度乘以指定值
     */
    public static Color applyLuminanceModulation(Color color, double lumModValue) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        // 调制亮度 (brightness)
        float newBrightness = (float) (hsb[2] * lumModValue);
        newBrightness = Math.max(0.0f, Math.min(1.0f, newBrightness)); // 限制在0-1范围

        return Color.getHSBColor(hsb[0], hsb[1], newBrightness);

    }

    /**
     * 应用亮度偏移 (lumOff)
     * 最关键的变换，样本中最常用
     * 在颜色的亮度上加上指定值
     */
    public static Color applyLuminanceOffset(Color color, double lumOffValue) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        // 偏移亮度 (brightness)
        float newBrightness = (float) (hsb[2] + lumOffValue);
        newBrightness = Math.max(0.0f, Math.min(1.0f, newBrightness)); // 限制在0-1范围

        return Color.getHSBColor(hsb[0], hsb[1], newBrightness);

    }

    /**
     * 应用色调 (tint) - 变亮
     */
    public static Color applyTint(Color color, double tintValue) {

        // Tint使颜色变亮，通过与白色混合
        int r = (int) (color.getRed() + (255 - color.getRed()) * tintValue);
        int g = (int) (color.getGreen() + (255 - color.getGreen()) * tintValue);
        int b = (int) (color.getBlue() + (255 - color.getBlue()) * tintValue);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return new Color(r, g, b);

    }

    /**
     * 应用阴影 (shade) - 变暗
     */
    public static Color applyShade(Color color, double shadeValue) {

        // Shade使颜色变暗，通过与黑色混合
        int r = (int) (color.getRed() * shadeValue);
        int g = (int) (color.getGreen() * shadeValue);
        int b = (int) (color.getBlue() * shadeValue);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return new Color(r, g, b);

    }

    /**
     * 应用透明度 (alpha)
     */
    public static Color applyAlpha(Color color, double alphaValue) {

        int alpha = (int) (255 * alphaValue);
        alpha = Math.max(0, Math.min(255, alpha));

        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);

    }

    /**
     * 应用饱和度 (sat)
     */
    public static Color applySaturation(Color color, double satValue) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        float newSaturation = (float) satValue;
        newSaturation = Math.max(0.0f, Math.min(1.0f, newSaturation));

        return Color.getHSBColor(hsb[0], newSaturation, hsb[2]);

    }

    /**
     * 应用饱和度调制 (satMod)
     */
    public static Color applySaturationModulation(Color color, double satModValue) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        float newSaturation = (float) (hsb[1] * satModValue);
        newSaturation = Math.max(0.0f, Math.min(1.0f, newSaturation));

        return Color.getHSBColor(hsb[0], newSaturation, hsb[2]);

    }

    /**
     * 应用色相偏移 (hue)
     */
    public static Color applyHueShift(Color color, double hueValue) {

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        // 色相是循环的，范围0-1
        float newHue = (float) ((hsb[0] + hueValue) % 1.0);
        if (newHue < 0) newHue += 1.0f;

        return Color.getHSBColor(newHue, hsb[1], hsb[2]);

    }

    /**
     * 解析颜色字符串为Color对象
     */
    private static Color parseColor(String colorStr) {
        if (StringHelper.isEmpty(colorStr)) {
            return null;
        }


        // 处理hex颜色
        if (colorStr.startsWith("#")) {
            return Color.decode(colorStr);
        }

        // 处理主题颜色名称 - 需要通过样式提供者解析
        // 这里返回null，让调用者处理主题颜色解析
        return null;
    }

    /**
     * 颜色修改信息容器
     */
    public static class ColorWithModifications {
        public String baseColor;
        public List<ColorModification> modifications;
    }

    /**
     * 单个颜色修改
     */
    public static class ColorModification {
        public final ColorModificationType type;
        public final double value;

        public ColorModification(ColorModificationType type, double value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * 颜色修改类型枚举
     */
    public enum ColorModificationType {
        LUMINANCE_MODULATION,    // lumMod - 最关键
        LUMINANCE_OFFSET,        // lumOff - 最关键
        TINT,                    // tint
        SHADE,                   // shade
        ALPHA,                   // alpha
        SATURATION,              // sat
        SATURATION_MODULATION,   // satMod
        HUE_SHIFT               // hue
    }
}