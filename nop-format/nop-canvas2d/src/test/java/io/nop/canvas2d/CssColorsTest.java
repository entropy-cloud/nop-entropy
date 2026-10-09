package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CSS 颜色解析值断言与抛错分支（plan 2359：HSL 独立实现禁 HSBtoRGB 代用的回归保护）。
 */
class CssColorsTest {

    @Test
    void hslPrimaryRedMustBePureRed() {
        // HSBtoRGB 代用会得 (128,0,0)；规范要求 hsl(0,100%,50%) == #ff0000
        assertEquals(new Color(255, 0, 0), CssColors.parse("hsl(0,100%,50%)"));
        assertEquals(new Color(255, 0, 0), CssColors.parse("hsl(0,100%,50%)".toUpperCase()));
        // 其他 HSL 基准值
        assertEquals(new Color(0, 255, 0), CssColors.parse("hsl(120,100%,50%)"));
        assertEquals(new Color(0, 0, 255), CssColors.parse("hsl(240,100%,50%)"));
        assertEquals(new Color(255, 255, 255), CssColors.parse("hsl(0,0%,100%)"));
        assertEquals(new Color(0, 0, 0), CssColors.parse("hsl(0,0%,0%)"));
    }

    @Test
    void hexForms() {
        assertEquals(new Color(255, 0, 0), CssColors.parse("#f00"));
        assertEquals(new Color(255, 0, 0), CssColors.parse("#ff0000"));
        assertEquals(new Color(255, 0, 0, 0x80), CssColors.parse("#ff000080"));
        assertEquals(new Color(255, 0, 0, 136), CssColors.parse("#f008"));
    }

    @Test
    void rgbForms() {
        assertEquals(new Color(84, 112, 198), CssColors.parse("rgb(84,112,198)"));
        assertEquals(new Color(84, 112, 198, 128), CssColors.parse("rgba(84,112,198,0.5)"));
        // % 形态
        assertEquals(new Color(255, 0, 0), CssColors.parse("rgb(100%,0%,0%)"));
        assertEquals(new Color(128, 0, 0, 255), CssColors.parse("rgba(50%,0%,0%,1)"));
        // modern 空格语法（裁定支持）
        assertEquals(new Color(51, 51, 51, 128), CssColors.parse("rgb(51 51 51 / 0.5)"));
        assertEquals(new Color(255, 0, 0, 128), CssColors.parse("rgba(255 0 0 / 50%)"));
    }

    @Test
    void namedColorsAndTransparent() {
        assertEquals(new Color(0xFF0000), CssColors.parse("red"));
        assertEquals(new Color(0x00FF00), CssColors.parse("lime"));
        assertEquals(new Color(0x008080), CssColors.parse("teal"));
        assertEquals(new Color(0xFFA500), CssColors.parse("orange"));
        assertEquals(new Color(0x808080), CssColors.parse("grey"));
        assertEquals(new Color(0, 0, 0, 0), CssColors.parse("transparent"));
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse("nope"));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse("#12"));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse("#12345"));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse("rgb(1,2)"));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse("hsl(a,b,c)"));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse(""));
        assertThrows(NopCanvas2DException.class, () -> CssColors.parse(null));
    }

    @Test
    void hslNegativeHueNormalizes() {
        assertEquals(new Color(255, 0, 0), CssColors.parse("hsl(-360deg,100%,50%)"));
        assertEquals(new Color(0, 0, 255), CssColors.parse("hsl(-120deg,100%,50%)"));
    }
}
