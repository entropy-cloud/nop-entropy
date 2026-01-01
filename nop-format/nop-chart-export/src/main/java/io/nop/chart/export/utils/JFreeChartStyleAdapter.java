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
import java.awt.geom.*;
import java.util.HashMap;

/**
 * Adapter for converting chart styles to JFreeChart format
 */
public class JFreeChartStyleAdapter {
    
    // 预定义形状映射
    private static final HashMap<String, Shape> SHAPE_TYPES = new HashMap<>();
    
    static {
        // 初始化预定义形状
        SHAPE_TYPES.put("circle", new Ellipse2D.Double(-4, -4, 8, 8));
        SHAPE_TYPES.put("square", new Rectangle(-4, -4, 8, 8));
        SHAPE_TYPES.put("diamond", new Polygon(
            new int[]{0, 4, 0, -4},
            new int[]{-4, 0, 4, 0},
            4
        ));
        SHAPE_TYPES.put("triangle", new Polygon(
            new int[]{0, 4, -4},
            new int[]{-4, 4, 4},
            3
        ));
        SHAPE_TYPES.put("cross", createCrossShape());
        SHAPE_TYPES.put("star", createStarShape(5, 4, 2));
    }
    
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
     * 创建渐变填充Paint对象
     * @param startColor 起始颜色
     * @param endColor 结束颜色
     * @param x1 起始X坐标
     * @param y1 起始Y坐标
     * @param x2 结束X坐标
     * @param y2 结束Y坐标
     * @return 渐变填充Paint对象
     */
    public static Paint createGradientPaint(String startColor, String endColor, 
                                          float x1, float y1, float x2, float y2) {
        if (StringHelper.isEmpty(startColor) || StringHelper.isEmpty(endColor)) {
            return null;
        }
        
        Color start = convertColor(startColor);
        Color end = convertColor(endColor);
        
        if (start == null || end == null) {
            return null;
        }
        
        return new GradientPaint(x1, y1, start, x2, y2, end);
    }
    
    /**
     * 获取预定义形状
     * @param shapeName 形状名称
     * @return Shape对象
     */
    public static Shape getPredefinedShape(String shapeName) {
        return SHAPE_TYPES.get(shapeName);
    }
    
    /**
     * 创建自定义形状
     * @param shapeType 形状类型
     * @param width 宽度
     * @param height 高度
     * @return Shape对象
     */
    public static Shape createShape(String shapeType, float width, float height) {
        if (StringHelper.isEmpty(shapeType)) {
            return new Ellipse2D.Double(-width/2, -height/2, width, height);
        }
        
        Shape shape = SHAPE_TYPES.get(shapeType.toLowerCase());
        if (shape != null) {
            return shape;
        }
        
        // 默认返回圆形
        return new Ellipse2D.Double(-width/2, -height/2, width, height);
    }
    
    /**
     * 创建十字形
     * @return 十字形Shape对象
     */
    private static Shape createCrossShape() {
        GeneralPath path = new GeneralPath();
        path.moveTo(-3, -3);
        path.lineTo(-1, -3);
        path.lineTo(-1, -1);
        path.lineTo(1, -1);
        path.lineTo(1, -3);
        path.lineTo(3, -3);
        path.lineTo(3, -1);
        path.lineTo(1, -1);
        path.lineTo(1, 1);
        path.lineTo(3, 1);
        path.lineTo(3, 3);
        path.lineTo(1, 3);
        path.lineTo(1, 1);
        path.lineTo(-1, 1);
        path.lineTo(-1, 3);
        path.lineTo(-3, 3);
        path.lineTo(-3, 1);
        path.lineTo(-1, 1);
        path.lineTo(-1, -1);
        path.lineTo(-3, -1);
        path.closePath();
        return path;
    }
    
    /**
     * 创建星形
     * @param points 点数
     * @param outerRadius 外半径
     * @param innerRadius 内半径
     * @return 星形Shape对象
     */
    private static Shape createStarShape(int points, double outerRadius, double innerRadius) {
        GeneralPath path = new GeneralPath();
        double angle = Math.PI / points;
        
        for (int i = 0; i < 2 * points; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            double theta = i * angle;
            double x = r * Math.cos(theta);
            double y = r * Math.sin(theta);
            
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
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