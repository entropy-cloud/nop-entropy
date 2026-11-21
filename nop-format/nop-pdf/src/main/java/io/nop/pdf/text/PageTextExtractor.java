package io.nop.pdf.text;

import io.nop.api.core.exceptions.NopException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static io.nop.pdf.core.PdfErrors.ARG_PAGE;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_GARBLED_TEXT;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_PAGE_EXTRACT_IO;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_SCANNED_PAGE;

/**
 * WI16：版面感知页文本抽取——strip/layout 双模式 + 页锚 + 扫描/乱码检测。
 *
 * <p>strip 模式（pdfio pageText :67-92 直译）：控制字符剥离、`[ \t]+`→单空格、
 * trim、maxChars。layout 模式：定位间隙重建多空格（**保留 run，不做空白归一**——
 * R1-M1；headingize v1 leader 与 WI15 follow-up 依赖此形态）。
 * 行分隔显式 "\\n"（R1-MN1 平台一致性）。锚 `<a id="pdf-page-N"></a>` 独立成行前置。
 *
 * <p>扫描判定（R1-M3 定版）：页 trim 后字符数==0 且 hasContents() → 硬错误；
 * "图片占面"不入谓词（移交 WI17 普查）；vector-only 页同样硬错。
 * 乱码双路：unmapped-glyph 事件计数（主信号）+ U+FFFD 字面兜底。
 */
public final class PageTextExtractor {

    /** pdfio stripControlChars 集合（\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F） */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t]+");

    private PageTextExtractor() {
    }

    public static PageTextExtractResult extract(PDDocument doc, PageTextOptions options) {
        PageTextOptions opts = options == null ? new PageTextOptions() : options;
        List<PageTextResult> pages = new ArrayList<>();
        int scanned = 0;
        int nPages = doc.getNumberOfPages();
        for (int p = 1; p <= nPages; p++) {
            PDPage page = doc.getPage(p - 1);
            Extraction extraction = extractPage(doc, p, opts);
            // plan 2586：IO 故障与扫描判定解耦——抽取 IO 失败 fail-loud（带页码），不误标为扫描页
            if (extraction.ioFailed()) {
                throw new NopException(ERR_PDF_PAGE_EXTRACT_IO)
                        .param(ARG_PAGE, p).param("cause", extraction.error());
            }
            // charCount 基于锚前原始文本（锚拼接后恒>0，会导致扫描判定失效——执行偏差）
            int rawCharCount = extraction.text() == null ? 0 : extraction.text().trim().length();
            String text = anchorPage(p, extraction.text(), opts.getMode());

            PageTextResult result = new PageTextResult(p, text, rawCharCount, extraction.fffdCount(), extraction.unmappedCount());
            if (rawCharCount == 0) {
                if (opts.getScanPolicy() == PageTextOptions.ScanPolicy.SKIP) {
                    result.setScanned(true);
                    scanned++;
                } else {
                    throw new NopException(ERR_PDF_SCANNED_PAGE).param(ARG_PAGE, p);
                }
            }
            if (opts.getFffdPolicy() != PageTextOptions.FffdPolicy.OFF
                    && extraction.fffdCount() > opts.getFffdThreshold()) {
                if (opts.getFffdPolicy() == PageTextOptions.FffdPolicy.ERROR)
                    throw new NopException(ERR_PDF_GARBLED_TEXT)
                            .param(ARG_PAGE, p)
                            .param("fffdCount", extraction.fffdCount());
                // WARN policy：留 stats，由调用方按 getFffdCount 观测
            }
            pages.add(result);
        }
        return new PageTextExtractResult(pages, scanned);
    }

    private static String anchorPage(int pageNo, String text, PageTextOptions.Mode mode) {
        String anchor = "<a id=\"pdf-page-" + pageNo + "\"></a>";
        if (text == null || text.isEmpty())
            return anchor + "\n";
        // 锚独立成行前置（R1-MN2：保 WI15 行级回填语义）
        return anchor + "\n" + text + (text.endsWith("\n") ? "" : "\n");
    }

    private static Extraction extractPage(PDDocument doc, int pageNo, PageTextOptions opts) {
        try {
            if (opts.getMode() == PageTextOptions.Mode.LAYOUT) {
                LayoutTextStripper stripper = new LayoutTextStripper();
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String raw = stripper.getText(doc);
                String text = opts.getMaxChars() != Integer.MAX_VALUE
                        && raw.length() > opts.getMaxChars()
                        ? raw.substring(0, opts.getMaxChars()) : raw;
                int fffd = 0;
                for (int i = 0; i < raw.length(); i++)
                    if (raw.charAt(i) == '\uFFFD')
                        fffd++;
                return new Extraction(text, fffd, stripper.getUnmappedCount(), false, null);
            }
            StripTextStripper stripper = new StripTextStripper();
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            String raw = stripper.getText(doc);
            // pdfio 直译：控制字符剥离 → [ \t]+ 单空格 → trim → maxChars
            String text = stripControlChars(raw);
            text = WHITESPACE_RUN.matcher(text).replaceAll(" ").trim();
            if (text.length() > opts.getMaxChars())
                text = text.substring(0, opts.getMaxChars());
            return new Extraction(text, stripper.getFffdCount(), stripper.getUnmappedCount(), false, null);
        } catch (IOException e) {
            // plan 2586：IO 故障不再吞成空串（会被误判为扫描页），标记 ioFailed 由调用方 fail-loud
            return new Extraction("", 0, 0, true, String.valueOf(e));
        }
    }

    private static String stripControlChars(String text) {
        return CONTROL_CHARS.matcher(text).replaceAll("");
    }

    private record Extraction(String text, int fffdCount, int unmappedCount, boolean ioFailed, String error) {
    }
}
