package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15：v2 outline 驱动回填（headingizeFromOutline :70-126 直译）——
 * 整键相等、窗口语义（上一物理页锚行+1 或本页锚行+1 → 本页块末）、
 * 原位升级 vs 锚后插入、depth clamp、无锚 skip。
 */
public class TestOutlineBackfiller {

    private static String anchor(int n) {
        return "<a id=\"pdf-page-" + n + "\"></a>";
    }

    private static PdfOutlineEntry entry(String title, int depth, int page) {
        return new PdfOutlineEntry().setTitle(title).setDepth(depth).setPage(page);
    }

    @Test
    public void testExactMatchReplacesInPlace() {
        String md = anchor(1) + "\nCover\n"
                + anchor(2) + "\nIntroduction\nbody text\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Introduction", 1, 2)));
        assertEquals(1, r.getStats().getReplaced());
        assertTrue(r.getMd().contains("# Introduction"), r.getMd());
        assertTrue(r.getStats().getInserted() == 0);
    }

    @Test
    public void testInsertAfterAnchorWhenNotFound() {
        String md = anchor(1) + "\nCover\n"
                + anchor(2) + "\nbody text about something else\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Missing Chapter", 2, 2)));
        assertEquals(1, r.getStats().getInserted());
        assertEquals(0, r.getStats().getReplaced());
        assertTrue(r.getMd().contains("## Missing Chapter"), r.getMd());
    }

    @Test
    public void testDepthClampTo6() {
        String md = anchor(1) + "\nCover\n"
                + anchor(2) + "\nDeep Section\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Deep Section", 9, 2)));
        assertTrue(r.getMd().contains("###### Deep Section"), r.getMd());
        assertFalse(r.getMd().contains("####### Deep Section"), r.getMd());
    }

    @Test
    public void testNoAnchorSkipped() {
        String md = anchor(1) + "\nCover\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Orphan Chapter", 1, 7)));
        assertEquals(1, r.getStats().getSkippedNoAnchor());
        assertFalse(r.getMd().contains("Orphan"));
    }

    @Test
    public void testWindowCoversPrevPageBottom() {
        // 章节 opener 在上一页底部：entry.page=2 但标题行在 page 1 末尾
        String md = anchor(1) + "\nCover\nChapter Opener\n"
                + anchor(2) + "\nbody continues\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Chapter Opener", 1, 2)));
        assertEquals(1, r.getStats().getReplaced(), "window must include prev page bottom");
        assertTrue(r.getMd().contains("# Chapter Opener"), r.getMd());
    }

    @Test
    public void testNoAnchorReturnsAsIs() {
        String md = "plain text without anchors";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("Anything", 1, 1)));
        assertEquals(md, r.getMd(), "无锚原样返回");
        assertEquals(0, r.getStats().getInserted());
        assertEquals(0, r.getStats().getSkippedNoAnchor(), "无锚路径循环不执行（JS :89-90 同语义）");
    }

    @Test
    public void testShortTitleUnmatched() {
        String md = anchor(1) + "\nab\n";
        OutlineBackfiller.Result r = OutlineBackfiller.backfill(md,
                List.of(entry("ab", 1, 1)));
        assertEquals(1, r.getStats().getUnmatched(), "title <3 chars → unmatched");
    }

}
