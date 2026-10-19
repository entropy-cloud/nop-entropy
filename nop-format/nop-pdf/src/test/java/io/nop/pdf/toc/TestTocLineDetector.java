package io.nop.pdf.toc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15：TOC_ENTRY_RE 三页码形态直译（物理页/逻辑页/中文汇编卷；\r 容忍；
 * NaN 伪条目守卫——headingize :17/:182 原文语义）。
 */
public class TestTocLineDetector {

    @Test
    public void testThreePageForms() {
        TocLine p = TocLineDetector.detect("Chapter 1 Intro .......... 12");
        assertEquals("Chapter 1 Intro", p.getTitle());
        assertEquals("12", p.getPageLabel());
        assertEquals(12, p.getPageNo());

        TocLine logical = TocLineDetector.detect("系统概述 ........ 3-1");
        assertEquals("系统概述", logical.getTitle());
        assertEquals("3-1", logical.getPageLabel());
        assertNull(logical.getPageNo(), "logical form: physical page unknown");

        TocLine annex = TocLineDetector.detect("附录内容 .......... 附件一-1");
        assertEquals("附录内容", annex.getTitle());
        assertEquals("附件一-1", annex.getPageLabel());
        assertNull(annex.getPageNo());
    }

    @Test
    public void testCarriageReturnTolerance() {
        TocLine t = TocLineDetector.detect("Chapter 1 Intro .......... 12\r");
        assertEquals(12, t.getPageNo());
    }

    @Test
    public void testSpaceRunLeader() {
        TocLine t = TocLineDetector.detect("Chapter 1 Intro          12");
        assertEquals(12, t.getPageNo());
    }

    @Test
    public void testNonTocLinesRejected() {
        assertNull(TocLineDetector.detect("plain body line"));
        assertNull(TocLineDetector.detect("ab 1"));
        assertNull(TocLineDetector.detect("标题与页码间无分隔 12"));
    }

    @Test
    public void testShortTitleRejected() {
        assertNull(TocLineDetector.detect("ab .......... 12"));
    }

    @Test
    public void testDetectAll() {
        List<TocLine> lines = TocLineDetector.detectAll(List.of(
                "<a id=\"pdf-page-2\"></a>",
                "Contents",
                "Chapter 1 Intro .......... 3",
                "body text",
                "Chapter 2 Setup .......... 4"));
        assertEquals(2, lines.size());
        assertEquals("Chapter 1 Intro", lines.get(0).getTitle());
    }

    @Test
    public void testDotSpaceMixedLeader() {
        TocLine t = TocLineDetector.detect("Chapter 1 . . . . . 7");
        assertEquals("Chapter 1", t.getTitle());
        assertEquals(7, t.getPageNo());
    }
}
