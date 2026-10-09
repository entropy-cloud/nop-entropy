package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 Phase 1：font 解析、FontMapper、度量缓存（D5/D6/D7 裁定的回归保护）。
 */
class CssFontParserTest {

    @Test
    void plainFormParses() {
        CssFont f = CssFontParser.parse("12px sans-serif");
        assertEquals(Font.PLAIN, f.style());
        assertEquals(Font.PLAIN, f.weight());
        assertEquals(12f, f.sizePx());
        assertEquals(0f, f.lineHeight());
        assertEquals(List.of("sans-serif"), f.families());
    }

    @Test
    void fullFormParses() {
        CssFont f = CssFontParser.parse("italic bold 16px 'Times New Roman', serif");
        assertEquals(Font.ITALIC, f.style());
        assertEquals(Font.BOLD, f.weight());
        assertEquals(16f, f.sizePx());
        assertEquals(List.of("Times New Roman", "serif"), f.families());
    }

    @Test
    void lineHeightFractionParses() {
        // D5：line-height 分数语法接受（zrender 当前不产出，浏览器允许）
        CssFont f = CssFontParser.parse("bold 12px/1.5 sans-serif");
        assertEquals(18f, f.lineHeight());
        assertEquals(12f, f.sizePx());
    }

    @Test
    void bolderLighterAndNumericWeights() {
        // D5：bolder→BOLD、lighter→PLAIN、数字 ≥600→BOLD
        assertEquals(Font.BOLD, CssFontParser.parse("bolder 14px sans-serif").weight());
        assertEquals(Font.PLAIN, CssFontParser.parse("lighter 14px sans-serif").weight());
        assertEquals(Font.BOLD, CssFontParser.parse("700 14px sans-serif").weight());
        assertEquals(Font.PLAIN, CssFontParser.parse("300 14px sans-serif").weight());
    }

    @Test
    void obliqueMapsToItalic() {
        assertEquals(Font.ITALIC, CssFontParser.parse("oblique 12px serif").style());
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("12pt sans-serif"));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("1.5em serif"));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("small-caps 12px serif"));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("12px"));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse(""));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse(null));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("garbage 12px sans-serif"));
        assertThrows(NopCanvas2DException.class, () -> CssFontParser.parse("-3px sans-serif"));
    }
}
