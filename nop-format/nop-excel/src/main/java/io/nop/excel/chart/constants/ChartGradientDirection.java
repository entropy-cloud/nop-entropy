package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 渐变方向枚举
 * 对应 OOXML 中的渐变填充方向设置
 */
public enum ChartGradientDirection {

    /**
     * 水平渐变（从左到右）
     * 对应 OOXML: &lt;a:lin ang="0"/&gt;
     */
    HORIZONTAL("horizontal", 0),

    /**
     * 垂直渐变（从上到下）
     * 对应 OOXML: &lt;a:lin ang="5400000"/&gt; (90°)
     */
    VERTICAL("vertical", 5400000),

    /**
     * 对角向上渐变（从左下到右上）
     * 对应 OOXML: &lt;a:lin ang="1350000"/&gt; (135°)
     */
    DIAGONAL_UP("diagonal_up", 1350000),

    /**
     * 对角向下渐变（从左上到右下）
     * 对应 OOXML: &lt;a:lin ang="3150000"/&gt; (315°)
     */
    DIAGONAL_DOWN("diagonal_down", 3150000),

    /**
     * 从中心向外渐变（径向）
     * 对应 OOXML: &lt;a:path path="circle"/&gt;
     */
    FROM_CENTER("from_center", -1),

    /**
     * 从角向外渐变
     * 对应 OOXML: &lt;a:path path="rect"/&gt;
     */
    FROM_CORNER("from_corner", -1),

    /**
     * 自定义角度渐变
     * 使用 angle 属性指定具体角度
     */
    CUSTOM("custom", -2);

    private final String value;
    private final int ooxmlAngle; // OOXML 角度单位（1/60000度），-1表示路径渐变，-2表示自定义

    ChartGradientDirection(String value, int ooxmlAngle) {
        this.value = value;
        this.ooxmlAngle = ooxmlAngle;
    }

    public String value() {
        return value;
    }

    public int getOoxmlAngle() {
        return ooxmlAngle;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 字符串值转换为枚举
     */
    @StaticFactoryMethod
    public static ChartGradientDirection fromValue(String value) {
        if (StringHelper.isEmpty(value)) {
            return null;
        }

        for (ChartGradientDirection direction : values()) {
            if (direction.value.equalsIgnoreCase(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown gradient direction: " + value);
    }

    /**
     * 是否为线性渐变
     * 线性渐变使用 &lt;a:lin&gt; 元素
     */
    public boolean isLinear() {
        switch (this) {
            case HORIZONTAL:
            case VERTICAL:
            case DIAGONAL_UP:
            case DIAGONAL_DOWN:
            case CUSTOM:
                return true;
            default:
                return false;
        }
    }

    /**
     * 是否为路径渐变
     * 路径渐变使用 &lt;a:path&gt; 元素
     */
    public boolean isPath() {
        switch (this) {
            case FROM_CENTER:
            case FROM_CORNER:
                return true;
            default:
                return false;
        }
    }

    /**
     * 获取对应的 OOXML 渐变类型
     */
    public String getOoxmlGradientType() {
        if (isLinear()) {
            return "lin";
        } else if (isPath()) {
            return "path";
        }
        return "lin"; // 默认
    }

    /**
     * 获取路径类型（如果是路径渐变）
     */
    public String getPathType() {
        switch (this) {
            case FROM_CENTER:
                return "circle";
            case FROM_CORNER:
                return "rect";
            default:
                return null;
        }
    }

    /**
     * 是否需要角度属性
     * 只有 CUSTOM 方向需要用户指定角度
     */
    public boolean requiresAngle() {
        return this == CUSTOM;
    }

    /**
     * 是否为对称渐变
     * 对称渐变有固定的角度值
     */
    public boolean hasFixedAngle() {
        switch (this) {
            case HORIZONTAL:    // 0°
            case VERTICAL:      // 90°
            case DIAGONAL_UP:   // 135°
            case DIAGONAL_DOWN: // 315°
                return true;
            default:
                return false;
        }
    }

    /**
     * 计算实际使用的 OOXML 角度
     *
     * @param userAngle 用户自定义的角度（度数），仅在 CUSTOM 时使用
     * @return OOXML 角度值（1/60000度）
     */
    public int calculateOoxmlAngle(Double userAngle) {
        if (this == CUSTOM && userAngle != null) {
            // 将用户角度（度）转换为 OOXML 单位（1/60000度）
            return (int) (userAngle * 60000);
        }
        return ooxmlAngle;
    }
}