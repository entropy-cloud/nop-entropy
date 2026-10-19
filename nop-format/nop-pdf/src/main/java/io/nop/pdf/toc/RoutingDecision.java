package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WI15：结构源路由决策（source + 条目 + corrections 审计轨迹 + crosscheck 告警）。
 *
 * <p>corrections 语义对齐 s0（守卫修正记录）；钩子产物不补 pageEnd（v2 不消费，
 * R2-Minor-2d）。
 */
public class RoutingDecision {
    /** outline | toc-regex | ai-reconstruct | chunk */
    private final String source;
    private final List<PdfOutlineEntry> entries;
    private final List<String> corrections;
    private final List<String> crosscheckWarnings;
    private final List<TocLine> tocLines;

    public RoutingDecision(String source, List<PdfOutlineEntry> entries, List<TocLine> tocLines,
                           List<String> corrections, List<String> crosscheckWarnings) {
        this.source = source;
        this.entries = entries;
        this.tocLines = tocLines;
        this.corrections = corrections;
        this.crosscheckWarnings = crosscheckWarnings;
    }

    public String getSource() {
        return source;
    }

    public List<PdfOutlineEntry> getEntries() {
        return entries;
    }

    /** toc-regex 路线时为目录页检出的条目（抽取留给回填，s0 语义：此列表仅供路由证据） */
    public List<TocLine> getTocLines() {
        return tocLines == null ? Collections.emptyList() : tocLines;
    }

    public List<String> getCorrections() {
        return corrections == null ? Collections.emptyList() : corrections;
    }

    public List<String> getCrosscheckWarnings() {
        return crosscheckWarnings == null ? Collections.emptyList() : crosscheckWarnings;
    }

    public static RoutingDecision copyOf(RoutingDecision d, String newSource, List<PdfOutlineEntry> newEntries,
                                         List<String> extraCorrections) {
        List<String> corrections = new ArrayList<>(d.getCorrections());
        corrections.addAll(extraCorrections);
        return new RoutingDecision(newSource, newEntries, d.tocLines, corrections, d.crosscheckWarnings);
    }
}
