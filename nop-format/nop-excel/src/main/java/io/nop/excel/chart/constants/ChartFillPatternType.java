package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图案填充类型
 * 对应 OOXML 的 ST_PresetPatternVal 和 POI 的 FillPatternType
 */
public enum ChartFillPatternType {
    /**
     * 无填充
     * OOXML: none, POI: NO_FILL
     */
    NONE("none"),

    /**
     * 纯色填充（特殊：只有前景色，无图案）
     * OOXML: solid, POI: SOLID_FOREGROUND
     */
    SOLID("solid"),

    /**
     * 5% 点状
     * OOXML: pct5, POI: FINE_DOTS
     */
    PERCENT_5("pct5"),

    /**
     * 10% 点状
     * OOXML: pct10, POI: SPARSE_DOTS
     */
    PERCENT_10("pct10"),

    /**
     * 20% 点状
     * OOXML: pct20
     */
    PERCENT_20("pct20"),

    /**
     * 25% 点状
     * OOXML: pct25
     */
    PERCENT_25("pct25"),

    /**
     * 30% 点状
     * OOXML: pct30
     */
    PERCENT_30("pct30"),

    /**
     * 40% 点状
     * OOXML: pct40
     */
    PERCENT_40("pct40"),

    /**
     * 50% 点状
     * OOXML: pct50
     */
    PERCENT_50("pct50"),

    /**
     * 60% 点状
     * OOXML: pct60
     */
    PERCENT_60("pct60"),

    /**
     * 70% 点状
     * OOXML: pct70
     */
    PERCENT_70("pct70"),

    /**
     * 75% 点状
     * OOXML: pct75
     */
    PERCENT_75("pct75"),

    /**
     * 80% 点状
     * OOXML: pct80
     */
    PERCENT_80("pct80"),

    /**
     * 90% 点状
     * OOXML: pct90
     */
    PERCENT_90("pct90"),

    /**
     * 横条纹
     * OOXML: horz, POI: THIN_HORZ_BANDS
     */
    HORIZONTAL_STRIPE("horz"),

    /**
     * 竖条纹
     * OOXML: vert, POI: THIN_VERT_BANDS
     */
    VERTICAL_STRIPE("vert"),

    /**
     * 反斜线条纹
     * OOXML: bDiag, POI: THIN_BACKWARD_DIAG
     */
    BACKWARD_DIAGONAL("bDiag"),

    /**
     * 正斜线条纹
     * OOXML: fDiag, POI: THIN_FORWARD_DIAG
     */
    FORWARD_DIAGONAL("fDiag"),

    /**
     * 十字交叉线
     * OOXML: cross
     */
    CROSS("cross"),

    /**
     * 斜交叉线
     * OOXML: diagCross
     */
    DIAGONAL_CROSS("diagCross"),

    /**
     * 深色横条纹
     * OOXML: darkHorz
     */
    DARK_HORIZONTAL("darkHorz"),

    /**
     * 深色竖条纹
     * OOXML: darkVert
     */
    DARK_VERTICAL("darkVert"),

    /**
     * 深色反斜线
     * OOXML: darkBDiag
     */
    DARK_BACKWARD_DIAGONAL("darkBDiag"),

    /**
     * 深色正斜线
     * OOXML: darkFDiag
     */
    DARK_FORWARD_DIAGONAL("darkFDiag"),

    /**
     * 深色十字交叉
     * OOXML: darkCross
     */
    DARK_CROSS("darkCross"),

    /**
     * 深色斜交叉
     * OOXML: darkDiagCross
     */
    DARK_DIAGONAL_CROSS("darkDiagCross"),

    /**
     * 大点
     * OOXML: lgSpot
     */
    LARGE_SPOT("lgSpot"),

    /**
     * 方格
     * OOXML: openDtl
     */
    CHECKER_BOARD("openDtl");

    private final String value;

    ChartFillPatternType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    @StaticFactoryMethod
    public static ChartFillPatternType fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        for (ChartFillPatternType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * 是否是百分比点状图案
     */
    public boolean isPercentPattern() {
        return name().startsWith("PERCENT_");
    }
}