package io.nop.excel.model.color;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 6位RRGGBB颜色的ARGB换算：修复前rgb()不置alpha位，6位hex颜色经
 * new Color(argb, true) 全透明，且纯黑 #000000 与 toArgbInt 的0哨兵混淆
 */
public class TestColorHelper {

    @Test
    public void testToArgbIntSetsOpaqueAlpha() {
        int argb = ColorHelper.toArgbInt("#5470C6");
        assertEquals(0xFF, new Color(argb, true).getAlpha(), "6位hex应是不透明颜色");
        assertEquals(0x54, new Color(argb, true).getRed());
        assertEquals(0xC6, new Color(argb, true).getBlue());
    }

    @Test
    public void testBlackIsNotSentinel() {
        assertNotEquals(0, ColorHelper.toArgbInt("#000000"), "纯黑不能与无效色哨兵0混淆");
        assertEquals(0xFF000000, ColorHelper.toArgbInt("#000000"));
        assertEquals(0, ColorHelper.toArgbInt(null));
        assertEquals(0, ColorHelper.toArgbInt(""));
    }

    @Test
    public void testArgb8DigitsUnchanged() {
        assertEquals(0x80FF0000, ColorHelper.toArgbInt("80FF0000"));
    }

    @Test
    public void testToCssColorPadsShortHex() {
        // 默认字体色"0x0"必须输出合法CSS（修复前输出"#0"，浏览器丢弃该声明）
        assertEquals("#000000", ColorHelper.toCssColor("0x0"));
        assertEquals("#5470c6", ColorHelper.toCssColor("#5470c6"));
        assertTrue(ColorHelper.toCssColor("80FF0000").startsWith("rgba("));
    }


    @Test
    public void testParseRgbaAllForms() {
        // 命名色（AWT 精确值）
        assertArrayEquals(new int[]{0, 128, 0, 255}, ColorHelper.parseRgba("green"));
        assertArrayEquals(new int[]{128, 128, 128, 255}, ColorHelper.parseRgba("gray"));
        // # 前缀
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorHelper.parseRgba("#FF0000"));
        // 8 位 AARRGGBB：alpha 提取
        assertArrayEquals(new int[]{255, 0, 0, 128}, ColorHelper.parseRgba("#80FF0000"));
        // 0x 前缀
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorHelper.parseRgba("0xFF0000"));
        // 裸 hex
        assertArrayEquals(new int[]{0, 255, 0, 255}, ColorHelper.parseRgba("00FF00"));
        // 1-2 位补零
        assertArrayEquals(new int[]{0, 0, 0, 255}, ColorHelper.parseRgba("0"));
        // rgb()/rgba()
        assertArrayEquals(new int[]{10, 20, 30, 255}, ColorHelper.parseRgba("rgb(10,20,30)"));
        assertArrayEquals(new int[]{10, 20, 30, 127}, ColorHelper.parseRgba("rgba(10,20,30,0.5)"));
        // 非法 null 不抛
        assertNull(ColorHelper.parseRgba(null));
        assertNull(ColorHelper.parseRgba(""));
        assertNull(ColorHelper.parseRgba("not-a-color"));
        assertNull(ColorHelper.parseRgba("rgb(1,2)"));
        assertNull(ColorHelper.parseRgba("rgb(300,0,0)"));
    }

    @Test
    public void testToNormalizedRgbRgbaNoCrash() {
        // WI9 修复：rgba() 填充此前抛 IllegalArgumentException
        float[] ret = ColorHelper.toNormalizedRgb("rgba(255,0,0,0.5)");
        assertEquals(1.0f, ret[0], 0.01f);
        assertEquals(0.0f, ret[1], 0.01f);
    }

    @Test
    public void testToNormalizedRgbFreshArray() {
        // WI9 审计要求：返回 fresh 数组（调用方 ColorModificationHelper 原地 mutate）
        float[] a = ColorHelper.toNormalizedRgb(null);
        a[0] = 999;
        float[] b = ColorHelper.toNormalizedRgb(null);
        assertEquals(0.0f, b[0], 0.001f, "must return fresh array, not shared constant");
    }
}