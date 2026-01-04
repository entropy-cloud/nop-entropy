package io.nop.chart.export.utils;

import io.nop.commons.util.StringHelper;
import io.nop.excel.chart.constants.ChartFillPatternType;
import io.nop.excel.chart.constants.ChartFillType;
import io.nop.excel.chart.constants.ChartGradientDirection;
import io.nop.excel.chart.constants.ChartMarkerType;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.excel.chart.model.ChartLineStyleModel;
import io.nop.excel.chart.model.ChartSpacingModel;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.color.ColorHelper;
import org.jfree.chart.ui.RectangleInsets;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/**
 * Adapter for converting chart styles to JFreeChart format
 */
public class JFreeChartStyleAdapter {

    // 预定义形状映射
    private static final HashMap<String, Shape> SHAPE_TYPES = new HashMap<>();

    static {
        // 初始化预定义形状 - 支持所有ChartMarkerType枚举值
        SHAPE_TYPES.put("circle", new Ellipse2D.Double(-4, -4, 8, 8));
        SHAPE_TYPES.put("square", new Rectangle2D.Double(-4, -4, 8, 8));
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
        SHAPE_TYPES.put("x", createXShape());
        SHAPE_TYPES.put("star", createStarShape(5, 4, 2));
        SHAPE_TYPES.put("plus", createPlusShape());
        SHAPE_TYPES.put("dash", createDashShape());
        SHAPE_TYPES.put("dot", new Ellipse2D.Double(-2, -2, 4, 4));
        
        // 添加别名支持
        SHAPE_TYPES.put("cross", createXShape()); // X的别名
        SHAPE_TYPES.put("auto", new Ellipse2D.Double(-4, -4, 8, 8)); // AUTO默认使用圆形
        SHAPE_TYPES.put("none", null); // NONE不显示标记
    }

    /**
     * 转换颜色字符串为Paint对象
     *
     * @param colorStr 颜色字符串
     * @param opacity  透明度 (0.0-1.0)
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
     *
     * @param startColor 起始颜色
     * @param endColor   结束颜色
     * @param x1         起始X坐标
     * @param y1         起始Y坐标
     * @param x2         结束X坐标
     * @param y2         结束Y坐标
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
     *
     * @param shapeName 形状名称
     * @return Shape对象
     */
    public static Shape getPredefinedShape(String shapeName) {
        return SHAPE_TYPES.get(shapeName);
    }

    /**
     * 创建自定义形状
     *
     * @param shapeType 形状类型
     * @param width     宽度
     * @param height    高度
     * @return Shape对象
     */
    public static Shape createShape(String shapeType, float width, float height) {
        if (StringHelper.isEmpty(shapeType)) {
            return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
        }

        Shape shape = SHAPE_TYPES.get(shapeType.toLowerCase());
        if (shape != null) {
            return shape;
        }

        // 尝试从ChartMarkerType中查找
        ChartMarkerType markerType = ChartMarkerType.fromValue(shapeType);
        if (markerType != null && markerType != ChartMarkerType.NONE) {
            // 使用默认大小和索引0创建标记形状
            return createMarkerShape(markerType, Math.min(width, height), 0);
        }

        // 默认返回圆形
        return new Ellipse2D.Double(-width / 2, -height / 2, width, height);
    }

    /**
     * 创建十字形
     *
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
     *
     * @param points      点数
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
     *
     * @param colorStr 颜色字符串
     * @return Paint对象
     */
    public static Paint toPaint(String colorStr) {
        return toPaint(colorStr, 1.0);
    }

    /**
     * 转换字体模型为Font对象
     *
     * @param fontModel 字体模型
     * @return Font对象
     */
    public static Font toFont(ExcelFont fontModel) {
        if (fontModel == null) {
            return null;
        }

        String fontName = StringHelper.toString(fontModel.getFontName(), "SansSerif");
        Float fontSizeValue = fontModel.getFontSize();
        int fontSize = fontSizeValue != null ? fontSizeValue.intValue() : 12;

        int style = Font.PLAIN;
        if (fontModel.isBold()) {
            style |= Font.BOLD;
        }
        if (fontModel.isItalic()) {
            style |= Font.ITALIC;
        }

        return new Font(fontName, style, fontSize);
    }

