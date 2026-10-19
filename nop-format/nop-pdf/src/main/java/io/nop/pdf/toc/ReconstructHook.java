package io.nop.pdf.toc;

import io.nop.pdf.outline.PdfOutlineEntry;

import java.util.List;
import java.util.Map;

/**
 * WI15：AI 重建钩子（使用方注入；nop-pdf 只提供确定性校验）。
 *
 * <p>输入：front-pages 文本（前 10 页、每页 1500 字上限，s0 :84-95 语义），
 * key 为 1-based 页号。输出：重建的 outline 条目（title/depth/page），由
 * StructureSourceRouter 做确定性校验（title≥3/depth clamp 1..6/page clamp/
 * 排序/去重——s0 validateReconstructedEntries :69-82 直译）；校验通过但 0 条目
 * = 诚实 0 条目（非失败，R2-Minor-2b）；产物不补 pageEnd（v2 不消费）。
 */
public interface ReconstructHook {
    List<PdfOutlineEntry> reconstruct(int totalPages, Map<Integer, String> frontPageTexts);
}
