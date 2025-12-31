package io.nop.chart.export.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartLineStyleModel;
import io.nop.excel.chart.model.ChartSpacingModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.color.ColorHelper;
import org.jfree.chart.ui.RectangleInsets;

import java.awt.*;

/**
 * Adapter for converting chart styles to JFreeChart format
 */
public class JFreeChartStyleAdapter {
    
    /**
     * 转换颜色字符串为Paint对象
     * @param colorStr 颜色字符串
     * @param opacity 透明度 (0.0-1.0)
     * @return Paint对象
     */
    public static Paint toPaint(String colorStr, double opacity) {
        if (StringHelper.isEmpty(colorStr)) {
            return null;
        }
        
        int argbInt = ColorHelper.toArgbInt(colorStr);
        if (argbInt == 0) {
            return null;
        }
        
        Color color = new Color(argbInt, true);
        
        if (opacity >= 0.0 && opacity <= 1.0 && opacity != 1.0) {
            int alpha = (int) (opacity * 255);
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
        
        return color;
    }
    
    /**
     * 转换颜色字符串为Paint对象（默认不透明）
     * @param colorStr 颜色字符串
     * @return Paint对象
     */
    public static Paint toPaint(String colorStr) {
        return toPaint(colorStr, 1.0);
    }
    
    /**
     * 转换字体模型为Font对象
     * @param fontModel 字体模型
     * @return Font对象
     */
    public static Font toFont(ExcelFont fontModel) {
        if (fontModel == null) {
            return null;
        }
        
        String fontName = StringHelper.toString(fontModel.getFontName(), "SansSerif");
        Integer fontSizeInt = ConvertHelper.toInt(fontModel.getFontSize(), err -> new NopException(err));
        int fontSize = fontSizeInt != null ? fontSizeInt : 12;
        
        int style = Font.PLAIN;
        if (Boolean.TRUE.equals(fontModel.isBold())) {
            style |= Font.BOLD;
        }
        if (Boolean.TRUE.equals(fontModel.isItalic())) {
            style |= Font.ITALIC;
        }
        
        return new Font(fontName, style, fontSize);
    }
    
    /**
     * 转换线条样式为Stroke对象
     * @param lineStyle 线条样式模型
     * @return Stroke对象
     */
    public static Stroke toStroke(ChartLineStyleModel lineStyle) {
        if (lineStyle == null) {
            return new BasicStroke();
        }
        
        Float widthFloat = ConvertHelper.toFloat(lineStyle.getWidth(), err -> new NopException(err));
        float width = widthFloat != null ? widthFloat : 1.0f;
        
        // 根据线条样式设置虚线模式
        float[] dashArray = null;
        if (lineStyle.getStyle() != null) {
            switch (lineStyle.getStyle()) {
                case DASH:
                    dashArray = new float[]{5.0f, 5.0f};
                    break;
                case DOT:
                    dashArray = new float[]{2.0f, 2.0f};
                    break;
                case DASH_DOT:
                    dashArray = new float[]{5.0f, 2.0f, 2.0f, 2.0f};
                    break;
                default:
                    // SOLID or null - no dash
                    break;
            }
        }
        
        if (dashArray != null) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dashArray, 0);
        } else {
            return new BasicStroke(width);
        }
    }
    
    /**
     * 转换间距模型为RectangleInsets对象
     * @param spacing 间距模型
     * @return RectangleInsets对象
     */
    public static RectangleInsets toInsets(ChartSpacingModel spacing) {
        if (spacing == null) {
            return RectangleInsets.ZERO_INSETS;
        }
        
        Double topDouble = ConvertHelper.convertTo(Double.class, spacing.getTop(), NopException::new);
        Double leftDouble = ConvertHelper.convertTo(Double.class, spacing.getLeft(), NopException::new);
        Double bottomDouble = ConvertHelper.convertTo(Double.class, spacing.getBottom(), NopException::new);
        Double rightDouble = ConvertHelper.convertTo(Double.class, spacing.getRight(), NopException::new);
        
        double top = topDouble != null ? topDouble : 0.0;
        double left = leftDouble != null ? leftDouble : 0.0;
        double bottom = bottomDouble != null ? bottomDouble : 0.0;
        double right = rightDouble != null ? rightDouble : 0.0;
        
        return new RectangleInsets(top, left, bottom, right);
    }
    
    /**
     * 从填充模型创建Paint对象
     * @param fillModel 填充模型
     * @return Paint对象
     */
    public static Paint createFillPaint(ChartFillModel fillModel) {
        if (fillModel == null) {
            return null;
        }
        
        String backgroundColor = fillModel.getBackgroundColor();
        String foregroundColor = fillModel.getForegroundColor();
        Double opacityDouble = ConvertHelper.convertTo(Double.class, fillModel.getOpacity(), NopException::new);
        double opacity = opacityDouble != null ? opacityDouble : 1.0;
        
        // 优先使用前景色，如果没有则使用背景色
        String colorStr = StringHelper.isNotEmpty(foregroundColor) ? foregroundColor : backgroundColor;
        
        return toPaint(colorStr, opacity);
    }
    
    /**
     * 从边框模型创建Stroke对象
     * @param borderModel 边框模型
     * @return Stroke对象
     */
    public static Stroke createBorderStroke(ChartBorderModel borderModel) {
        if (borderModel == null) {
            return null;
        }
        
        Float widthFloat = ConvertHelper.toFloat(borderModel.getWidth(), err -> new NopException(err));
        float width = widthFloat != null ? widthFloat : 1.0f;
        
        // 根据边框样式设置虚线模式
        float[] dashArray = null;
        if (borderModel.getStyle() != null) {
            switch (borderModel.getStyle()) {
                case DASH:
                    dashArray = new float[]{5.0f, 5.0f};
                    break;
                case DOT:
                    dashArray = new float[]{2.0f, 2.0f};
                    break;
                case DASH_DOT:
                    dashArray = new float[]{5.0f, 2.0f, 2.0f, 2.0f};
                    break;
                default:
                    // SOLID or null - no dash
                    break;
            }
        }
        
        if (dashArray != null) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dashArray, 0);
        } else {
            return new BasicStroke(width);
        }
    }
    
    /**
     * 从边框模型创建Paint对象
     * @param borderModel 边框模型
     * @return Paint对象
     */
    public static Paint createBorderPaint(ChartBorderModel borderModel) {
        if (borderModel == null) {
            return null;
        }
        
        Double opacityDouble = ConvertHelper.convertTo(Double.class, borderModel.getOpacity(), NopException::new);
        double opacity = opacityDouble != null ? opacityDouble : 1.0;
        return toPaint(borderModel.getColor(), opacity);
    }
    
    /**
     * 转换颜色字符串为Color对象
     * @param colorStr 颜色字符串
     * @return Color对象
     */
    public static Color convertColor(String colorStr) {
        Paint paint = toPaint(colorStr);
        return paint instanceof Color ? (Color) paint : null;
    }
    
    /**
     * 转换字体模型为Font对象（兼容方法）
     * @param textStyle 文本样式模型
     * @return Font对象
     */
    public static Font convertFont(io.nop.excel.chart.model.ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            return null;
        }
        return toFont(textStyle.getFont());
    }
}