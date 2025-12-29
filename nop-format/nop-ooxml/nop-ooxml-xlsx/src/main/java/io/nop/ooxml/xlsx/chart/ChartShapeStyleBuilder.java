package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartFillPatternType;
import io.nop.excel.chart.constants.ChartGradientDirection;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartFillPictureModel;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.excel.chart.model.ChartShadowModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.util.UnitsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChartShapeStyleBuilder - 形状样式构建器
 * 负责将ChartShapeStyleModel转换为OOXML a:spPr元素
 * 支持填充、边框、阴影等样式属性的完整生成
 */
public class ChartShapeStyleBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ChartShapeStyleBuilder.class);
    public static final ChartShapeStyleBuilder INSTANCE = new ChartShapeStyleBuilder();

    /**
     * 构建形状样式XML
     *
     * @param shapeStyle 形状样式模型
     * @return a:spPr XML节点，如果样式为空则返回null
     */
    public XNode buildShapeStyle(ChartShapeStyleModel shapeStyle) {
        if (shapeStyle == null) {
            LOG.debug("Shape style model is null, skipping style generation");
            return null;
        }

        XNode spPrNode = XNode.make("a:spPr");
        boolean hasContent = false;

        // 构建填充
        if (buildFill(spPrNode, shapeStyle.getFill())) {
            hasContent = true;
        }

        // 构建边框
        if (buildBorder(spPrNode, shapeStyle.getBorder())) {
            hasContent = true;
        }

        // 构建阴影效果
        if (buildShadow(spPrNode, shapeStyle.getShadow())) {
            hasContent = true;
        }

        // 如果没有任何内容，返回null
        if (!hasContent) {
            LOG.debug("No style content generated, returning null");
            return null;
        }

        LOG.debug("Successfully built shape style XML");

        return spPrNode;

    }

    /**
     * 构建填充
     *
     * @param spPrNode 形状属性节点
     * @param fill     填充模型
     * @return 是否添加了填充内容
     */
    private boolean buildFill(XNode spPrNode, ChartFillModel fill) {
        if (fill == null || fill.getType() == null) {
            LOG.debug("Fill model is null or has no type, skipping fill generation");
            return false;
        }


        switch (fill.getType()) {
            case NONE:
                return buildNoFill(spPrNode);
            case SOLID:
                return buildSolidFill(spPrNode, fill);
            case GRADIENT:
                return buildGradientFill(spPrNode, fill);
            case PATTERN:
                return buildPatternFill(spPrNode, fill);
            case PICTURE:
                return buildPictureFill(spPrNode, fill);
            default:
                LOG.warn("Unknown fill type: {}, skipping fill generation", fill.getType());
                return false;
        }

    }

    /**
     * 构建无填充
     */
    private boolean buildNoFill(XNode spPrNode) {
        spPrNode.addChild("a:noFill");
        LOG.debug("Built no fill");
        return true;
    }

    /**
     * 构建纯色填充
     */
    private boolean buildSolidFill(XNode spPrNode, ChartFillModel fill) {
        String color = fill.getForegroundColor();
        if (StringHelper.isEmpty(color)) {
            LOG.debug("Solid fill has no color, skipping");
            return false;
        }

        XNode solidFillNode = spPrNode.addChild("a:solidFill");

        // 构建颜色
        buildColor(solidFillNode, color);

        // 构建透明度
        buildOpacity(solidFillNode, fill.getOpacity());

        LOG.debug("Built solid fill with color: {}", color);
        return true;
    }

    /**
     * 构建渐变填充
     */
    private boolean buildGradientFill(XNode spPrNode, ChartFillModel fill) {
        ChartGradientModel gradient = fill.getGradient();
        if (gradient == null || !gradient.getEnabled()) {
            LOG.debug("Gradient is null or disabled, skipping gradient fill");
            return false;
        }

        XNode gradFillNode = spPrNode.addChild("a:gradFill");

        // 构建渐变停止点
        buildGradientStops(gradFillNode, gradient, fill.getOpacity());

        // 构建渐变方向 - 使用新的枚举
        buildGradientDirection(gradFillNode, gradient);

        // 添加平铺矩形
        gradFillNode.addChild("a:tileRect");

        LOG.debug("Built gradient fill with direction: {}", gradient.getDirection());
        return true;
    }

    /**
     * 构建渐变停止点
     */
    private void buildGradientStops(XNode gradFillNode, ChartGradientModel gradient, Double opacity) {
        XNode gsLstNode = gradFillNode.addChild("a:gsLst");

        // 构建开始颜色停止点
        String startColor = gradient.getStartColor();
        if (!StringHelper.isEmpty(startColor)) {
            XNode startGsNode = gsLstNode.addChild("a:gs");
            startGsNode.setAttr("pos", "0");
            buildColor(startGsNode, startColor);

            // 为开始颜色添加透明度
            buildOpacity(startGsNode, opacity);
        }

        // 构建结束颜色停止点
        String endColor = gradient.getEndColor();
        if (!StringHelper.isEmpty(endColor)) {
            XNode endGsNode = gsLstNode.addChild("a:gs");
            endGsNode.setAttr("pos", "100000");
            buildColor(endGsNode, endColor);

            // 为结束颜色添加透明度
            buildOpacity(endGsNode, opacity);
        }
    }

    /**
     * 构建渐变方向 - 重构版本
     */
    private void buildGradientDirection(XNode gradFillNode, ChartGradientModel gradient) {
        ChartGradientDirection direction = gradient.getDirection();
        if (direction == null) {
            // 默认使用水平渐变
            direction = ChartGradientDirection.HORIZONTAL;
        }

        if (direction.isLinear()) {
            // 线性渐变
            XNode linNode = gradFillNode.addChild("a:lin");

            // 计算实际使用的OOXML角度
            Double userAngle = gradient.getAngle();
            int ooxmlAngle = direction.calculateOoxmlAngle(userAngle);

            if (ooxmlAngle >= 0) { // 有效角度
                linNode.setAttr("ang", String.valueOf(ooxmlAngle));
            }

            // 设置缩放标志
            if (userAngle != null && direction == ChartGradientDirection.CUSTOM) {
                linNode.setAttr("scaled", "0");
            }
        } else if (direction.isPath()) {
            // 路径渐变
            XNode pathNode = gradFillNode.addChild("a:path");
            pathNode.setAttr("path", direction.getPathType());

            // 为路径渐变添加 fillToRect
            buildPathFillToRect(pathNode, direction);
        } else {
            LOG.warn("Unknown gradient direction: {}, defaulting to horizontal", direction);
            // 默认线性渐变
            XNode linNode = gradFillNode.addChild("a:lin");
            linNode.setAttr("ang", "0");
        }
    }

    /**
     * 构建路径渐变的填充矩形
     */
    private void buildPathFillToRect(XNode pathNode, ChartGradientDirection direction) {
        XNode fillToRectNode = pathNode.addChild("a:fillToRect");

        switch (direction) {
            case FROM_CENTER:
                // 圆形渐变，从中心向外
                fillToRectNode.setAttr("l", "50000");
                fillToRectNode.setAttr("t", "50000");
                fillToRectNode.setAttr("r", "50000");
                fillToRectNode.setAttr("b", "50000");
                break;
            case FROM_CORNER:
                // 矩形渐变，从角向外
                fillToRectNode.setAttr("l", "100000");
                fillToRectNode.setAttr("t", "100000");
                fillToRectNode.setAttr("r", "0");
                fillToRectNode.setAttr("b", "0");
                break;
            default:
                // 默认填充整个区域
                fillToRectNode.setAttr("l", "0");
                fillToRectNode.setAttr("t", "0");
                fillToRectNode.setAttr("r", "100000");
                fillToRectNode.setAttr("b", "100000");
        }
    }

    /**
     * 构建图案填充
     */
    private boolean buildPatternFill(XNode spPrNode, ChartFillModel fill) {
        ChartFillPatternType pattern = fill.getPattern();
        if (pattern == null) {
            LOG.debug("Pattern type is null, skipping pattern fill");
            return false;
        }

        XNode pattFillNode = spPrNode.addChild("a:pattFill");

        // 设置图案类型
        String ooxmlPattern = mapPatternTypeToOoxml(pattern);
        pattFillNode.setAttr("prst", ooxmlPattern);

        // 构建前景色
        String foregroundColor = fill.getForegroundColor();
        if (!StringHelper.isEmpty(foregroundColor)) {
            XNode fgClrNode = pattFillNode.addChild("a:fgClr");
            buildColor(fgClrNode, foregroundColor);
        }

        // 构建背景色
        String backgroundColor = fill.getBackgroundColor();
        if (!StringHelper.isEmpty(backgroundColor)) {
            XNode bgClrNode = pattFillNode.addChild("a:bgClr");
            buildColor(bgClrNode, backgroundColor);
        }

        // 构建透明度
        buildOpacity(pattFillNode, fill.getOpacity());

        LOG.debug("Built pattern fill with pattern: {}", pattern);
        return true;
    }

    /**
     * 映射图案类型到OOXML
     */
    private String mapPatternTypeToOoxml(ChartFillPatternType pattern) {
        switch (pattern) {
            case SOLID:
                return "solid";
            case PERCENT_5:
                return "pct5";
            case PERCENT_10:
                return "pct10";
            case PERCENT_20:
                return "pct20";
            case PERCENT_25:
                return "pct25";
            case PERCENT_30:
                return "pct30";
            case PERCENT_40:
                return "pct40";
            case PERCENT_50:
                return "pct50";
            case PERCENT_60:
                return "pct60";
            case PERCENT_70:
                return "pct70";
            case PERCENT_75:
                return "pct75";
            case PERCENT_80:
                return "pct80";
            case PERCENT_90:
                return "pct90";
            case HORIZONTAL_STRIPE:
                return "horz";
            case VERTICAL_STRIPE:
                return "vert";
            case BACKWARD_DIAGONAL:
                return "bdiag";
            case FORWARD_DIAGONAL:
                return "fdiag";
            case CROSS:
                return "cross";
            case DIAGONAL_CROSS:
                return "diagcross";
            case DARK_HORIZONTAL:
                return "darkhorz";
            case DARK_VERTICAL:
                return "darkvert";
            case DARK_BACKWARD_DIAGONAL:
                return "darkbdiag";
            case DARK_FORWARD_DIAGONAL:
                return "darkfdiag";
            case DARK_CROSS:
                return "darkcross";
            case DARK_DIAGONAL_CROSS:
                return "darkdiagcross";
            case LARGE_SPOT:
                return "lgspot";
            case CHECKER_BOARD:
                return "opendtl";
            case NONE:
                return "none";
            default:
                LOG.warn("Unknown pattern type: {}, using solid", pattern);
                return "solid";
        }
    }

    /**
     * 构建图片填充
     */
    private boolean buildPictureFill(XNode spPrNode, ChartFillModel fill) {
        ChartFillPictureModel picture = fill.getPicture();
        if (picture == null) {
            LOG.debug("Picture model is null, skipping picture fill");
            return false;
        }

        XNode blipFillNode = spPrNode.addChild("a:blipFill");

        // 构建图片引用
        XNode blipNode = blipFillNode.addChild("a:blip");

        String embedId = picture.getEmbedId();
        if (!StringHelper.isEmpty(embedId)) {
            blipNode.setAttr("r:embed", embedId);
        }

        String imageUrl = picture.getImageUrl();
        if (!StringHelper.isEmpty(imageUrl)) {
            blipNode.setAttr("r:link", imageUrl);
        }

        // 构建拉伸或平铺模式
        if (picture.getStretch()) {
            XNode stretchNode = blipFillNode.addChild("a:stretch");
            stretchNode.addChild("a:fillRect");
        } else if (picture.getTitle()) {
            blipFillNode.addChild("a:tile");
        }

        // 构建透明度
        buildOpacity(blipFillNode, fill.getOpacity());

        LOG.debug("Built picture fill");
        return true;
    }

    /**
     * 构建颜色
     */
    private void buildColor(XNode parentNode, String color) {
        if (StringHelper.isEmpty(color)) {
            return;
        }

        // 检查是否为RGB颜色（以#开头）
        if (color.startsWith("#")) {
            color = color.substring(1).toUpperCase();
        }
        XNode srgbClrNode = parentNode.addChild("a:srgbClr");
        srgbClrNode.setAttr("val", color);
    }

    /**
     * 构建透明度
     */
    private void buildOpacity(XNode parentNode, Double opacity) {
        if (opacity == null || opacity >= 1.0) {
            return; // 完全不透明，不需要添加透明度元素
        }

        // 转换为OOXML透明度值（0-100000）
        long ooxmlOpacity = Math.round((1.0 - opacity) * 100000);
        if (ooxmlOpacity > 0) {
            XNode alphaNode = parentNode.addChild("a:alpha");
            alphaNode.setAttr("val", String.valueOf(ooxmlOpacity));
        }

    }

    /**
     * 构建边框
     */
    private boolean buildBorder(XNode spPrNode, ChartBorderModel border) {
        if (border == null) {
            LOG.debug("Border model is null, skipping border generation");
            return false;
        }


        XNode lnNode = spPrNode.addChild("a:ln");

        // 设置边框宽度
        Double width = border.getWidth();
        if (width != null && width > 0) {
            long emuWidth = UnitsHelper.pointsToEMU(width);
            lnNode.setAttr("w", String.valueOf(emuWidth));
        }

        // 构建边框颜色
        String color = border.getColor();
        if (!StringHelper.isEmpty(color)) {
            XNode solidFillNode = lnNode.addChild("a:solidFill");
            buildColor(solidFillNode, color);

            // 构建边框透明度
            buildOpacity(solidFillNode, border.getOpacity());
        }

        // 构建线条样式
        ChartLineStyle style = border.getStyle();
        if (style != null) {
            if (style != ChartLineStyle.SOLID) {
                XNode prstDashNode = lnNode.addChild("a:prstDash");
                String ooxmlStyle = mapLineStyleToOoxml(style);
                prstDashNode.setAttr("val", ooxmlStyle);
            }
        }

        if (Boolean.TRUE.equals(border.getRound()))
            lnNode.addChild("a:round");

        LOG.debug("Built border with width: {}, color: {}, style: {}", width, color, style);
        return true;

    }

    /**
     * 映射线条样式到OOXML
     */
    private String mapLineStyleToOoxml(ChartLineStyle style) {
        switch (style) {
            case SOLID:
                return "solid";
            case DASH:
                return "dash";
            case DOT:
                return "dot";
            case DASH_DOT:
                return "dashdot";
            case DASH_DOT_DOT:
                return "dashdotdot";
            case LONG_DASH:
                return "lgdash";
            case LONG_DASH_DOT:
                return "lgdashdot";
            case LONG_DASH_DOT_DOT:
                return "lgdashdotdot";
            case SYS_DASH:
                return "sysdash";
            case SYS_DOT:
                return "sysdot";
            case SYS_DASH_DOT:
                return "sysdashdot";
            default:
                LOG.warn("Unknown line style: {}, using solid", style);
                return "solid";
        }
    }

    /**
     * 构建阴影效果
     */
    private boolean buildShadow(XNode spPrNode, ChartShadowModel shadow) {
        if (shadow == null || !shadow.getEnabled()) {
            LOG.debug("Shadow is null or disabled, skipping shadow generation");
            return false;
        }

        XNode effectLstNode = spPrNode.addChild("a:effectLst");
        XNode outerShdwNode = effectLstNode.addChild("a:outerShdw");

        // 设置阴影距离
        Double offsetX = shadow.getOffsetX();
        Double offsetY = shadow.getOffsetY();
        if (offsetX != null && offsetY != null) {
            // 计算距离（使用勾股定理）
            double distance = Math.sqrt(offsetX * offsetX + offsetY * offsetY);
            long emuDistance = UnitsHelper.pointsToEMU(distance);
            outerShdwNode.setAttr("dist", String.valueOf(emuDistance));

            // 计算角度
            double angle = Math.atan2(offsetY, offsetX);
            double degrees = angle * 180 / Math.PI;
            String ooxmlAngleStr = ChartPropertyHelper.degreesToOoxmlAngleString(degrees);
            outerShdwNode.setAttr("dir", ooxmlAngleStr);
        }

        // 设置模糊半径
        Double blur = shadow.getBlur();
        if (blur != null && blur > 0) {
            long emuBlur = UnitsHelper.pointsToEMU(blur);
            outerShdwNode.setAttr("blurRad", String.valueOf(emuBlur));
        }

        // 构建阴影颜色
        String color = shadow.getColor();
        if (!StringHelper.isEmpty(color)) {
            buildColor(outerShdwNode, color);
        }

        // 构建阴影透明度
        Double opacity = shadow.getOpacity();
        if (opacity != null && opacity < 1.0) {
            buildOpacity(outerShdwNode, opacity);
        }

        LOG.debug("Built shadow effect");
        return true;

    }
}