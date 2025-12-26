package io.nop.excel.chart.constants;

/**
 * 文本方向枚举
 * 对应 OOXML 中的 a:bodyPr/@vert 属性
 * ST_TextVerticalType
 */
public enum ChartTextDirection {
    /**
     * 水平方向（默认）
     * OOXML: horz
     */
    HORIZONTAL("horz"),

    /**
     * 垂直方向 - 从顶部到底部，每个字符旋转90度
     * OOXML: vert
     */
    VERTICAL("vert"),

    /**
     * 垂直方向 - 从底部到顶部，每个字符旋转270度
     * OOXML: vert270
     */
    VERTICAL_270("vert270"),

    /**
     * 垂直方向 - 蒙古文样式（从上到下，从左到右）
     * OOXML: mongolianVert
     */
    MONGOLIAN_VERTICAL("mongolianVert"),

    /**
     * 文字方向从右到左
     * OOXML: rtl
     */
    RIGHT_TO_LEFT("rtl"),

    /**
     * 文字方向从左到右
     * OOXML: ltr
     */
    LEFT_TO_RIGHT("ltr"),

    /**
     * 文字方向根据内容自动判断
     * OOXML: auto
     */
    AUTO("auto");

    private final String value;

    ChartTextDirection(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    public static ChartTextDirection fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ChartTextDirection dir : values()) {
            if (dir.value.equals(value)) {
                return dir;
            }
        }
        return HORIZONTAL;
    }
}