package io.nop.report.pdf.utils;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTextWrapHelper {

    static PDFont helvetica() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /**
     * 单个字符宽度超过maxWidth时，按单词折行不应死循环
     */
    @Test
    @Timeout(10)
    public void testWrapByWordSingleCharTooWide() throws Exception {
        PDFont font = helvetica();
        // 超大字号使单字符宽度超过maxWidth
        List<String> lines = TextWrapHelper.splitTextIntoLines("ab cd", font, 10000f, 20f, 0);
        // 所有字符必须保留，不允许静默丢失
        String joined = String.join("", lines);
        assertEquals("abcd", joined.replace(" ", ""));
    }

    @Test
    @Timeout(10)
    public void testWrapByCharacterSingleCharTooWide() throws Exception {
        PDFont font = helvetica();
        List<String> lines = TextWrapHelper.splitTextIntoLines("abc", font, 10000f, 20f, 1);
        // 不应产生空行，也不应丢失文本
        for (String line : lines) {
            assertTrue(!line.isEmpty(), "should not produce empty lines: " + lines);
        }
        assertEquals("abc", String.join("", lines));
    }

    @Test
    @Timeout(10)
    public void testWrapForcedUnaffected() throws Exception {
        PDFont font = helvetica();
        List<String> lines = TextWrapHelper.splitTextIntoLines("abc def", font, 10000f, 20f, 2);
        assertEquals("abcdef", String.join("", lines).replace(" ", ""));
    }
}
