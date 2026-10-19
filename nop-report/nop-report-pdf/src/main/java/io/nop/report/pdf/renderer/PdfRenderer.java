package io.nop.report.pdf.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelStyle;
import io.nop.report.pdf.ReportPdfConfigs;
import io.nop.report.pdf.utils.HeaderFooterEvaluator;
import io.nop.report.pdf.utils.HeaderFooterRuns;
import io.nop.report.pdf.utils.PdfImageHelper;
import io.nop.report.pdf.utils.PdfStyleHelper;
import io.nop.report.pdf.ReportPdfErrors;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.report.pdf.ReportPdfErrors.ARG_FAILED_CHAR;
import static io.nop.report.pdf.ReportPdfErrors.ARG_FONT_NAME;
import static io.nop.report.pdf.ReportPdfErrors.ARG_SCOPE;
import static io.nop.report.pdf.ReportPdfErrors.ARG_TEXT_SAMPLE;
import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_INVALID_PAGE_NUMBER_SCOPE;

public class PdfRenderer {
    static final Logger LOG = LoggerFactory.getLogger(PdfRenderer.class);

    static final String KEY_DEFAULT_FONT = "@default";
    static final String KEY_FALLBACK_FONT = "@fallback";

    private final PDDocument document;
    private final Map<String, PDFont> fontCache = new HashMap<>();

    // WI10：字体解析结果缓存（font + 合成标志），避免逐单元格重复解析/加载字体
    private final Map<String, FontManager.FontResolution> fontResolutionCache = new HashMap<>();
    private final Map<ByteString, PDImageXObject> imageCache = new HashMap<>();
    private final List<PDPage> pages = new ArrayList<>();
    private final Set<Integer> pagesWithFooter = new HashSet<>();

    // WI4：延迟页眉页脚（含 &-占位符的 HF 需全部页渲染完成后求值绘制，&N 才可得）
    private final List<DeferredHeaderFooter> deferredHeaderFooters = new ArrayList<>();
    // WI4：每个 sheet 的页区间 [startInclusive, endInclusive]（scope=sheet 时 &P/&N 用）
    private final List<int[]> sheetPageRanges = new ArrayList<>();
    private String sourcePath;

    public PdfRenderer(PDDocument document) {
        this.document = document;
    }

    public PDDocument getDocument() {
        return document;
    }

    public PDFont getFont(ExcelFont font) {
        return resolveFont(font).getFont();
    }

    /**
     * WI10：解析字体 + 合成标志（结果按文档缓存，与 {@link #getFont(String, boolean, boolean)} 同键）。
     */
    public FontManager.FontResolution resolveFont(ExcelFont font) {
        if (font == null)
            return new FontManager.FontResolution(getDefaultFont(), false, false);
        return resolveFont(font.getFontName(), font.isBold(), font.isItalic());
    }

    /**
     * WI10：解析字体 + 合成标志，结果按 fullName 键缓存——避免逐单元格重复加载 TTF。
     */
    public FontManager.FontResolution resolveFont(String fontName, boolean bold, boolean italic) {
        if (fontName == null)
            return new FontManager.FontResolution(getDefaultFont(), false, false);

        String fontKey = FontManager.instance().getFontFullName(fontName, bold, italic);
        FontManager.FontResolution res = fontResolutionCache.get(fontKey);
        if (res == null) {
            res = FontManager.instance().resolveFont(fontName, bold, italic, document);
            fontResolutionCache.put(fontKey, res);
        }
        return res;
    }

    /**
     * WI10：字形回退增强版——回退切换字体后 synthetic 标志按请求维度重置
     * （回退字体无 bold/italic 变体，被请求的维度必然未被满足）。
     */
    public FontManager.FontResolution fontForText(String text, FontManager.FontResolution res,
                                                  boolean bold, boolean italic) {
        PDFont font = fontForText(text, res.getFont());
        if (font == res.getFont())
            return res;
        return new FontManager.FontResolution(font, bold, italic);
    }

    public PDFont getFont(String fontName, boolean bold, boolean italic) {
        return resolveFont(fontName, bold, italic).getFont();
    }

    /**
     * 默认字体：VFS提供/fonts/default.ttf时优先加载（通常为CJK字体），
     * 否则回退base-14的Helvetica
     */
    public PDFont getDefaultFont() {
        PDFont font = fontCache.get(KEY_DEFAULT_FONT);
        if (font == null) {
            font = FontManager.instance().loadDefaultFont(document);
            fontCache.put(KEY_DEFAULT_FONT, font);
        }
        return font;
    }

