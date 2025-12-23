package io.nop.excel.chart.constants;

/**
 * Color palette constants for chart styling
 * Generated from chart-types.xdef constant definitions
 */
public class ChartColorPalettes {
    
    // Category color palettes
    public static final String[] CATEGORY10 = {
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", 
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    };
    
    public static final String[] CATEGORY20 = {
        "#1f77b4", "#aec7e8", "#ff7f0e", "#ffbb78", "#2ca02c", "#98df8a", 
        "#d62728", "#ff9896", "#9467bd", "#c5b0d5", "#8c564b", "#c49c94", 
        "#e377c2", "#f7b6d3", "#7f7f7f", "#c7c7c7", "#bcbd22", "#dbdb8d", 
        "#17becf", "#9edae5"
    };
    
    // Themed color palettes
    public static final String[] PASTEL = {
        "#fbb4ae", "#b3cde3", "#ccebc5", "#decbe4", "#fed9a6", 
        "#ffffcc", "#e5d8bd", "#fddaec", "#f2f2f2"
    };
    
    public static final String[] BRIGHT = {
        "#e41a1c", "#377eb8", "#4daf4a", "#984ea3", "#ff7f00", 
        "#ffff33", "#a65628", "#f781bf", "#999999"
    };
    
    public static final String[] DARK = {
        "#1b9e77", "#d95f02", "#7570b3", "#e7298a", "#66a61e", 
        "#e6ab02", "#a6761d", "#666666"
    };
    
    public static final String[] COOL = {
        "#8dd3c7", "#ffffb3", "#bebada", "#fb8072", "#80b1d3", "#fdb462", 
        "#b3de69", "#fccde5", "#d9d9d9", "#bc80bd", "#ccebc5", "#ffed6f"
    };
    
    public static final String[] WARM = {
        "#d73027", "#f46d43", "#fdae61", "#fee08b", "#ffffbf", 
        "#e6f598", "#abdda4", "#66c2a5", "#3288bd"
    };
    
    /**
     * Get color palette by name
     * @param paletteName the name of the palette
     * @return array of color strings, or CATEGORY10 as default
     */
    public static String[] getPalette(String paletteName) {
        if (paletteName == null) {
            return CATEGORY10;
        }
        
        switch (paletteName.toLowerCase()) {
            case "category10":
                return CATEGORY10;
            case "category20":
                return CATEGORY20;
            case "pastel":
                return PASTEL;
            case "bright":
                return BRIGHT;
            case "dark":
                return DARK;
            case "cool":
                return COOL;
            case "warm":
                return WARM;
            default:
                return CATEGORY10;
        }
    }
    
    /**
     * Get color from palette by index, cycling if necessary
     * @param palette the color palette
     * @param index the color index
     * @return color string
     */
    public static String getColor(String[] palette, int index) {
        if (palette == null || palette.length == 0) {
            return DEFAULT_COLOR;
        }
        return palette[index % palette.length];
    }
    
    private static final String DEFAULT_COLOR = "#1f77b4";
    
    private ChartColorPalettes() {
        // Utility class - prevent instantiation
    }
}