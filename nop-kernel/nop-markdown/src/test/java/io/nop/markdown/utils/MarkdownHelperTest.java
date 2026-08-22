package io.nop.markdown.utils;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.markdown.utils.MarkdownHelper.findImagePositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownHelperTest {

    @Test
    void testNormalImage() {
        String s = "abc ![alt](url) xyz";
        List<IntRangeBean> list = findImagePositions(s);
        assertEquals(1, list.size());
        IntRangeBean bean = list.get(0);
        assertEquals(s.indexOf("!"), bean.getStart());
        assertEquals(s.indexOf(")"), bean.getLast());
        assertEquals("![alt](url)", s.substring(bean.getStart(), bean.getEnd()));
    }

    @Test
    void testNoImage() {
        String s = "abc !alt](url) or ![alt]url) or ![alt](url";
        List<IntRangeBean> list = findImagePositions(s);
        assertTrue(list.isEmpty());
    }

    @Test
    void testMultipleImages() {
        String s = "First ![a](u) Second ![b](v) End";
        List<IntRangeBean> list = findImagePositions(s);
        assertEquals(2, list.size());
        assertEquals("![a](u)", s.substring(list.get(0).getStart(), list.get(0).getEnd()));
        assertEquals("![b](v)", s.substring(list.get(1).getStart(), list.get(1).getEnd()));
    }

    @Test
    void testImageWithNewline() {
        String s = "abc ![alt]\n(url) xyz";
        List<IntRangeBean> list = findImagePositions(s);
        assertTrue(list.isEmpty());

        s = "abc ![alt](ur\nl) xyz";
        list = findImagePositions(s);
        assertTrue(list.isEmpty());
    }

    @Test
    void testParenthesisInUrlUnmatched() {
        String s = "abc ![alt](u(r)l) xyz";
        // 只匹配到第一个 ')'
        List<IntRangeBean> list = findImagePositions(s);
        assertEquals(1, list.size());
        assertEquals("![alt](u(r)", s.substring(list.get(0).getStart(), list.get(0).getEnd()));
    }

    @Test
    void testImageAtStartAndEnd() {
        String s = "![start](a) and ![end](b)";
        List<IntRangeBean> list = findImagePositions(s);
        assertEquals(2, list.size());
        assertEquals("![start](a)", s.substring(list.get(0).getStart(), list.get(0).getEnd()));
        assertEquals("![end](b)", s.substring(list.get(1).getStart(), list.get(1).getEnd()));
    }

    @Test
    void testRemoveStyleSingleStarDoesNotThrow() {
        // 修复前 "*" / "**" / "***" 走 substring(begin>end) 抛 StringIndexOutOfBoundsException
        assertEquals("*", MarkdownHelper.removeStyle("*"));
        assertEquals("**", MarkdownHelper.removeStyle("**"));
        // "***" 视为 * 包裹 * 的样式，剥一层
        assertEquals("*", MarkdownHelper.removeStyle("***"));
        // 修复前 "___" 越界
        assertEquals("___", MarkdownHelper.removeStyle("___"));
        // 正常剥除行为不变
        assertEquals("a", MarkdownHelper.removeStyle("*a*"));
        assertEquals("a", MarkdownHelper.removeStyle("**a**"));
        assertEquals("a", MarkdownHelper.removeStyle("***a***"));
        assertEquals("a", MarkdownHelper.removeStyle("___a___"));
        assertEquals("plain", MarkdownHelper.removeStyle("plain"));
    }

    @Test
    void testChangeLinkUrlDoesNotMutateInputAndKeepsPairing() {
        // 两个链接，传入逆序的 posList 与配对的 newUrls
        String text = "first [a](u1) mid [b](u2) last";
        List<IntRangeBean> sorted = MarkdownHelper.findLinkPositions(text, false);
        assertEquals(2, sorted.size());
        // 逆序传入：pos[b] 配 newUrl X，pos[a] 配 newUrl Y
        List<IntRangeBean> reversed = List.of(sorted.get(1), sorted.get(0));
        List<String> urls = List.of("X", "Y");

        String result = MarkdownHelper.changeLinkUrl(text, reversed, urls);

        // 修复前：Collections.sort 原地重排 posList 但不重排 urls，且修改了调用方列表
        assertTrue(result.contains("[a](Y)"), result);
        assertTrue(result.contains("[b](X)"), result);
        // 输入列表不被原地修改
        assertEquals(sorted.get(1), reversed.get(0));
        assertEquals(sorted.get(0), reversed.get(1));
    }

    @Test
    void testChangeLinkUrlSizeMismatchThrowsNopException() {
        String text = "first [a](u1) mid [b](u2) last";
        List<IntRangeBean> list = MarkdownHelper.findLinkPositions(text, false);
        NopException e = assertThrows(NopException.class,
                () -> MarkdownHelper.changeLinkUrl(text, list, List.of("only-one")));
        assertEquals("nop.err.markdown.pos-list-size-not-match", e.getErrorCode());
    }
}