package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 线条样式枚举
 * 对应 OOXML <a:prstDash val="…"/> 和 Excel 线型
 */
public enum ChartLineStyle {
    /**
     * 实线
     */
    SOLID("solid"),
    
    /**
     * 短划线
     */
    DASH("dash"),
    
    /**
     * 圆点
     */
    DOT("dot"),
    
    /**
     * 短划线加点
     */
    DASH_DOT("dashDot"),
    
    /**
     * 长划线加点
     */
    LONG_DASH("lgDash"),
    
    /**
     * 长划线加两点
     */
    LONG_DASH_DOT("lgDashDot"),
    
    /**
     * 长划线加两点（另一种）
     */
    LONG_DASH_DOT_DOT("lgDashDotDot"),
    
    /**
     * 系统默认虚线
     */
    SYS_DASH("sysDash"),
    
    /**
     * 系统默认点线
     */
    SYS_DOT("sysDot"),
    
    /**
     * 系统默认点划线
     */
    SYS_DASH_DOT("sysDashDot");

    private final String value;

    ChartLineStyle(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString(){
        return value;
    }

    /**
     * XML 值 → 枚举
     */
    @StaticFactoryMethod
    public static ChartLineStyle fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartLineStyle style : values()) {
            if (style.value.equals(v)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Illegal line style value: " + v);
    }
}