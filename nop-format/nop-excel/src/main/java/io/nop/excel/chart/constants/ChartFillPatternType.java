package io.nop.excel.chart.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

/**
 * 图案填充类型
 * 对应 OOXML 的 ST_PresetPatternVal 和 POI 的 FillPatternType
 */
public enum ChartFillPatternType {
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
     * 细横条纹
     * OOXML: ltHorz
     */
    THIN_HORIZONTAL("ltHorz"),

    /**
     * 细竖条纹
     * OOXML: ltVert
     */
    THIN_VERTICAL("ltVert"),

    /**
     * 粗横条纹
     * OOXML: dkHorz
     */
    THICK_HORIZONTAL("dkHorz"),

    /**
     * 粗竖条纹
     * OOXML: dkVert
     */
    THICK_VERTICAL("dkVert"),

    /**
     * 窄横条纹
     * OOXML: narHorz
     */
    NARROW_HORIZONTAL("narHorz"),

    /**
     * 窄竖条纹
     * OOXML: narVert
     */
    NARROW_VERTICAL("narVert"),

    /**
     * 虚线横条纹
     * OOXML: dashHorz
     */
    DASHED_HORIZONTAL("dashHorz"),

    /**
     * 虚线竖条纹
     * OOXML: dashVert
     */
    DASHED_VERTICAL("dashVert"),

    /**
     * 下斜线
     * OOXML: dnDiag
     */
    DOWNWARD_DIAGONAL("dnDiag"),

    /**
     * 上斜线
     * OOXML: upDiag
     */
    UPWARD_DIAGONAL("upDiag"),

    /**
     * 细下斜线
     * OOXML: ltDnDiag
     */
    THIN_DOWNWARD_DIAGONAL("ltDnDiag"),

    /**
     * 细上斜线
     * OOXML: ltUpDiag
     */
    THIN_UPWARD_DIAGONAL("ltUpDiag"),

    /**
     * 粗下斜线
     * OOXML: dkDnDiag
     */
    THICK_DOWNWARD_DIAGONAL("dkDnDiag"),

    /**
     * 粗上斜线
     * OOXML: dkUpDiag
     */
    THICK_UPWARD_DIAGONAL("dkUpDiag"),

    /**
     * 宽下斜线
     * OOXML: wdDnDiag
     */
    WIDE_DOWNWARD_DIAGONAL("wdDnDiag"),

    /**
     * 宽上斜线
     * OOXML: wdUpDiag
     */
    WIDE_UPWARD_DIAGONAL("wdUpDiag"),

    /**
     * 虚线下斜线
     * OOXML: dashDnDiag
     */
    DASHED_DOWNWARD_DIAGONAL("dashDnDiag"),

    /**
     * 虚线上斜线
     * OOXML: dashUpDiag
     */
    DASHED_UPWARD_DIAGONAL("dashUpDiag"),

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
     * 小方格
     * OOXML: smCheck
     */
    SMALL_CHECKER("smCheck"),

    /**
     * 大方格
     * OOXML: lgCheck
     */
    LARGE_CHECKER("lgCheck"),

    /**
     * 小网格
     * OOXML: smGrid
     */
    SMALL_GRID("smGrid"),

    /**
     * 大网格
     * OOXML: lgGrid
     */
    LARGE_GRID("lgGrid"),

    /**
     * 点网格
     * OOXML: dotGrid
     */
    DOT_GRID("dotGrid"),

    /**
     * 小彩屑
     * OOXML: smConfetti
     */
    SMALL_CONFETTI("smConfetti"),

    /**
     * 大彩屑
     * OOXML: lgConfetti
     */
    LARGE_CONFETTI("lgConfetti"),

    /**
     * 水平砖块
     * OOXML: horzBrick
     */
    HORIZONTAL_BRICK("horzBrick"),

    /**
     * 斜向砖块
     * OOXML: diagBrick
     */
    DIAGONAL_BRICK("diagBrick"),

    /**
     * 实心菱形
     * OOXML: solidDmnd
     */
    SOLID_DIAMOND("solidDmnd"),

    /**
     * 空心菱形
     * OOXML: openDmnd
     */
    OPEN_DIAMOND("openDmnd"),

    /**
     * 点菱形
     * OOXML: dotDmnd
     */
    DOT_DIAMOND("dotDmnd"),

    /**
     * 格子花纹
     * OOXML: plaid
     */
    PLAID("plaid"),

    /**
     * 球体
     * OOXML: sphere
     */
    SPHERE("sphere"),

    /**
     * 编织花纹
     * OOXML: weave
     */
    WEAVE("weave"),

    /**
     * 点状花纹
     * OOXML: divot
     */
    DIVOT("divot"),

    /**
     * 瓦片花纹
     * OOXML: shingle
     */
    SHINGLE("shingle"),

    /**
     * 波浪花纹
     * OOXML: wave
     */
    WAVE("wave"),

    /**
     * 格子架花纹
     * OOXML: trellis
     */
    TRELLIS("trellis"),

    /**
     * 锯齿花纹
     * OOXML: zigZag
     */
    ZIG_ZAG("zigZag");

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
        return null;
    }

    /**
     * 是否是百分比点状图案
     */
    public boolean isPercentPattern() {
        return name().startsWith("PERCENT_");
    }
}