package io.nop.office.model;

import io.nop.office.model.constants.OfficeFontFamily;
import io.nop.office.model.constants.OfficeFontUnderline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OfficeFont CSS 生成语义：font-family/font-weight/font-style/font-size/color/text-decoration
 * 按字段非空才输出；字号浮点尾部 ".0" 省略；下划线样式区分 single/single-accounting/none
 */
public class TestOfficeFontCssStyle {

    private static String css(OfficeFont font) {
        StringBuilder sb = new StringBuilder();
        font.toCssStyle(sb);
        return sb.toString();
    }

    @Test
    public void testDefaultFontProducesFamilySizeColor() {
        String css = css(OfficeFont.DEFAULT_FONT);
        assertTrue(css.contains("font-family:\"Calibri\",sans-serif;\n"), css);
        assertTrue(css.contains("font-size:11pt;\n"), css);
        // 默认字体色 "0x0" 必须补零为合法 CSS 色值
        assertTrue(css.contains("color:#000000;\n"), css);
        assertFalse(css.contains("font-weight"), css);
    }

    @Test
    public void testBoldItalicAndSize() {
        OfficeFont font = new OfficeFont();
        font.setFontName("Arial");
        font.setBold(true);
        font.setItalic(true);
        font.setFontSize(10.5f);
        font.setFontColor("0xFF0000");

        String css = css(font);
        assertTrue(css.contains("font-family:\"Arial\";\n"), css);
        assertTrue(css.contains("font-weight:bold;\n"), css);
        assertTrue(css.contains("font-style:italic;\n"), css);
        // 10.5 无 ".0" 尾巴，原样输出
        assertTrue(css.contains("font-size:10.5pt;\n"), css);
        assertTrue(css.contains("color:#FF0000;\n"), css);
    }

    @Test
    public void testFontSizeStringOmitsTrailingZero() {
        OfficeFont font = new OfficeFont();
        font.setFontSize(11f);
        assertEquals("11", font.getFontSizeString());
        font.setFontSize(10.5f);
        assertEquals("10.5", font.getFontSizeString());
        font.setFontSize(null);
        assertNull(font.getFontSizeString());
    }

    @Test
    public void testUnderlineSingleDecoration() {
        OfficeFont font = new OfficeFont();
        font.setUnderlineStyle(OfficeFontUnderline.SINGLE);
        assertTrue(css(font).contains("text-decoration:underline;\n"), css(font));
    }

    @Test
    public void testUnderlineSingleAccountingAddsStyle() {
        OfficeFont font = new OfficeFont();
        font.setUnderlineStyle(OfficeFontUnderline.SINGLE_ACCOUNTING);
        String css = css(font);
        assertTrue(css.contains("text-decoration:underline;\n"), css);
        assertTrue(css.contains("text-underline-style:single-accounting;\n"), css);
    }

    @Test
    public void testUnderlineNoneOmitsDecoration() {
        OfficeFont font = new OfficeFont();
        font.setFontName("Arial");
        font.setUnderlineStyle(OfficeFontUnderline.NONE);
        assertFalse(css(font).contains("text-decoration"), css(font));
    }

    @Test
    public void testCssFontFamilyResolvesFromFamilyText() {
        OfficeFont font = new OfficeFont();
        assertNull(font.getCssFontFamily());
        font.setFontFamily("swiss");
        assertEquals("sans-serif", font.getCssFontFamily());
        font.setFontFamily("not-a-family");
        assertNull(font.getCssFontFamily());
        assertEquals(OfficeFontFamily.SWISS, OfficeFontFamily.fromText("swiss"));
        assertNull(OfficeFontFamily.fromCode(6));
    }

    @Test
    public void testDefaultFontIsFrozenSingleton() {
        assertEquals("Calibri", OfficeFont.DEFAULT_FONT.getFontName());
        assertEquals(OfficeFont.DEFAULT_FONT, OfficeFont.DEFAULT_FONT);
        assertTrue(OfficeFont.DEFAULT_FONT.frozen());
    }
}