    /**
     * 转换线条样式为Stroke对象
     *
     * @param lineStyle 线条样式模型
     * @return Stroke对象
     */
    public static Stroke toStroke(ChartLineStyleModel lineStyle) {
        if (lineStyle == null) {
            return new BasicStroke();
        }

        Double widthFloat = lineStyle.getWidth();
        float width = widthFloat != null ? widthFloat.floatValue() : 1.0f;

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
     *
     * @param spacing 间距模型
     * @return RectangleInsets对象
     */
    public static RectangleInsets toInsets(ChartSpacingModel spacing) {
        if (spacing == null) {
            return RectangleInsets.ZERO_INSETS;
        }

        Double topDouble = spacing.getTop();
        Double leftDouble = spacing.getLeft();
        Double bottomDouble = spacing.getBottom();
        Double rightDouble = spacing.getRight();

        double top = topDouble != null ? topDouble : 0.0;
        double left = leftDouble != null ? leftDouble : 0.0;
        double bottom = bottomDouble != null ? bottomDouble : 0.0;
        double right = rightDouble != null ? rightDouble : 0.0;

        return new RectangleInsets(top, left, bottom, right);
    }

    /**
     * 从填充模型创建Paint对象
     *
     * @param fillModel 填充模型
     * @return Paint对象
     */
    public static Paint createFillPaint(ChartFillModel fillModel) {
        if (fillModel == null) {
            return null;
        }

        ChartFillType fillType = fillModel.getType();
        if (fillType == null) {
            // 默认使用纯色填充
            return createSolidFillPaint(fillModel);
        }

        switch (fillType) {
            case NONE:
                return null;
            case SOLID:
                return createSolidFillPaint(fillModel);
            case GRADIENT:
                return createGradientFillPaint(fillModel);
            case PATTERN:
                return createPatternFillPaint(fillModel);
            case PICTURE:
                return createPictureFillPaint(fillModel);
            default:
                return createSolidFillPaint(fillModel);
        }
    }

    /**
     * 创建纯色填充Paint对象
     *
     * @param fillModel 填充模型
     * @return Paint对象
     */
    private static Paint createSolidFillPaint(ChartFillModel fillModel) {
        String backgroundColor = fillModel.getBackgroundColor();
        String foregroundColor = fillModel.getForegroundColor();
        Double opacityDouble = fillModel.getOpacity();
        double opacity = opacityDouble != null ? opacityDouble : 1.0;

        // 优先使用前景色，如果没有则使用背景色
        String colorStr = StringHelper.isNotEmpty(foregroundColor) ? foregroundColor : backgroundColor;

        return toPaint(colorStr, opacity);
    }

    /**
     * 创建渐变填充Paint对象
     *
     * @param fillModel 填充模型
     * @return Paint对象
     */
    private static Paint createGradientFillPaint(ChartFillModel fillModel) {
        if (fillModel.getGradient() == null) {
            return createSolidFillPaint(fillModel);
        }

        ChartGradientModel gradient = fillModel.getGradient();
        if (gradient.getEnabled() != null && !gradient.getEnabled()) {
            return createSolidFillPaint(fillModel);
        }

        String startColor = gradient.getStartColor();
        String endColor = gradient.getEndColor();

        if (StringHelper.isEmpty(startColor) || StringHelper.isEmpty(endColor)) {
            return createSolidFillPaint(fillModel);
        }

        ChartGradientDirection direction = gradient.getDirection();
        Double angle = gradient.getAngle();

        // 根据方向或角度创建渐变
        if (direction != null) {
            switch (direction) {
                case HORIZONTAL:
                    return createLinearGradient(startColor, endColor, 0f, 0f, 1f, 0f, fillModel.getOpacity());
                case VERTICAL:
                    return createLinearGradient(startColor, endColor, 0f, 0f, 0f, 1f, fillModel.getOpacity());
                case DIAGONAL_UP:
                    return createLinearGradient(startColor, endColor, 0f, 1f, 1f, 0f, fillModel.getOpacity());
                case DIAGONAL_DOWN:
                    return createLinearGradient(startColor, endColor, 0f, 0f, 1f, 1f, fillModel.getOpacity());
                case FROM_CENTER:
                case FROM_CORNER:
                    // 径向渐变暂不支持，降级为线性渐变
                    return createLinearGradient(startColor, endColor, 0f, 0f, 1f, 1f, fillModel.getOpacity());
                default:
                    if (angle != null) {
                        return createLinearGradientWithAngle(startColor, endColor, angle.floatValue(), fillModel.getOpacity());
                    }
                    return createLinearGradient(startColor, endColor, 0f, 0f, 1f, 1f, fillModel.getOpacity());
            }
        } else if (angle != null) {
            return createLinearGradientWithAngle(startColor, endColor, angle.floatValue(), fillModel.getOpacity());
        }

        // 默认对角线渐变
        return createLinearGradient(startColor, endColor, 0f, 0f, 1f, 1f, fillModel.getOpacity());
    }

    /**
     * 创建线性渐变Paint对象
     *
     * @param startColor 起始颜色
     * @param endColor   结束颜色
     * @param x1         起始X坐标 (0.0-1.0)
     * @param y1         起始Y坐标 (0.0-1.0)
     * @param x2         结束X坐标 (0.0-1.0)
     * @param y2         结束Y坐标 (0.0-1.0)
     * @param opacity    透明度
     * @return 渐变Paint对象
     */
    private static Paint createLinearGradient(String startColor, String endColor,
                                              float x1, float y1, float x2, float y2, Double opacity) {
        Color start = convertColorWithOpacity(startColor, opacity);
        Color end = convertColorWithOpacity(endColor, opacity);

        if (start == null || end == null) {
            return null;
        }

        return new GradientPaint(x1, y1, start, x2, y2, end);
    }

    /**
     * 根据角度创建线性渐变Paint对象
     *
     * @param startColor 起始颜色
     * @param endColor   结束颜色
     * @param angle      角度 (0-360度)
     * @param opacity    透明度
     * @return 渐变Paint对象
     */
    private static Paint createLinearGradientWithAngle(String startColor, String endColor,
                                                       float angle, Double opacity) {
        // 将角度转换为弧度
        double radians = Math.toRadians(angle);

        // 计算渐变方向向量
        float x2 = (float) Math.cos(radians);
        float y2 = (float) Math.sin(radians);

        // 归一化到0-1范围
        float length = (float) Math.sqrt(x2 * x2 + y2 * y2);
        if (length > 0) {
            x2 /= length;
            y2 /= length;
        }

        return createLinearGradient(startColor, endColor, 0f, 0f, x2, y2, opacity);
    }

    /**
     * 转换颜色字符串为Color对象，并应用透明度
     *
     * @param colorStr 颜色字符串
     * @param opacity  透明度
     * @return Color对象
     */
    private static Color convertColorWithOpacity(String colorStr, Double opacity) {
        if (StringHelper.isEmpty(colorStr)) {
            return null;
        }

        int argbInt = ColorHelper.toArgbInt(colorStr);
        if (argbInt == 0) {
            return null;
        }

        Color color = new Color(argbInt, true);

        if (opacity != null && opacity >= 0.0 && opacity <= 1.0 && opacity != 1.0) {
            int alpha = (int) (opacity * 255);
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        return color;
    }

    /**
     * 创建图案填充Paint对象
     *
     * @param fillModel 填充模型
     * @return Paint对象
     */
    private static Paint createPatternFillPaint(ChartFillModel fillModel) {
        ChartFillPatternType patternType = fillModel.getPattern();
        if (patternType == null) {
            return createSolidFillPaint(fillModel);
        }

        String backgroundColor = fillModel.getBackgroundColor();
        String foregroundColor = fillModel.getForegroundColor();
        Double opacity = fillModel.getOpacity();

        // 优先使用前景色作为图案颜色，背景色作为底色
        String patternColorStr = StringHelper.isNotEmpty(foregroundColor) ? foregroundColor : backgroundColor;
        String backgroundColorStr = StringHelper.isNotEmpty(backgroundColor) ? backgroundColor : "#FFFFFF";

        Color patternColor = convertColorWithOpacity(patternColorStr, opacity);
        Color backgroundColorColor = convertColorWithOpacity(backgroundColorStr, opacity);

        if (patternColor == null) {
            return createSolidFillPaint(fillModel);
        }

        // 创建图案图像
        BufferedImage patternImage = createPatternImage(patternType, patternColor, backgroundColorColor);
        if (patternImage == null) {
            return createSolidFillPaint(fillModel);
        }

        // 创建纹理填充
        Rectangle2D anchor = new Rectangle2D.Float(0, 0, patternImage.getWidth(), patternImage.getHeight());
        return new TexturePaint(patternImage, anchor);
    }

    /**
     * 创建图案图像
     *
     * @param patternType     图案类型
     * @param patternColor    图案颜色
     * @param backgroundColor 背景颜色
     * @return BufferedImage对象
     */
    private static BufferedImage createPatternImage(ChartFillPatternType patternType,
                                                    Color patternColor, Color backgroundColor) {
        int size = 8; // 图案大小
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 设置背景
        if (backgroundColor != null) {
            g2d.setColor(backgroundColor);
            g2d.fillRect(0, 0, size, size);
        }

        g2d.setColor(patternColor);

        switch (patternType) {
            case PERCENT_5:
                drawDotsPattern(g2d, size, 0.05f);
                break;
            case PERCENT_10:
                drawDotsPattern(g2d, size, 0.10f);
                break;
            case PERCENT_20:
                drawDotsPattern(g2d, size, 0.20f);
                break;
            case PERCENT_25:
                drawDotsPattern(g2d, size, 0.25f);
                break;
            case PERCENT_30:
                drawDotsPattern(g2d, size, 0.30f);
                break;
            case PERCENT_40:
                drawDotsPattern(g2d, size, 0.40f);
                break;
            case PERCENT_50:
                drawDotsPattern(g2d, size, 0.50f);
                break;
            case PERCENT_60:
                drawDotsPattern(g2d, size, 0.60f);
                break;
            case PERCENT_70:
                drawDotsPattern(g2d, size, 0.70f);
                break;
            case PERCENT_75:
                drawDotsPattern(g2d, size, 0.75f);
                break;
            case PERCENT_80:
                drawDotsPattern(g2d, size, 0.80f);
                break;
            case PERCENT_90:
                drawDotsPattern(g2d, size, 0.90f);
                break;
            case HORIZONTAL_STRIPE:
                drawHorizontalStripePattern(g2d, size);
                break;
            case VERTICAL_STRIPE:
                drawVerticalStripePattern(g2d, size);
                break;
            case UPWARD_DIAGONAL:
                drawDiagonalStripePattern(g2d, size);
                break;
            case DOWNWARD_DIAGONAL:
                drawReverseDiagonalStripePattern(g2d, size);
                break;
            case DIAGONAL_CROSS:
                drawDiagonalCrossPattern(g2d, size);
                break;
            case CROSS:
                drawCrossPattern(g2d, size);
                break;
            case THICK_HORIZONTAL:
                drawDarkHorizontalPattern(g2d, size);
                break;
            case THICK_VERTICAL:
                drawDarkVerticalPattern(g2d, size);
                break;
            case THICK_UPWARD_DIAGONAL:
                drawDarkDiagonalPattern(g2d, size);
                break;
            case THICK_DOWNWARD_DIAGONAL:
                drawDarkReverseDiagonalPattern(g2d, size);
                break;
            case LARGE_CONFETTI:
                drawLargeSpotPattern(g2d, size);
                break;
            case LARGE_CHECKER:
                drawCheckerBoardPattern(g2d, size);
                break;
            default:
                // 默认使用点状图案
                drawDotsPattern(g2d, size, 0.5f);
                break;
        }

        g2d.dispose();
        return image;
    }

    /**
     * 绘制点状图案
     */
    private static void drawDotsPattern(Graphics2D g2d, int size, float density) {
        int dotSize = 1;
        int spacing = Math.max(1, (int) (1 / density));

        for (int y = 0; y < size; y += spacing) {
            for (int x = 0; x < size; x += spacing) {
                g2d.fillRect(x, y, dotSize, dotSize);
            }
        }
    }

    /**
     * 绘制水平条纹图案
     */
    private static void drawHorizontalStripePattern(Graphics2D g2d, int size) {
        g2d.fillRect(0, 0, size, 1);
        g2d.fillRect(0, 2, size, 1);
    }

    /**
     * 绘制垂直条纹图案
     */
    private static void drawVerticalStripePattern(Graphics2D g2d, int size) {
        g2d.fillRect(0, 0, 1, size);
        g2d.fillRect(2, 0, 1, size);
    }

    /**
     * 绘制对角条纹图案
     */
    private static void drawDiagonalStripePattern(Graphics2D g2d, int size) {
        for (int i = -size; i < size; i += 2) {
            g2d.drawLine(i, 0, i + size, size);
        }
    }

    /**
     * 绘制反向对角条纹图案
     */
    private static void drawReverseDiagonalStripePattern(Graphics2D g2d, int size) {
        for (int i = 0; i < 2 * size; i += 2) {
            g2d.drawLine(i, 0, i - size, size);
        }
    }

    /**
     * 绘制对角交叉图案
     */
    private static void drawDiagonalCrossPattern(Graphics2D g2d, int size) {
        drawDiagonalStripePattern(g2d, size);
        drawReverseDiagonalStripePattern(g2d, size);
    }

    /**
     * 绘制细对角条纹图案
     */
    private static void drawThinDiagonalStripePattern(Graphics2D g2d, int size) {
        for (int i = -size; i < size; i += 1) {
            g2d.drawLine(i, 0, i + size, size);
        }
    }

    /**
     * 绘制细反向对角条纹图案
     */
    private static void drawThinReverseDiagonalStripePattern(Graphics2D g2d, int size) {
        for (int i = 0; i < 2 * size; i += 1) {
            g2d.drawLine(i, 0, i - size, size);
        }
    }

    /**
     * 创建图片填充Paint对象
     *
     * @param fillModel 填充模型
     * @return Paint对象
     */
    private static Paint createPictureFillPaint(ChartFillModel fillModel) {
        if (fillModel.getPicture() == null) {
            return createSolidFillPaint(fillModel);
        }

        // 这里需要实现图片填充逻辑
        // 暂时返回纯色填充作为降级方案
        return createSolidFillPaint(fillModel);
    }

    /**
     * 从边框模型创建Stroke对象
     *
     * @param borderModel 边框模型
     * @return Stroke对象
     */
    public static Stroke createBorderStroke(ChartBorderModel borderModel) {
        if (borderModel == null) {
            return null;
        }

        Double widthFloat = borderModel.getWidth();
        float width = widthFloat != null ? widthFloat.floatValue() : 1.0f;

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
     *
     * @param borderModel 边框模型
     * @return Paint对象
     */
    public static Paint createBorderPaint(ChartBorderModel borderModel) {
        if (borderModel == null) {
            return null;
        }

        Double opacityDouble = borderModel.getOpacity();
        double opacity = opacityDouble != null ? opacityDouble : 1.0;
        return toPaint(borderModel.getColor(), opacity);
    }

    /**
     * 转换颜色字符串为Color对象
     *
     * @param colorStr 颜色字符串
     * @return Color对象
     */
    public static Color convertColor(String colorStr) {
        Paint paint = toPaint(colorStr);
        return paint instanceof Color ? (Color) paint : null;
    }

    /**
     * 转换字体模型为Font对象（兼容方法）
     *
     * @param textStyle 文本样式模型
     * @return Font对象
     */
    public static Font convertFont(io.nop.excel.chart.model.ChartTextStyleModel textStyle) {
        if (textStyle == null) {
            return null;
        }
        return toFont(textStyle.getFont());
    }

    /**
     * 绘制十字交叉图案
     */
    private static void drawCrossPattern(Graphics2D g2d, int size) {
        g2d.fillRect(0, size / 2 - 1, size, 2);
        g2d.fillRect(size / 2 - 1, 0, 2, size);
    }

    /**
     * 绘制深色水平条纹图案
     */
    private static void drawDarkHorizontalPattern(Graphics2D g2d, int size) {
        for (int y = 0; y < size; y += 2) {
            g2d.fillRect(0, y, size, 1);
        }
    }

    /**
     * 绘制深色垂直条纹图案
     */
    private static void drawDarkVerticalPattern(Graphics2D g2d, int size) {
        for (int x = 0; x < size; x += 2) {
            g2d.fillRect(x, 0, 1, size);
        }
    }

    /**
     * 绘制深色对角条纹图案
     */
    private static void drawDarkDiagonalPattern(Graphics2D g2d, int size) {
        g2d.setStroke(new BasicStroke(2));
        for (int i = -size; i < size; i += 2) {
            g2d.drawLine(i, 0, i + size, size);
        }
        g2d.setStroke(new BasicStroke(1));
    }

    /**
     * 绘制深色反向对角条纹图案
     */
    private static void drawDarkReverseDiagonalPattern(Graphics2D g2d, int size) {
        g2d.setStroke(new BasicStroke(2));
        for (int i = 0; i < 2 * size; i += 2) {
            g2d.drawLine(i, 0, i - size, size);
        }
        g2d.setStroke(new BasicStroke(1));
    }


    /**
     * 绘制大点图案
     */
    private static void drawLargeSpotPattern(Graphics2D g2d, int size) {
        int dotSize = 2;
        for (int y = 0; y < size; y += 4) {
            for (int x = 0; x < size; x += 4) {
                g2d.fillRect(x, y, dotSize, dotSize);
            }
        }
    }

    /**
     * 绘制方格图案
     */
    private static void drawCheckerBoardPattern(Graphics2D g2d, int size) {
        int blockSize = size / 4;
        for (int y = 0; y < size; y += blockSize) {
            for (int x = 0; x < size; x += blockSize) {
                if ((x / blockSize + y / blockSize) % 2 == 0) {
                    g2d.fillRect(x, y, blockSize, blockSize);
                }
            }
        }
    }

    /**
     * 创建X形标记
     *
     * @return X形Shape对象
     */
    private static Shape createXShape() {
        GeneralPath path = new GeneralPath();
        path.moveTo(-3, -3);
        path.lineTo(3, 3);
        path.moveTo(-3, 3);
        path.lineTo(3, -3);
        return path;
    }

    /**
     * 创建加号形标记
     *
     * @return 加号形Shape对象
     */
    private static Shape createPlusShape() {
        GeneralPath path = new GeneralPath();
        path.moveTo(0, -3);
        path.lineTo(0, 3);
        path.moveTo(-3, 0);
        path.lineTo(3, 0);
        return path;
    }

    /**
     * 创建短横线形标记
     *
     * @return 短横线形Shape对象
     */
    private static Shape createDashShape() {
        return new Line2D.Double(-3, 0, 3, 0);
    }

    /**
     * 根据ChartMarkerType创建形状
     *
     * @param markerType 标记类型
     * @param size       标记大小
     * @param index      系列索引（用于AUTO类型选择）
     * @return Shape对象
     */
    public static Shape createMarkerShape(ChartMarkerType markerType, float size, int index) {
        if (markerType == null || markerType == ChartMarkerType.NONE) {
            return null;
        }

        // 处理AUTO类型
        ChartMarkerType actualType = markerType;
        if (markerType == ChartMarkerType.AUTO) {
            actualType = markerType.getAutoType(index);
        }

        // 获取基础形状
        Shape shape = getPredefinedShape(actualType.value());
        if (shape == null) {
            // 默认返回圆形
            return new Ellipse2D.Double(-size / 2, -size / 2, size, size);
        }

        // 根据标记类型调整大小
        double sizeMultiplier = actualType.getDefaultSizeMultiplier();
        float adjustedSize = (float) (size * sizeMultiplier);

        // 对于线条类标记（X、PLUS、DASH），需要调整线条粗细
        if (actualType == ChartMarkerType.X || actualType == ChartMarkerType.PLUS || 
            actualType == ChartMarkerType.DASH) {
            return shape; // 线条类标记保持原样，由Stroke控制粗细
        }

        // 对于填充类标记，调整大小
        if (shape instanceof Ellipse2D.Double) {
            return new Ellipse2D.Double(-adjustedSize / 2, -adjustedSize / 2, adjustedSize, adjustedSize);
        } else if (shape instanceof Rectangle2D.Double) {
            return new Rectangle2D.Double(-adjustedSize / 2, -adjustedSize / 2, adjustedSize, adjustedSize);
        }

        return shape;
    }

    /**
     * 根据ChartMarkerType创建适合的Stroke
     *
     * @param markerType 标记类型
     * @param lineWidth  线条宽度
     * @return Stroke对象
     */
    public static Stroke createMarkerStroke(ChartMarkerType markerType, float lineWidth) {
        if (markerType == null) {
            return new BasicStroke(lineWidth);
        }

        // 处理AUTO类型
        ChartMarkerType actualType = markerType;
        if (markerType == ChartMarkerType.AUTO) {
            actualType = ChartMarkerType.CIRCLE; // AUTO默认使用圆形
        }

        // 对于线条类标记，使用稍粗的线条
        float strokeWidth = lineWidth;
        if (actualType == ChartMarkerType.X || actualType == ChartMarkerType.PLUS || 
            actualType == ChartMarkerType.DASH) {
            strokeWidth = Math.max(lineWidth, 1.5f); // 确保线条可见
        }

        return new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}