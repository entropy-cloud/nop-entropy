package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 Phase 2：truncate/splitLines（对齐 zrender truncateText2/wrapText 语义）。
 * 消费者裁定：无引擎消费者（zrender JS 自带），本工具为 Java 侧基础设施——
 * 测试钉住语义规格本身。
 */
class TextLayoutUtilsTest {
    private static final String FONT = "14px sans-serif";
    private static final TextMeasurer MEASURER = new TextMeasurer();

    @Test
    void fittingTextReturnedAsIs() {
        String text = "hi";
        double width = MEASURER.measure(text, FONT).getWidth();
        assertEquals(text, TextLayoutUtils.truncateText(text, FONT, width + 10, MEASURER));
    }

    @Test
    void overflowTruncatedWithEllipsisInsideMaxWidth() {
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        double narrow = MEASURER.measure("ABCDE", FONT).getWidth();
        String truncated = TextLayoutUtils.truncateText(text, FONT, narrow, MEASURER);
        assertTrue(truncated.endsWith("..."), "truncated text must end with ellipsis: " + truncated);
        assertTrue(MEASURER.measure(truncated, FONT).getWidth() <= narrow,
                "truncated result must fit maxWidth");
        assertTrue(truncated.length() < text.length());
    }

    @Test
    void ellipsisWiderThanMaxWidthReturnsEmpty() {
        // zrender truncateText2：省略号自身超宽 → 空串
        assertEquals("", TextLayoutUtils.truncateText("ABCDEF", FONT, 2, ".", MEASURER));
    }

    @Test
    void customEllipsisHonored() {
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        double narrow = MEASURER.measure("ABC>", FONT).getWidth();
        String truncated = TextLayoutUtils.truncateText(text, FONT, narrow, ">", MEASURER);
        assertTrue(truncated.endsWith(">"));
        assertTrue(MEASURER.measure(truncated, FONT).getWidth() <= narrow);
    }

    @Test
    void splitLinesOnNewline() {
        List<String> lines = TextLayoutUtils.splitLines("a\nb\nc", FONT, 0, MEASURER);
        assertEquals(List.of("a", "b", "c"), lines);
    }

    @Test
    void cjkTextWrapsPerCharacter() {
        // 非字母即断点：CJK 逐字断行
        String text = "中文中文中文中文中文";
        double twoChars = MEASURER.measure("中文", FONT).getWidth();
        List<String> lines = TextLayoutUtils.splitLines(text, FONT, twoChars, MEASURER);
        assertEquals(5, lines.size());
        for (String line : lines)
            assertEquals(2, line.length());
        assertEquals(text, String.join("", lines));
    }

    @Test
    void englishWordKeptWholeWhenItFits() {
        // 词边界保持：英文单词不断开
        String text = "aaa bbb ccc";
        double twoWords = MEASURER.measure("aaa bbb", FONT).getWidth();
        List<String> lines = TextLayoutUtils.splitLines(text, FONT, twoWords, MEASURER);
        assertEquals(2, lines.size());
        assertEquals("aaa bbb", lines.get(0));
        assertEquals("ccc", lines.get(1));
    }

    @Test
    void overlongWordHardBroken() {
        // 单词自身超宽：硬断
        String text = "AAAAAAAAAAAAAAAAAAAA";
        double fourChars = MEASURER.measure("AAAA", FONT).getWidth();
        List<String> lines = TextLayoutUtils.splitLines(text, FONT, fourChars, MEASURER);
        assertTrue(lines.size() >= 4);
        for (String line : lines)
            assertTrue(MEASURER.measure(line, FONT).getWidth() <= fourChars,
                    "each hard-broken line must fit: " + line);
        assertEquals(text, String.join("", lines));
    }

    @Test
    void nullCoercesToNullString() {
        assertEquals(List.of("null"), TextLayoutUtils.splitLines(null, FONT, 0, MEASURER));
        assertEquals("null", TextLayoutUtils.truncateText(null, FONT, 1000, MEASURER));
    }
}
