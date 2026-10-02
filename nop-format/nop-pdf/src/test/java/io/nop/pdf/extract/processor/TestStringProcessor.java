package io.nop.pdf.extract.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 文本预处理语义：空白剔除、全角->半角归一化、顺序数字扫描、整数替换（含年份识别）
 */
public class TestStringProcessor {

    @Test
    public void testRemoveWhitespaceRemovesAllWhitespace() {
        StringProcessor sp = new StringProcessor("a b\r\nc\u3000d\t");
        sp.removeWhitespace();
        assertEquals("abcd", sp.toString());
    }

    @Test
    public void testRemoveCrlfKeepsSpaces() {
        StringProcessor sp = new StringProcessor("a b\r\nc");
        sp.removeCrlf();
        assertEquals("a bc", sp.toString());
    }

    @Test
    public void testNormalizeDigitConvertsFullWidthToHalfWidth() {
        StringProcessor sp = new StringProcessor("１２３．５％—＋");
        sp.normalizeDigit();
        assertEquals("123.5%-+", sp.toString());
    }

    @Test
    public void testSearchDigitsAdvancesPositionSequentially() {
        StringProcessor sp = new StringProcessor("a12b34");
        assertEquals("12", sp.searchDigits());
        assertEquals(3, sp.pos());
        assertEquals("34", sp.searchDigits());
        assertEquals(6, sp.pos());
        assertNull(sp.searchDigits());
    }

    @Test
    public void testReplaceIntegerMarksYearPattern() {
        // 4位数字且首字符为'2'、次字符<='1' 时按年份替换
        StringProcessor sp = new StringProcessor("2023年12月");
        sp.replaceInteger("N");
        assertEquals("[YN]年N月", sp.toString());
    }

    @Test
    public void testReplaceIntegerNormalNumber() {
        StringProcessor sp = new StringProcessor("x 98 y");
        sp.replaceInteger("N");
        assertEquals("x N y", sp.toString());
    }

    @Test
    public void testStartsWithFindAndMoveClamp() {
        StringProcessor sp = new StringProcessor("hello world");
        assertTrue(sp.startsWith("hello"));
        assertFalse(sp.startsWith("world"));

        assertEquals(6, sp.find("world"));

        sp.move(8);
        assertFalse(sp.startsWith("world"));
        assertTrue(sp.startsWith("rld"));

        // move 超界被钳制在 length
        sp.move(100);
        assertEquals(11, sp.pos());
    }

    @Test
    public void testSubstringUnaffectedByPosition() {
        StringProcessor sp = new StringProcessor("abcdef");
        sp.move(3);
        assertEquals("abc", sp.substring(0, 3));
    }
}
