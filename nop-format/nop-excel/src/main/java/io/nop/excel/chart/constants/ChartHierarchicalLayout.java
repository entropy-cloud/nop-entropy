package io.nop.excel.chart.constants;

/**
 * Hierarchical chart layout algorithm enumeration
 * Based on OOXML standard for hierarchical chart layouts
 */
public enum ChartHierarchicalLayout {
    /**
     * Squarified treemap algorithm
     */
    SQUARIFIED("squarified"),
    
    /**
     * Slice-and-dice algorithm
     */
    SLICE("slice"),
    
    /**
     * Strip layout algorithm
     */
    STRIP("strip"),
    
    /**
     * Binary tree layout algorithm
     */
    BINARY_TREE("binaryTree"),
    
    /**
     * Sunburst layout algorithm
     */
    SUNBURST("sunburst"),
    
    /**
     * Treemap layout algorithm
     */
    TREEMAP("treemap");

    private final String value;

    ChartHierarchicalLayout(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ChartHierarchicalLayout fromValue(String value) {
        for (ChartHierarchicalLayout layout : values()) {
            if (layout.value.equals(value)) {
                return layout;
            }
        }
        throw new IllegalArgumentException("Unknown hierarchical layout: " + value);
    }
}