    /**
     * 字形回退：当前字体无法编码文本中的字符（如base-14字体遇到中文）时
     * 切换到CJK回退字体；所有字体都无法编码时抛出带配置指引的错误
     */
    public PDFont fontForText(String text, PDFont font) {
        if (FontManager.instance().canEncode(font, text))
            return font;

        PDFont fallback = getFallbackFont();
        if (fallback != null && FontManager.instance().canEncode(fallback, text)) {
            LOG.debug("nop.pdf.font-glyph-fallback:font={},fallback={}",
                    font == null ? null : font.getName(), fallback.getName());
            return fallback;
        }

        Integer failed = FontManager.instance().getLastFailedCodePoint();
        // 无回退字体时优先抛出"缺回退字体"（指引配置字体），仅在已有回退字体
        // 但该字符仍无法编码时才抛"缺字形"
        if (fallback == null && FontManager.instance().hasDefaultFontResource() == false
                && FontManager.instance().discoveredFontFiles().isEmpty()) {
            throw new NopException(ReportPdfErrors.ERR_PDF_FALLBACK_FONT_NOT_FOUND)
                    .param(ARG_TEXT_SAMPLE, StringHelper.limitLen(text, 20));
        }

        throw new NopException(ReportPdfErrors.ERR_PDF_FONT_MISSING_GLYPH)
                .param(ARG_FONT_NAME, font == null ? null : font.getName())
                .param(ARG_TEXT_SAMPLE, StringHelper.limitLen(text, 20))
                .param(ARG_FAILED_CHAR, failed == null ? "?"
                        : "U+" + Integer.toHexString(failed).toUpperCase());
    }

    /**
     * CJK回退字体（解析结果按文档缓存）
     */
    public PDFont getFallbackFont() {
        PDFont font = fontCache.get(KEY_FALLBACK_FONT);
        if (font == null) {
            font = FontManager.instance().loadFallbackFont(document);
            if (font != null)
                fontCache.put(KEY_FALLBACK_FONT, font);
        }
        return font;
    }

