package io.nop.chart.export.echarts;

import io.nop.excel.chart.constants.ChartDataLabelPosition;
import io.nop.excel.chart.constants.ChartGradientDirection;
import io.nop.excel.chart.constants.ChartLineStyle;
import io.nop.excel.chart.constants.ChartMarkerType;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.excel.chart.model.ChartLineStyleModel;
import io.nop.excel.chart.model.ChartMarkersModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps ChartModel style structures to ECharts option fragments.
 *
 * <p>Style mapping is best-effort visual approximation ("尽量贴近" per roadmap);
 * unsupported decorations (pattern/texture fills) fall back to their foreground
 * color, which is a documented approximation rather than a semantic change.</p>
 */
public final class EchartsStyleMapper {

    private EchartsStyleMapper() {
    }

    public static Map<String, Object> newMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Normalize Excel color strings ("4472C4", "FF4472C4" ARGB, "#4472C4") to ECharts
     * color strings ("#4472C4" / "rgba(...)").
     */
    public static String normalizeColor(String color) {
        if (color == null || color.isEmpty())
            return null;
        String hex = color.startsWith("#") ? color.substring(1) : color;
        if (hex.length() == 6)
            return "#" + hex.toLowerCase();
        if (hex.length() == 8) {
            int argb = (int) Long.parseLong(hex, 16);
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            return "rgba(" + r + "," + g + "," + b + "," + fmt(a / 255.0) + ")";
        }
        return color;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /**
     * Map a solid fill color or gradient to an ECharts itemStyle color value.
     * Returns null when the fill produces no paint (fill type NONE / empty).
     */
    public static Object fillColor(ChartFillModel fill) {
        if (fill == null)
            return null;
        if (fill.getGradient() != null && Boolean.TRUE.equals(fill.getGradient().getEnabled())) {
            Map<String, Object> grad = gradient(fill.getGradient());
            if (grad != null)
                return grad;
        }
        String color = fill.getForegroundColor() != null ? fill.getForegroundColor() : fill.getBackgroundColor();
        return normalizeColor(color);
    }

    /**
     * Build an ECharts linear/radial gradient object from a ChartGradientModel.
     */
    public static Map<String, Object> gradient(ChartGradientModel grad) {
        String start = normalizeColor(grad.getStartColor());
        String end = normalizeColor(grad.getEndColor());
        if (start == null && end == null)
            return null;
        if (start == null)
            start = end;
        if (end == null)
            end = start;

        Map<String, Object> map = newMap();
        ChartGradientDirection dir = grad.getDirection() == null ? ChartGradientDirection.VERTICAL : grad.getDirection();
        if (dir == ChartGradientDirection.FROM_CENTER || dir == ChartGradientDirection.FROM_CORNER) {
            map.put("type", "radial");
            map.put("x", 0.5);
            map.put("y", 0.5);
            map.put("r", 0.8);
        } else {
            map.put("type", "linear");
            double[] v = directionVector(dir, grad.getAngle());
            map.put("x", v[0]);
            map.put("y", v[1]);
            map.put("x2", v[2]);
            map.put("y2", v[3]);
        }
        map.put("colorStops", java.util.Arrays.asList(stop(0, start), stop(1, end)));
        return map;
    }

    private static Map<String, Object> stop(double offset, String color) {
        Map<String, Object> map = newMap();
        map.put("offset", offset);
        map.put("color", color);
        return map;
    }

    private static double[] directionVector(ChartGradientDirection dir, Double angle) {
        switch (dir) {
            case HORIZONTAL:
                return new double[]{0, 0, 1, 0};
            case VERTICAL:
                return new double[]{0, 0, 0, 1};
            case DIAGONAL_UP:
                return new double[]{0, 1, 1, 0};
            case DIAGONAL_DOWN:
                return new double[]{0, 0, 1, 1};
            default: // CUSTOM or unhandled: use angle in degrees, 0 = left-to-right
                double rad = Math.toRadians(angle == null ? 0 : angle);
                double dx = Math.cos(rad);
                double dy = Math.sin(rad);
                return new double[]{0.5 - dx / 2, 0.5 - dy / 2, 0.5 + dx / 2, 0.5 + dy / 2};
        }
    }

    /**
     * Map a series/axis line style to an ECharts lineStyle object.
     */
    public static Map<String, Object> lineStyle(ChartLineStyleModel line) {
        if (line == null)
            return null;
        Map<String, Object> map = newMap();
        String color = normalizeColor(line.getColor());
        if (color != null)
            map.put("color", color);
        if (line.getWidth() != null)
            map.put("width", line.getWidth());
        if (line.getOpacity() != null)
            map.put("opacity", line.getOpacity());
        String type = lineType(line.getStyle());
        if (type != null)
            map.put("type", type);
        return map;
    }

    public static String lineType(ChartLineStyle style) {
        if (style == null)
            return null;
        switch (style) {
            case SOLID:
                return "solid";
            case DOT:
            case SYS_DOT:
                return "dotted";
            case DASH:
            case SYS_DASH:
            case LONG_DASH:
            case DASH_DOT:
            case LONG_DASH_DOT:
            case DASH_DOT_DOT:
            case LONG_DASH_DOT_DOT:
                return "dashed";
            default:
                return "solid";
        }
    }

    /**
     * Map marker config to ECharts symbol/symbolSize fields on a series.
     */
    public static void applyMarkers(Map<String, Object> series, ChartMarkersModel markers) {
        if (markers == null)
            return;
        if (Boolean.FALSE.equals(markers.getEnabled())) {
            series.put("symbol", "none");
            return;
        }
        String symbol = symbol(markers.getType());
        if (symbol != null)
            series.put("symbol", symbol);
        if (markers.getSize() != null)
            series.put("symbolSize", markers.getSize());
    }

    public static String symbol(ChartMarkerType type) {
        if (type == null)
            return null;
        switch (type) {
            case CIRCLE:
            case DOT:
                return "circle";
            case SQUARE:
                return "rect";
            case DIAMOND:
                return "diamond";
            case TRIANGLE:
                return "triangle";
            case AUTO:
                return null; // ECharts default (circle)
            case NONE:
                return "none";
            // X/STAR/PLUS/DASH have no native ECharts symbol; nearest-shape approximation.
            case X:
                return "diamond";
            case STAR:
                return "pin";
            case PLUS:
            case DASH:
                return "rect";
            default:
                return null;
        }
    }

    public static Map<String, Object> splitLine(ChartGridModel grid) {
        Map<String, Object> split = newMap();
        if (grid == null || !grid.isVisible()) {
            split.put("show", false);
            return split;
        }
        split.put("show", true);
        Map<String, Object> style = newMap();
        String color = normalizeColor(grid.getColor());
        if (color != null)
            style.put("color", color);
        if (grid.getWidth() != null)
            style.put("width", grid.getWidth());
        if (grid.getOpacity() != null)
            style.put("opacity", grid.getOpacity());
        String type = lineType(grid.getStyle());
        if (type != null)
            style.put("type", type);
        split.put("lineStyle", style);
        return split;
    }

    /**
     * Build the ECharts label object for chart data labels. The formatter string uses
     * ECharts template placeholders; {@code pieFamily} enables the percent placeholder.
     */
    public static Map<String, Object> label(io.nop.excel.chart.model.ChartDataLabelsModel labels, boolean pieFamily) {
        Map<String, Object> label = newMap();
        if (labels == null)
            return label;

        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(labels.getShowSerName()))
            sb.append("{a}");
        if (Boolean.TRUE.equals(labels.getShowCatName())) {
            if (sb.length() > 0)
                sb.append(separator(labels));
            sb.append("{b}");
        }
        if (Boolean.TRUE.equals(labels.getShowVal())) {
            if (sb.length() > 0)
                sb.append(separator(labels));
            sb.append("{c}");
        }
        if (pieFamily && Boolean.TRUE.equals(labels.getShowPercent())) {
            if (sb.length() > 0)
                sb.append(separator(labels));
            sb.append("{d}%");
        }
        label.put("show", true);
        label.put("formatter", sb.length() > 0 ? sb.toString() : "{c}");

        String pos = labelPosition(labels.getPosition(), pieFamily);
        if (pos != null)
            label.put("position", pos);
        return label;
    }

