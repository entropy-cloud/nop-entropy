package io.nop.pdf.toc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WI15：轻量 sorted 页文本 API——PDFTextStripper setSortByPosition(true)，
 * 保留空白 run（多空格/点导符字形不坍缩——headingize v1 的 `≥2 空格` leader
 * 依赖此形态）。
 *
 * <p>已知限制（plan 2484 R2-Major-2，实证）：纯定位间隙（newLineAtOffset 拉开）
 * 会被 PDFTextStripper 坍缩为单空格——此类目录行在 toc-regex 路径系统性漏检；
 * WI16（-layout 等价 owner）落地后对版。
 */
public final class SortedPageTexts {

    private SortedPageTexts() {
    }

    /**
     * 逐页提取（1-based 页号 → 文本，保留原始行结构与空白 run）
     */
    public static Map<Integer, String> extract(PDDocument doc) {
        Map<Integer, String> ret = new LinkedHashMap<>();
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        for (int p = 1, n = doc.getNumberOfPages(); p <= n; p++) {
            stripper.setStartPage(p);
            stripper.setEndPage(p);
            try {
                ret.put(p, stripper.getText(doc));
            } catch (IOException e) {
                ret.put(p, "");
            }
        }
        return ret;
    }
}