    public PdfPageRenderer addPage(PDRectangle pageSize) throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        pages.add(page);
        return new PdfPageRenderer(document, page);
    }

    /**
     * 模板来源路径（&F/&Z 求值来源），null 表示无（求值为空串）
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * WI4：标记一个 sheet 的页区间开始（PdfReportRenderer 每个 sheet 渲染前后配对调用）
     */
    public void markSheetRangeStart() {
        sheetPageRanges.add(new int[]{pages.size(), -1});
    }

    public void markSheetRangeEnd() {
        if (!sheetPageRanges.isEmpty()) {
            int[] range = sheetPageRanges.get(sheetPageRanges.size() - 1);
            range[1] = pages.size() - 1;
        }
    }

    /**
     * 当前文档页的 0-based 序号（最后一页）
     */
    public int currentPageIndex() {
        return pages.size() - 1;
    }

    /**
     * 当前页在所属 sheet 内的 0-based 序号（scope=sheet 时 &P 用）
     */
    public int currentSheetPageIndex() {
        int pageIndex = pages.size() - 1;
        if (sheetPageRanges.isEmpty())
            return pageIndex;
        int[] range = sheetPageRanges.get(sheetPageRanges.size() - 1);
        return pageIndex - range[0];
    }

    /**
     * sheet 页数（scope=sheet 时 &N 用）；无区间记录时回退全文档页数
     */
    public int currentSheetPageCount() {
        if (sheetPageRanges.isEmpty())
            return pages.size();
        int[] range = sheetPageRanges.get(sheetPageRanges.size() - 1);
        return range[1] - range[0] + 1;
    }

    public void addDeferredHeaderFooter(DeferredHeaderFooter task) {
        deferredHeaderFooters.add(task);
    }

    /**
     * 模板已显式配置页脚的页面不再叠加默认页码
     */
    public void markFooterDrawn() {
        if (!pages.isEmpty())
            pagesWithFooter.add(pages.size() - 1);
    }

    /**
     * 延迟页眉页脚任务（WI4）：含 &-代码的 HF 段在全部页渲染完成后求值绘制（&N 才可得）。
     */
    public static final class DeferredHeaderFooter {
        private final int pageIndex;
        private final String position;
        private final String rawText;
        private final PDRectangle rect;
        private final ExcelStyle style;
        private final String sheetName;
        // WI5：&G 图片引用（hf.imageName）与来源 sheet 图片快照
        private final String imageName;
        private final List<ExcelImage> sheetImages;

        public DeferredHeaderFooter(int pageIndex, String position, String rawText,
                                    PDRectangle rect, ExcelStyle style, String sheetName,
                                    String imageName, List<ExcelImage> sheetImages) {
            this.pageIndex = pageIndex;
            this.position = position;
            this.rawText = rawText;
            this.rect = rect;
            this.style = style;
            this.sheetName = sheetName;
            this.imageName = imageName;
            this.sheetImages = sheetImages;
        }
    }

    /**
     * 统一求值并绘制延迟页眉页脚；在 drawDefaultPageNumbers 之前调用（所有页已渲染，&N 可得）。
     */
    public void drawDeferredHeaderFooters() throws IOException {
        if (deferredHeaderFooters.isEmpty())
            return;

        String scope = ReportPdfConfigs.CFG_PDF_PAGE_NUMBER_SCOPE.get();
        if (!"global".equals(scope) && !"sheet".equals(scope))
            throw new NopException(ERR_PDF_INVALID_PAGE_NUMBER_SCOPE).param(ARG_SCOPE, scope);

        for (DeferredHeaderFooter task : deferredHeaderFooters) {
            int page;
            int total;
            if ("sheet".equals(scope)) {
                int[] range = findSheetRange(task.pageIndex);
                page = task.pageIndex - range[0] + 1;
                total = range[1] - range[0] + 1;
            } else {
                page = task.pageIndex + 1;
                total = pages.size();
            }
            String sourcePath = getSourcePath();
            String fileName = sourcePath == null ? "" : fileNameOf(sourcePath);
            HeaderFooterEvaluator.Context ctx = new HeaderFooterEvaluator.Context(
                    page, total, task.sheetName, fileName, sourcePath == null ? "" : sourcePath);

            List<HeaderFooterRuns.Run> runs = HeaderFooterRuns.split(task.rawText);
            // 先求值并计算总宽（L/C/R 定位需要）
            int n = runs.size();
            String[] texts = new String[n];
            PDFont[] fonts = new PDFont[n];
            FontManager.FontResolution[] fontRes = new FontManager.FontResolution[n];
            float[] sizes = new float[n];
            float[] widths = new float[n];
            float totalWidth = 0;
            for (int i = 0; i < n; i++) {
                HeaderFooterRuns.Run run = runs.get(i);
                texts[i] = HeaderFooterEvaluator.evaluate(run.getText(), ctx);
                ExcelFont styleFont = task.style != null ? task.style.getFont() : null;
                if (run.getFontName() != null) {
                    fontRes[i] = resolveFont(run.getFontName(), styleFont != null && styleFont.isBold(),
                            styleFont != null && styleFont.isItalic());
                } else {
                    fontRes[i] = resolveFont(styleFont);
                }
                fonts[i] = fontRes[i].getFont();
                sizes[i] = run.getFontSize() > 0 ? run.getFontSize()
                        : PdfStyleHelper.getFontSize(styleFont);
                texts[i] = sanitizeForFont(fonts[i], texts[i]);
                widths[i] = fonts[i].getStringWidth(texts[i]) / 1000 * sizes[i];
                totalWidth += widths[i];
            }

            float rectWidth = task.rect.getWidth();
            float x = task.rect.getLowerLeftX();
            if ("C".equals(task.position))
                x += (rectWidth - totalWidth) / 2;
            else if ("R".equals(task.position))
                x += rectWidth - totalWidth;

            try (PDPageContentStream cs = getContentStreamFor(task.pageIndex)) {
                // WI5：run 序列裁剪到分区矩形（越界不越界绘制到相邻分区）
                cs.saveGraphicsState();
                cs.addRect(task.rect.getLowerLeftX(), task.rect.getLowerLeftY(),
                        task.rect.getWidth(), task.rect.getHeight());
                cs.clip();
                for (int i = 0; i < n; i++) {
                    if (texts[i].isEmpty())
                        continue;
                    PDRectangle runRect = new PDRectangle(x, task.rect.getLowerLeftY(),
                            Math.max(widths[i], 1), task.rect.getHeight());
                    PdfStyleHelper.drawUnwrappedText(cs, texts[i], fonts[i],
                            fontRes[i].isSyntheticBold(), fontRes[i].isSyntheticItalic(),
                            sizes[i], runRect, task.style);
                    x += widths[i];
                }
                cs.restoreGraphicsState();

                // WI5：&G 图片（logo）——分区文本绘制后叠加渲染
                if (task.rawText.indexOf('G') >= 0 && hasGCode(task.rawText)) {
                    ExcelImage logo = findLogo(task.imageName, task.sheetImages);
                    if (logo == null) {
                        LOG.warn("nop.report.pdf.header-image-missing:imageName={},sheet={}",
                                task.imageName, task.sheetName);
                    } else {
                        PDImageXObject pdImage = getImage(logo.getData());
                        double imgH = pdImage.getHeight();
                        double imgW = pdImage.getWidth();
                        double fit = Math.min(task.rect.getHeight() / imgH, task.rect.getWidth() / imgW);
                        double drawH = imgH * fit;
                        double drawW = imgW * fit;
                        double ix = task.rect.getLowerLeftX() + (task.rect.getWidth() - drawW) / 2;
                        double iy = task.rect.getLowerLeftY() + (task.rect.getHeight() - drawH) / 2;
                        PdfImageHelper.drawImage(this, cs, logo, ix, iy, drawW, drawH, null);
                    }
                }
            }
        }
        deferredHeaderFooters.clear();
    }

    private static boolean hasGCode(String rawText) {
        for (int i = 0; i < rawText.length() - 1; i++) {
            if (rawText.charAt(i) == '&') {
                char code = rawText.charAt(i + 1);
                if (code == 'G')
                    return true;
                if (code == '&')
                    i++;
            }
        }
        return false;
    }

    private static ExcelImage findLogo(String imageName, List<ExcelImage> sheetImages) {
        if (sheetImages == null || sheetImages.isEmpty())
            return null;
        if (StringHelper.isEmpty(imageName))
            return sheetImages.get(0);
        for (ExcelImage image : sheetImages) {
            if (imageName.equals(image.getName()))
                return image;
        }
        return null;
    }

    private int[] findSheetRange(int pageIndex) {
        for (int[] range : sheetPageRanges) {
            if (pageIndex >= range[0] && pageIndex <= range[1])
                return range;
        }
        return new int[]{0, Math.max(0, pages.size() - 1)};
    }

    private PDPageContentStream getContentStreamFor(int pageIndex) throws IOException {
        PDPage page = pages.get(pageIndex);
        return new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    private static String fileNameOf(String path) {
        int pos = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return pos >= 0 ? path.substring(pos + 1) : path;
    }

    private String sanitizeForFont(PDFont font, String text) {
        try {
            font.encode(text);
            return text;
        } catch (Exception e) {
            return PdfStyleHelper.sanitizeTextForFont(text, font);
        }
    }

    /**
     * 默认页码（F13）：多页文档且页面未配置页脚时，在页底居中绘制"第 x / y 页"。
     * 在所有内容渲染完成后、save之前调用
     */
    public void drawDefaultPageNumbers() throws IOException {
        if (!Boolean.TRUE.equals(ReportPdfConfigs.CFG_PDF_DEFAULT_PAGE_FOOTER.get()))
            return;

        int total = pages.size();
        if (total <= 1)
            return;

        for (int i = 0; i < total; i++) {
            if (pagesWithFooter.contains(i))
                continue;
            PDPage page = pages.get(i);
            // 无CJK回退字体的环境（纯ASCII部署）降级为英文页码，避免默认页码导致整份导出失败
            String text = "第 " + (i + 1) + " / " + total + " 页";
            PDFont font;
            try {
                font = fontForText(text, getDefaultFont());
            } catch (NopException e) {
                LOG.warn("nop.pdf.page-number-ascii-fallback:无CJK回退字体，页码降级为英文");
                text = "Page " + (i + 1) + " / " + total;
                font = fontForText(text, getDefaultFont());
            }
            float fontSize = 9f;
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;

            try (PDPageContentStream cs = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setFont(font, fontSize);
                cs.beginText();
                cs.newLineAtOffset((page.getMediaBox().getWidth() - textWidth) / 2, 15f);
                cs.showText(text);
                cs.endText();
            }
        }
    }

    public void saveToStream(OutputStream outputStream) throws IOException {
        document.save(outputStream);
    }

    public PDImageXObject getImage(ByteString data) throws IOException {
        PDImageXObject image = imageCache.get(data);
        if (image != null)
            return image;

        image = PDImageXObject.createFromByteArray(document, data.toByteArray(), null);
        imageCache.put(data, image);
        return image;
    }
}
