package io.nop.pdf.toc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI15：v1 toc 驱动回填——headingize :128-358 全机制直译的行为钉。
 */
public class TestTocBackfill {

    private static String anchor(int n) {
        return "<a id=\"pdf-page-" + n + "\"></a>";
    }

    /** n 页 md：每页锚 + 一行占位正文 */
    private String skeleton(int pages) {
        StringBuilder sb = new StringBuilder();
        for (int p = 1; p <= pages; p++) {
            sb.append(anchor(p)).append('\n');
            sb.append("Page ").append(p).append(" body\n");
        }
        return sb.toString();
    }

    // ========== 基本路径：exact/adjacent/页顶兜底 ==========

    @Test
    public void testExactAndPageTopFallback() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Cover\n");
        sb.append(anchor(2)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 3\n")
                .append("Chapter 2 Setup .......... 4\n")
                .append("Chapter 3 Notes .......... 5\n");
        sb.append(anchor(3)).append('\n').append("Chapter 1 Intro\nbody\n");
        sb.append(anchor(4)).append('\n').append("Chapter 2 Setup\nbody\n");
        sb.append(anchor(5)).append('\n').append("tail page\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        String out = result.getMd();
        // exact：Chapter 1 行原位升级
        assertTrue(out.contains("# Chapter 1 Intro"), out);
        // 兜底：Chapter 3（页 99 不存在）插在最后一个有锚页（5）顶部
        assertTrue(out.contains("# Chapter 3 Notes"), out);
        assertEquals(2, result.getStats().getByExact());
        assertEquals(1, result.getStats().getByPageTop());
        assertEquals(3, result.getStats().getTocEntries());
    }

    // ========== 目录区判定：条目 <3 放弃 ==========

    @Test
    public void testLessThanThreeEntriesAbandons() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 2\n")
                .append("Chapter 2 Setup .......... 2\n");
        sb.append(anchor(2)).append('\n').append("Chapter 1 Intro\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(0, result.getStats().getInserted(), "entries <3 must abandon");
        assertFalse(result.getMd().contains("# Chapter 1"));
    }

    // ========== pageOffset 扫描 ==========

    @Test
    public void testPageOffsetScanAdoptsShift() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Cover\n");
        sb.append(anchor(2)).append('\n').append("Contents\n");
        for (int c = 1; c <= 10; c++)
            sb.append("Chapter ").append(c).append(" Title .......... ").append(c + 2).append('\n');
        // 实际标题在 目录页码+2 的物理页（且都不指向 TOC 页自身——c=1 条目 page 3 可评分）
        for (int c = 1; c <= 10; c++) {
            sb.append(anchor(c + 4)).append('\n').append("Chapter ").append(c).append(" Title\nbody\n");
        }
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(2, result.getStats().getPageOffset().intValue(), "offset 2 must be adopted");
        // 页码被整体平移后 exact 命中
        assertEquals(10, result.getStats().getByExact());
    }

    // ========== tocDepth 深度过滤 ==========

