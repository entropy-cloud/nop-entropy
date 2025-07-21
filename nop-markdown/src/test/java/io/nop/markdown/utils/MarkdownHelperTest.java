package io.nop.markdown.utils;

import io.nop.api.core.beans.IntRangeBean;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.markdown.utils.MarkdownHelper.findImagePositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}