    private static String separator(io.nop.excel.chart.model.ChartDataLabelsModel labels) {
        String sep = labels.getSeparator();
        return sep == null ? "\n" : sep;
    }

    public static String labelPosition(ChartDataLabelPosition position, boolean pieFamily) {
        if (position == null)
            return null;
        switch (position) {
            case CENTER:
                return "inside";
            case INSIDE_BASE:
                return "insideBottom";
            case INSIDE_END:
                return "insideTop";
            case OUTSIDE_END:
                return pieFamily ? "outside" : "top";
            case BEST_FIT:
                return pieFamily ? "outside" : "top";
            case LEFT:
                return "left";
            case RIGHT:
                return "right";
            case TOP:
                return "top";
            case BOTTOM:
                return "bottom";
            default:
                return null;
        }
    }

    /**
     * Item style (color / gradient / border / opacity) for a series or data point.
     */
    public static Map<String, Object> itemStyle(ChartShapeStyleModel shapeStyle) {
        Map<String, Object> item = newMap();
        if (shapeStyle == null)
            return item;
        if (shapeStyle.getFill() != null) {
            Object color = fillColor(shapeStyle.getFill());
            if (color != null)
                item.put("color", color);
        }
        if (shapeStyle.getBorder() != null) {
            Map<String, Object> border = newMap();
            String color = normalizeColor(shapeStyle.getBorder().getColor());
            if (color != null)
                border.put("color", color);
            if (shapeStyle.getBorder().getWidth() != null)
                border.put("width", shapeStyle.getBorder().getWidth());
            String type = lineType(shapeStyle.getBorder().getStyle());
            if (type != null)
                border.put("type", type);
            if (!border.isEmpty())
                item.put("borderColor", border.get("color"));
        }
        if (shapeStyle.getOpacity() != null)
            item.put("opacity", shapeStyle.getOpacity());
        return item;
    }
}
