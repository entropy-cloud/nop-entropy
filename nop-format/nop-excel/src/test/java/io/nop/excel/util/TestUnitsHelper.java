package io.nop.excel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Excel 单位换算语义：EMU/点/像素/twips/列宽像素换算与 FixedPoint 定点数双向转换。
 */
public class TestUnitsHelper {

    @Test
    public void testEmuConversions() {
        // 1 点 = 12700 EMU, 1 像素 = 9525 EMU（96 DPI）
        assertEquals(12700, UnitsHelper.pointsToEMU(1));
        assertEquals(19050, UnitsHelper.pixelToEMU(2));
        assertEquals(2.0, UnitsHelper.emuToPoints(25400), 1e-9);
        // 列宽 256 分之 1 字符单位
        assertEquals(UnitsHelper.charactersToEMU(1), UnitsHelper.columnWidthToEMU(256));
        assertEquals(UnitsHelper.EMU_PER_CHARACTER, UnitsHelper.charactersToEMU(1));
    }

    // FixedPoint（16.16 定点数）与 double 双向转换 roundtrip（负数编码有精度损失，见 final report quirk 记录）
    @Test
    public void testFixedPointRoundtrip() {
        assertEquals(98304, UnitsHelper.doubleToFixedPoint(1.5));
        assertEquals(1.5, UnitsHelper.fixedPointToDouble(98304), 1e-12);

        for (double v : new double[]{0.0, 1.5, 2.25, 123.456}) {
            // 16.16 定点数最低分辨率为 1/65536，量化误差在半个 ulp 内
            assertEquals(v, UnitsHelper.fixedPointToDouble(UnitsHelper.doubleToFixedPoint(v)), 1e-4);
        }
    }

    @Test
    public void testPointPixelTwipsInchConversions() {
        // 96/72 DPI 换算
        assertEquals(96, UnitsHelper.pointsToPixel(72));
        assertEquals(72.0, UnitsHelper.pixelToPoints(96), 1e-9);

        // twips = 1/20 磅
        assertEquals(12700, UnitsHelper.twipsToEMU(20));
        assertEquals(50, UnitsHelper.pointsToTwips(2.5));
        assertEquals(2.5, UnitsHelper.twipsToPoints(50), 1e-9);

        // 英寸/主 DPI
        assertEquals(144.0, UnitsHelper.inchesToPoints(2), 1e-9);
        assertEquals(2.0, UnitsHelper.pointsToInches(144), 1e-9);
        assertEquals(72.0, UnitsHelper.masterToPoints(576), 1e-9);
        assertEquals(576, UnitsHelper.pointsToMaster(72));
    }

    // 列宽 256 分之单位 -> 像素：每 256 单位 7px，余数按比例
    @Test
    public void testColumnWidthToPixels() {
        // 8 个标准字符宽（8*256 单位）= 8*7 = 56 px
        assertEquals(56, UnitsHelper.getColumnWidthInPx(8 * 256));
        // 1 个字符宽
        assertEquals(7, UnitsHelper.getColumnWidthInPx(256));
        // 半个字符宽（128 单位）：浮点除法 128/(256/7)=3.4999... 舍入为 3px
        assertEquals(3, UnitsHelper.getColumnWidthInPx(128));
    }
}
