package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 坐标轴刻度标签位置枚举
 * 对应 OOXML <c:tickLblPos val="…"/>
 */
public enum ChartAxisTickLabelPosition {

    /**
     * 标签显示在轴旁（默认）
     */
    NEXT_TO("nextTo"),

    /**
     * 标签显示在绘图区“低”侧（如底部轴的下方）
     */
    LOW("low"),

    /**
     * 标签显示在绘图区“高”侧（如底部轴的上方）
     */
    HIGH("high"),

    /**
     * 不显示刻度标签
     */
    NONE("none");

    private final String value;

    ChartAxisTickLabelPosition(String value) {
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
    public static ChartAxisTickLabelPosition fromValue(String v) {
        if (StringHelper.isEmpty(v))
            return null;

        for (ChartAxisTickLabelPosition p : values()) {
            if (p.value.equals(v)) {
                return p;
            }
        }
        throw new IllegalArgumentException("illegal tickLblPos value: " + v);
    }
}