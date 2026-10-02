package io.nop.excel.model.color;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.model.constants.ExcelPaperSize;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Excel 预定义颜色索引与 ARGB 的双向映射语义；纸张尺寸枚举的尺寸与索引边界语义。
 */
public class TestPredefinedColorsIndex {

    // 颜色索引/ARGB 常量语义：RED 索引 0x0A，ARGB 为 FF+RGB 大写十六进制
    @Test
    public void testPredefinedColorArgbAndIndex() {
        assertEquals(0x0A, PredefinedColors.RED.getIndex());
        assertEquals(0xFF0000, PredefinedColors.RED.getRgb());
        // rgb 字段为 24 位 RGB，高 8 位恒为 0（argb 字符串中的 FF 不会进入 getRgb/getAlpha）
        assertEquals(0, PredefinedColors.RED.getAlpha());
        assertEquals(255, PredefinedColors.RED.getRed());
        assertEquals(0, PredefinedColors.RED.getGreen());
        assertEquals(0, PredefinedColors.RED.getBlue());
        assertEquals("FFFF0000", PredefinedColors.RED.toString());
        assertEquals("FFFF0000", PredefinedColors.RED.getArgb());

        // WHITE 无第二索引
        assertEquals(-1, PredefinedColors.WHITE.getIndex2());
        assertEquals(0x09, PredefinedColors.WHITE.getIndex());
    }

    // getColorIndex / getByIndex 语义：按 values() 顺序注册，indexColors 先注册者优先，indexMap 后写覆盖
    @Test
    public void testColorIndexRoundtrip() {
        // 以注册顺序模拟两张索引表的构建规则
        Map<Integer, PredefinedColors> indexColors = new HashMap<>();
        Map<String, Integer> indexMap = new HashMap<>();
        for (PredefinedColors value : PredefinedColors.values()) {
            indexColors.putIfAbsent(value.getIndex(), value);
            if (value.getIndex2() != -1) {
                indexColors.putIfAbsent(value.getIndex2(), value);
            }
            indexMap.put(value.getArgb(), value.getIndex());
        }

        for (PredefinedColors color : PredefinedColors.values()) {
            assertEquals(indexColors.get(color.getIndex()), PredefinedColors.getByIndex(color.getIndex()),
                    color.name());
            assertEquals(indexMap.get(color.getArgb()), PredefinedColors.getColorIndex(color.getArgb()),
                    color.name());
        }

        // 已知冲突：MAROON 的 index 0x19 与 PLUM 的 index2 相同，先注册的 PLUM 胜出
        assertEquals(PredefinedColors.PLUM, PredefinedColors.getByIndex(0x19));
        // BLACK 与 AUTOMATIC 同为 FF000000，indexMap 后写覆盖为 AUTOMATIC 的 index
        assertEquals(0x40, PredefinedColors.getColorIndex("FF000000").intValue());
        assertEquals(PredefinedColors.BLACK, PredefinedColors.getByIndex(0x08));
        assertEquals(PredefinedColors.AUTOMATIC, PredefinedColors.getByIndex(0x40));

        // 不在预定义表中的颜色
        assertNull(PredefinedColors.getColorIndex("FF123456"));
        assertNull(PredefinedColors.getByIndex(-1));
    }

    // 纸张尺寸枚举：A4 尺寸 595x842（单位 1/72 英寸）
    @Test
    public void testPaperSizeDimensions() {
        assertEquals(595.0, ExcelPaperSize.A4_PAPER.getWidth(), 1e-9);
        assertEquals(842.0, ExcelPaperSize.A4_PAPER.getHeight(), 1e-9);
        assertEquals(595.0f, ExcelPaperSize.A4_PAPER.getFloatWidth(), 1e-6f);
        assertEquals(842.0f, ExcelPaperSize.A4_PAPER.getFloatHeight(), 1e-6f);
        assertEquals(595.0, ExcelPaperSize.DEFAULT.getWidth(), 1e-9);
    }

    // of(index) 按枚举序数取值，越界必须报 ERR_EXCEL_INVALID_PAPER_SIZE
    @Test
    public void testPaperSizeOfBounds() {
        assertEquals(ExcelPaperSize.DEFAULT, ExcelPaperSize.of(0));
        assertThrows(NopException.class, () -> ExcelPaperSize.of(-1));
        assertThrows(NopException.class, () -> ExcelPaperSize.of(ExcelPaperSize.values().length));
    }
}
