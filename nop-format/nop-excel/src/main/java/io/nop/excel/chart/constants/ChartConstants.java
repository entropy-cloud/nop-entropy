package io.nop.excel.chart.constants;

/**
 * Chart configuration constants
 * Generated from chart-types.xdef constant definitions
 */
public class ChartConstants {
    
    // Default chart dimensions
    public static final double DEFAULT_WIDTH = 800.0;
    public static final double DEFAULT_HEIGHT = 600.0;
    public static final double MIN_WIDTH = 100.0;
    public static final double MIN_HEIGHT = 100.0;
    public static final double MAX_WIDTH = 10000.0;
    public static final double MAX_HEIGHT = 10000.0;
    
    // Default animation settings
    public static final long DEFAULT_ANIMATION_DURATION = 1000L;
    public static final long DEFAULT_ANIMATION_DELAY = 0L;
    
    // Default styling values
    public static final double DEFAULT_LINE_WIDTH = 2.0;
    public static final double DEFAULT_MARKER_SIZE = 4.0;
    public static final double DEFAULT_OPACITY = 1.0;
    public static final double DEFAULT_FILL_OPACITY = 0.7;
    
    // Default font sizes
    public static final double DEFAULT_FONT_SIZE = 12.0;
    public static final double TITLE_FONT_SIZE = 16.0;
    public static final double AXIS_FONT_SIZE = 11.0;
    public static final double LEGEND_FONT_SIZE = 11.0;
    public static final double LABEL_FONT_SIZE = 10.0;
    
    // Default colors
    public static final String DEFAULT_BACKGROUND_COLOR = "#ffffff";
    public static final String DEFAULT_TEXT_COLOR = "#333333";
    public static final String DEFAULT_BORDER_COLOR = "#cccccc";
    public static final String DEFAULT_GRID_COLOR = "#e0e0e0";
    
    // Chart type compatibility
    public static final String[] AXIS_COMPATIBLE_TYPES = {"line", "bar", "column", "scatter", "bubble", "area", "combo"};
    public static final String[] SINGLE_SERIES_TYPES = {"pie", "doughnut"};
    public static final String[] MULTI_SERIES_TYPES = {"line", "bar", "column", "scatter", "bubble", "area", "radar", "heatmap", "combo"};
    
    // Export format settings
    public static final double DEFAULT_PDF_RESOLUTION = 300.0;
    public static final boolean DEFAULT_EXCEL_PRESERVE_ASPECT_RATIO = true;
    public static final boolean DEFAULT_ECHARTS_RESPONSIVE = true;
    
    private ChartConstants() {
        // Utility class - prevent instantiation
    }
}