    @Test
    public void testTocDepthFilter() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("2 Overview .......... 3\n")
                .append("2.1 Scope .......... 3\n")
                .append("2.1.1 Detail .......... 3\n")
                .append("3 Shutdown .......... 3\n");
        sb.append(anchor(3)).append('\n')
                .append("2 Overview\n2.1 Scope\n2.1.1 Detail\n3 Shutdown\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md,
                new BackfillOptions().setTocDepth(1));
        assertEquals(2, result.getStats().getDepthFiltered(), "2.1 与 2.1.1 深度>1 被过滤");
        assertEquals(4, result.getStats().getTocEntries());
    }

    // ========== 过滤器 ==========

    @Test
    public void testFigureExampleAndPseudoPageFilters() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("Figure 2-1 Plot .......... 3\n")
                .append("Table 3-4 Data .......... 3\n")
                .append("filename: sample_response.csv .......... 3\n")
                .append("Front Matter V17\n")
                .append("Chapter 1 Real .......... 3\n")
                .append("Chapter 2 Real2 .......... 3\n")
                .append("Chapter 3 Real3 .......... 3\n");
        sb.append(anchor(3)).append('\n')
                .append("Chapter 1 Real\nChapter 2 Real2\nChapter 3 Real3\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(2, result.getStats().getIndexSkipped(), "Figure/Table entries skipped");
        assertEquals(1, result.getStats().getExampleSkipped(), "filename example skipped");
        // V17（字母前缀伪页码）在抽取期被 NaN 守卫丢弃：tocEntries 不含它
        assertEquals(3, result.getStats().getTocEntries());
        assertEquals(3, result.getStats().getInserted());
    }

    // ========== 逻辑页单调扫描 ==========

    @Test
    public void testLogicalPageScan() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Cover\n");
        sb.append(anchor(2)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 1-1\n")
                .append("Chapter 2 Setup .......... 1-5\n")
                .append("Chapter 3 Notes .......... 2-2\n");
        sb.append(anchor(3)).append('\n').append("章节前言\n")
                .append("Chapter 1 Intro\nbody\n");
        sb.append(anchor(4)).append('\n').append("Chapter 2 Setup\nbody\n");
        sb.append(anchor(5)).append('\n').append("Chapter 3 Notes\nbody\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertTrue(result.getStats().getByScan() >= 3, "logical entries must scan: " +
                result.getStats().getByScan());
        String out = result.getMd();
        assertTrue(out.contains("# Chapter 1 Intro") && out.contains("# Chapter 2 Setup")
                && out.contains("# Chapter 3 Notes"), out);
    }

    // ========== duplicateSkipped 与 usedLines 冲突 ==========

    @Test
    public void testDuplicateEntriesSkipped() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 3\n")
                .append("Chapter 1 Intro .......... 3\n")
                .append("Chapter 2 Setup .......... 3\n")
                .append("Chapter 3 Notes .......... 3\n");
        sb.append(anchor(3)).append('\n')
                .append("Chapter 1 Intro\nChapter 2 Setup\nChapter 3 Notes\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(1, result.getStats().getDuplicateSkipped(), "same key→same line: 幂等跳过");
        assertEquals(3, result.getStats().getInserted());
    }

    // ========== SOURCE_HEADING_LIKE 守卫 ==========

    @Test
    public void testSourceHeadingLikeGuard() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("Chapter 1 Shell Notes .......... 3\n")
                .append("Chapter 2 Shell Notes .......... 3\n")
                .append("Chapter 3 Shell Notes .......... 3\n");
        sb.append(anchor(3)).append('\n')
                .append("# shell comment looks like heading\n")
                .append("more text\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        // 守卫语义：跳过 source-heading-like 行后插入第一条正文之上（不是 unmatched）
        assertEquals(3, result.getStats().getByPageTop());
        assertEquals(0, result.getStats().getUnmatched());
        assertFalse(result.getMd().contains("## shell comment"),
                "must not create heading shells over source-heading-like lines");
        assertTrue(result.getMd().contains("# Chapter 1 Shell Notes"), result.getMd());
    }

    // ========== BODY_H1 pass ==========

    @Test
    public void testBodyH1Pass() {
        // body-h1 pass 位于 TOC gate 之后（headingize :155 先于 :351）——fixture 须含合法目录页
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 2\n")
                .append("Chapter 2 Setup .......... 2\n")
                .append("Chapter 3 Notes .......... 2\n");
        sb.append(anchor(2)).append('\n')
                .append("Chapter 1 Intro\nChapter 2 Setup\nChapter 3 Notes\n")
                .append("Chapter 4\nAppendix A\nplain text\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(2, result.getStats().getBodyH1());
        assertTrue(result.getMd().contains("# Chapter 4\n"), result.getMd());
        assertTrue(result.getMd().contains("# Appendix A\n"));
    }

    // ========== forceScan ==========

    @Test
    public void testForceScanUsesMonotonicScan() {
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Cover\n");
        sb.append(anchor(2)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 9-9\n")
                .append("Chapter 2 Setup .......... 9-9\n")
                .append("Chapter 3 Notes .......... 9-9\n");
        sb.append(anchor(3)).append('\n').append("Chapter 1 Intro\nbody\n");
        sb.append(anchor(4)).append('\n').append("Chapter 2 Setup\nbody\n");
        sb.append(anchor(5)).append('\n').append("Chapter 3 Notes\nbody\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md,
                new BackfillOptions().setForceScan(true));
        assertTrue(result.getStats().getByScan() >= 3, "forceScan must use monotonic scan");
        assertEquals(0, result.getStats().getByPageTop(), "forceScan never falls back to page-top");
    }

    // ========== 相邻页 ==========

    @Test
    public void testAdjacentPageMatch() {
        // TOC 页须在页数前 20%（6 页 → idx≤1.2）；标题实际在下一页（opener 在页底的情形）
        StringBuilder sb = new StringBuilder();
        sb.append(anchor(1)).append('\n').append("Cover\n");
        sb.append(anchor(2)).append('\n').append("Contents\n")
                .append("Chapter 1 Intro .......... 3\n")
                .append("Chapter 2 Setup .......... 4\n")
                .append("Chapter 3 Notes .......... 5\n");
        sb.append(anchor(3)).append('\n').append("p3\n");
        sb.append(anchor(4)).append('\n').append("Chapter 1 Intro\n");
        sb.append(anchor(5)).append('\n').append("Chapter 2 Setup\n");
        sb.append(anchor(6)).append('\n').append("Chapter 3 Notes\n");
        String md = sb.toString();

        TocBackfiller.Result result = TocBackfiller.backfill(md, new BackfillOptions());
        assertEquals(3, result.getStats().getByAdjacent(),
                "adjacent page hits must be counted: " + result.getStats().getByAdjacent());
    }
}
