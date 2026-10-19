package io.nop.pdf.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.pdf.tabula.ObjectExtractor;
import io.nop.pdf.tabula.Page;
import io.nop.pdf.tabula.Table;
import io.nop.pdf.tabula.TabulaTableHelper;
import io.nop.pdf.tabula.extractors.BasicExtractionAlgorithm;
import io.nop.pdf.tabula.extractors.SpreadsheetExtractionAlgorithm;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.pdf.core.PdfErrors.ARG_FAILED_CHAR;
import static io.nop.pdf.core.PdfErrors.ARG_PAGE;
import static io.nop.pdf.core.PdfErrors.ARG_PAGE_COUNT;
import static io.nop.pdf.core.PdfErrors.ARG_TEXT_SAMPLE;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_CJK_REQUIRES_FONT;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_IMAGE_INVALID;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_PAGE_OUT_OF_RANGE;
import static io.nop.pdf.core.PdfErrors.ERR_PDF_TEXT_NOT_ENCODABLE;

public class PdfDoc implements Closeable {
    // WI12：水印缺省参数定版（plan 2481 R1-M7）
    static final float WATERMARK_DEGREES = 45f;
    static final float WATERMARK_OPACITY = 0.25f;
    static final float WATERMARK_FONT_SIZE = 36f;
    static final float PAGE_NUMBER_Y = 20f;
    static final float HEADER_Y_FROM_TOP = 20f;
    static final float DEFAULT_STAMP_FONT_SIZE = 12f;

    private final PDDocument doc;

    public PdfDoc() {
        this.doc = new PDDocument();
    }

    public PdfDoc(PDDocument doc) {
        this.doc = Guard.notNull(doc, "doc");
    }

    public static PdfDoc loadFromFile(File file) {
        try {
            return new PdfDoc(Loader.loadPDF(file));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * WI13：加载密码保护的 PDF（错密码 InvalidPasswordException → NopException.adapt，
     * 不新增错误码——plan 2482 R1-Major-3 裁定：密码错误是环境输入错误而非契约违约）。
     */
    public static PdfDoc loadFromFile(File file, String password) {
        try {
            return new PdfDoc(Loader.loadPDF(file, password));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    // ========== WI13：文档元数据与保护 ==========

    /**
     * 读取 Document Information Dictionary（字段缺失为 null）
     */
    public PdfDocInfo getInfo() {
        PDDocumentInformation info = documentInformation();
        PdfDocInfo ret = new PdfDocInfo();
        ret.setTitle(info.getTitle());
        ret.setAuthor(info.getAuthor());
        ret.setSubject(info.getSubject());
        ret.setKeywords(info.getKeywords());
        ret.setCreator(info.getCreator());
        ret.setProducer(info.getProducer());
        return ret;
    }

    /**
     * 写入 Document Information Dictionary（null 字段不覆盖）
     */
    public void setInfo(PdfDocInfo info) {
        Guard.notNull(info, "info");
        PDDocumentInformation di = documentInformation();
        if (info.getTitle() != null)
            di.setTitle(info.getTitle());
        if (info.getAuthor() != null)
            di.setAuthor(info.getAuthor());
        if (info.getSubject() != null)
            di.setSubject(info.getSubject());
        if (info.getKeywords() != null)
            di.setKeywords(info.getKeywords());
        if (info.getCreator() != null)
            di.setCreator(info.getCreator());
        if (info.getProducer() != null)
            di.setProducer(info.getProducer());
    }

    private PDDocumentInformation documentInformation() {
        return doc.getDocumentInformation();
    }

    /**
     * WI13：加密文档（须在 save 之前调用；加密只影响写出，不影响内存操作）。
     * 算法定版 AES-256（setEncryptionKeyLength(256) + setPreferAES(true)——3.0.8 缺省
     * RC4-40 弱加密，plan 2482 R1-Major-2）。ownerPassword 拥有全部权限；userPassword
     * 受 accessPermission 限制（空 userPassword=合法的无密码受限打开；空 ownerPassword
     * 不安全，由调用方自担）。
     */
    public void encrypt(String ownerPassword, String userPassword,
                        boolean allowPrinting, boolean allowCopying) {
        AccessPermission permission = new AccessPermission();
        permission.setCanPrint(allowPrinting);
        permission.setCanExtractContent(allowCopying);
        StandardProtectionPolicy policy = new StandardProtectionPolicy(ownerPassword, userPassword, permission);
        policy.setEncryptionKeyLength(256);
        policy.setPreferAES(true);
        try {
            doc.protect(policy);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 文档是否已加密（仅反映加载态：load 加密文档为 true；对新建文档 encrypt 后
     * 仍为 false——protect 只在 save 时写出加密字典，加密态以 reload 后断言为准）
     */
    public boolean isEncrypted() {
        return doc.isEncrypted();
    }

    public int getNumberOfPages() {
        return doc.getNumberOfPages();
    }

    /**
     * WI12：底层 PDDocument 访问（测试/高级用途；装配 API 已覆盖常规需求）
     */
    public PDDocument getDocument() {
        return doc;
    }

    public String getPageText(int pageNum) {
        PDFTextStripper stripper = new PDFTextStripper();

        // 设置开始页和结束页（页码从1开始）
        stripper.setStartPage(pageNum);
        stripper.setEndPage(pageNum);

        try {
            return stripper.getText(doc);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public String getAllText() {
        return getAllText("====Page{pageNo}===", null, -1);
    }

    public String getAllText(String beginPattern, String endPattern, int maxSizePerPage) {
        int totalPages = getNumberOfPages();
        if (totalPages <= 0)
            return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= totalPages; i++) {
            int pageNo = i;
            String text = getPageText(i);
            if (beginPattern != null) {
                String beginText = StringHelper.renderTemplate(beginPattern, name -> {
                    if (name.equals("pageNo"))
                        return pageNo;
                    return "${" + name + "}";
                });
                sb.append(beginText);
                sb.append('\n');
            }
            if (maxSizePerPage > 0) {
                sb.append(StringHelper.limitLen(text, maxSizePerPage));
            } else {
                sb.append(text);
            }
            sb.append("\n\n");

            if (endPattern != null) {
                String endText = StringHelper.renderTemplate(endPattern, name -> {
                    if (name.equals("pageNo"))
                        return pageNo;
                    return "${" + name + "}";
                });
                sb.append(endText);
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public PdfDoc selectPages(List<Integer> pages) {
        int totalPages = getNumberOfPages();

        PDDocument ret = new PDDocument();

        for (int pageNum : pages) {
            if (pageNum >= 1 && pageNum <= totalPages) {
                // 注意：PDFBox 3.x中getPage方法可能返回的是不可变页面对象
                // 需要根据实际情况处理
                PDPage page = this.doc.getPage(pageNum - 1);
                ret.addPage(page);
            }
        }
        return new PdfDoc(ret);
    }

    public PdfDoc selectPageRange(int firstPage, int lastPage) {
        int totalPages = getNumberOfPages();

        PDDocument ret = new PDDocument();

        for (int pageNum = firstPage; pageNum <= lastPage; pageNum++) {
            if (pageNum >= 1 && pageNum <= totalPages) {
                // 注意：PDFBox 3.x中getPage方法可能返回的是不可变页面对象
                // 需要根据实际情况处理
                PDPage page = this.doc.getPage(pageNum - 1);
                ret.addPage(page);
            }
        }
        return new PdfDoc(ret);
    }

    public PdfDoc selectPage(int pageNum) {
        PDDocument ret = new PDDocument();
        if (pageNum >= 1 && pageNum <= getNumberOfPages()) {
            ret.addPage(this.doc.getPage(pageNum - 1));
        }
        return new PdfDoc(ret);
    }

    public void splitIntoPages(File dir) {
        dir.mkdirs();

        for (int i = 1, n = this.getNumberOfPages(); i <= n; i++) {
            File file = new File(dir, StringHelper.leftPad(i + "", 3, '0') + ".pdf");

            PdfDoc doc = selectPage(i);
            try {
                doc.save(file);
            } finally {
                IoHelper.safeClose(doc);
            }
        }
    }

    public List<BaseTable> extractTables(int pageNum) {
        Page page = new ObjectExtractor(doc).extract(pageNum );

        List<Table> tables = new BasicExtractionAlgorithm().extract(page);
        return tables.stream().map(TabulaTableHelper::toBaseTable).collect(Collectors.toList());
    }

    public String getAllTablesHtml() {
        StringBuilder sb = new StringBuilder();
        int totalPages = getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            String html = getTablesHtml(i);
            sb.append("<p>").append("Page").append(i).append("</p>\n");
            sb.append(html);
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getTablesHtml(int pageNum) {
        StringBuilder sb = new StringBuilder();
        for (BaseTable table : extractTables(pageNum)) {
            sb.append(table.toHtmlString());
            sb.append('\n');
        }
        return sb.toString();
    }

    // ========== WI12：生成/装配 API ==========

    /**
     * 合并多个 PDF 为一个（COS 深拷贝页面，源文档关闭后目标仍可保存/渲染）。
     * 部分失败首错即抛（携带文件名），已打开源文档与内部目标文档一并关闭。
     */
    public static PdfDoc merge(List<File> files) {
        Guard.notEmpty(files, "files");
        PdfDoc target = new PdfDoc();
        try {
            for (File file : files) {
                PdfDoc src = null;
                try {
                    src = PdfDoc.loadFromFile(file);
                    target.appendDocument(src.doc);
                } catch (Exception e) {
                    throw wrapWithFile(e, file);
                } finally {
                    IoHelper.safeClose(src);
                }
            }
            return target;
        } catch (RuntimeException ex) {
            IoHelper.safeClose(target);
            throw ex;
        }
    }

    /**
     * 追加另一文档的全部页面到本文档（appendDocument COS 深拷贝）。
     * 不接管 other 的生命周期（调用方负责 close）。
     */
    public void append(PdfDoc other) {
        Guard.notNull(other, "other");
        appendDocument(other.doc);
    }

    /**
     * 拆分导出增强：按页码子集导出到文件。先做范围校验（越界 fail-loud），
     * 再委托 selectPages（其现语义静默跳过越界，不可依赖）。
     */
    public void exportPages(List<Integer> pages1Based, File out) {
        Guard.notNull(pages1Based, "pages");
        int n = getNumberOfPages();
        for (Integer p : pages1Based) {
            if (p == null || p < 1 || p > n)
                throw new NopException(ERR_PDF_PAGE_OUT_OF_RANGE)
                        .param(ARG_PAGE, p).param(ARG_PAGE_COUNT, n);
        }
        PdfDoc selected = selectPages(pages1Based);
        try {
            selected.save(out);
        } finally {
            IoHelper.safeClose(selected);
        }
    }

    /**
     * 指定页指定位置盖章文本。缺省定位=水平居中、垂直页中心；缺省字号 12pt、黑色、不旋转不透明。
     * 字体：fontFile 非空时在目标文档上加载（禁止跨文档缓存）；否则 Helvetica——
     * 任一字符 encode 失败即 fail-loud（首失败字符按 UnicodeBlock 分类错误码）。
     */
    public void stampText(int pageIdx1Based, String text, PdfStampOptions opts) {
        checkPage(pageIdx1Based);
        Guard.notEmpty(text, "text");
        PDPage page = doc.getPage(pageIdx1Based - 1);
        PDFont font = resolveStampFont(opts, text);
        float fontSize = opts != null && opts.getFontSize() != null ? opts.getFontSize() : DEFAULT_STAMP_FONT_SIZE;
        float textWidth = textWidth(font, text, fontSize);
        PDRectangle box = page.getMediaBox();
        float x = opts != null && opts.getX() != null ? opts.getX() : (box.getWidth() - textWidth) / 2;
        float y = opts != null && opts.getY() != null ? opts.getY() : box.getHeight() / 2;
        float degrees = opts != null && opts.getDegrees() != null ? opts.getDegrees() : 0f;
        PDColor color = colorOf(opts == null ? null : opts.getFontColor());

        try (PDPageContentStream cs = appendStream(page)) {
            cs.saveGraphicsState();
            applyOpacity(cs, page, opts);
            cs.setFont(font, fontSize);
            cs.setNonStrokingColor(color);
            cs.beginText();
            if (degrees != 0) {
                double rad = Math.toRadians(degrees);
                cs.setTextMatrix(new Matrix((float) Math.cos(rad), (float) Math.sin(rad),
                        -(float) Math.sin(rad), (float) Math.cos(rad), x, y));
            } else {
                cs.newLineAtOffset(x, y);
            }
            cs.showText(text);
            cs.endText();
            cs.restoreGraphicsState();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 指定页指定位置盖章图片。宽高缺省=像素/72pt（1px=1pt@72dpi），单边缺省按比例缩放。
     */
    public void stampImage(int pageIdx1Based, byte[] image, PdfStampOptions opts) {
        checkPage(pageIdx1Based);
        Guard.notEmpty(image, "image");
        PDPage page = doc.getPage(pageIdx1Based - 1);
        PDImageXObject pdImage = decodeImage(image);
        PDRectangle box = page.getMediaBox();
        float w = opts != null && opts.getImageWidth() != null ? opts.getImageWidth() : pdImage.getWidth();
        float h = opts != null && opts.getImageHeight() != null ? opts.getImageHeight() : pdImage.getHeight();
        if (opts != null && opts.getImageWidth() != null && opts.getImageHeight() == null)
            h = w * pdImage.getHeight() / pdImage.getWidth();
        if (opts != null && opts.getImageHeight() != null && opts.getImageWidth() == null)
            w = h * pdImage.getWidth() / pdImage.getHeight();
        float x = opts != null && opts.getX() != null ? opts.getX() : (box.getWidth() - w) / 2;
        float y = opts != null && opts.getY() != null ? opts.getY() : (box.getHeight() - h) / 2;
        float degrees = opts != null && opts.getDegrees() != null ? opts.getDegrees() : 0f;

        try (PDPageContentStream cs = appendStream(page)) {
            cs.saveGraphicsState();
            applyOpacity(cs, page, opts);
            if (degrees != 0) {
                // 绕图片中心旋转（绕角点旋转会使旋转后的视觉中心偏移）
                cs.transform(Matrix.getTranslateInstance(x + w / 2, y + h / 2));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(degrees), 0, 0));
                cs.drawImage(pdImage, -w / 2, -h / 2, w, h);
            } else {
                cs.drawImage(pdImage, x, y, w, h);
            }
            cs.restoreGraphicsState();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 全页文字水印。缺省：45° 逆时针、opacity 0.25、页面居中、36pt。
     */
    public void watermarkText(String text, PdfStampOptions opts) {
        PdfStampOptions o = watermarkDefaults(opts);
        for (int i = 1, n = getNumberOfPages(); i <= n; i++) {
            stampText(i, text, o);
        }
    }

    /**
     * 全页图片水印。缺省：45° 逆时针、opacity 0.25、页面居中。
     */
    public void watermarkImage(byte[] image, PdfStampOptions opts) {
        PdfStampOptions o = watermarkDefaults(opts);
        for (int i = 1, n = getNumberOfPages(); i <= n; i++) {
            stampImage(i, image, o);
        }
    }

    /**
     * 追加页码：pattern 中 {page}/{pages} 占位符按当前页/总页数替换，页脚水平居中（y 缺省 20pt）。
     * 仅透传 opts 的 fontFile/fontColor/fontSize/opacity——y 与水平居中由本方法定版，
     * 调用方设置的 x/degrees 不生效（closure audit Minor-B 裁定）。
     */
    public void appendPageNumbers(String pattern, PdfStampOptions opts) {
        Guard.notEmpty(pattern, "pattern");
        int n = getNumberOfPages();
        for (int i = 1; i <= n; i++) {
            String label = pattern.replace("{page}", String.valueOf(i))
                    .replace("{pages}", String.valueOf(n));
            PdfStampOptions o = opts != null ? opts : new PdfStampOptions();
            stampText(i, label, new PdfStampOptions()
                    .setFontFile(o.getFontFile())
                    .setFontColor(o.getFontColor())
                    .setFontSize(o.getFontSize() != null ? o.getFontSize() : 10f)
                    .setOpacity(o.getOpacity())
                    .setY(o.getY() != null ? o.getY() : PAGE_NUMBER_Y));
        }
    }

    /**
     * 给已有 PDF 追加页眉/页脚字面文本（header 页顶 y=pageHeight-20，footer 页底 y=20，
     * 均水平居中）。字体规则同 stampText（CJK 等不可编码字符 fail-loud）。
     */
    public void appendHeaderFooter(String header, String footer, PdfStampOptions opts) {
        int n = getNumberOfPages();
        for (int i = 1; i <= n; i++) {
            PDRectangle box = doc.getPage(i - 1).getMediaBox();
            if (header != null && !header.isEmpty()) {
                stampText(i, header, hfOpts(opts, box.getHeight() - HEADER_Y_FROM_TOP));
            }
            if (footer != null && !footer.isEmpty()) {
                stampText(i, footer, hfOpts(opts, PAGE_NUMBER_Y));
            }
        }
    }

    private PdfStampOptions hfOpts(PdfStampOptions opts, float y) {
        PdfStampOptions o = opts != null ? opts : new PdfStampOptions();
        return new PdfStampOptions()
                .setFontFile(o.getFontFile())
                .setFontColor(o.getFontColor())
                .setFontSize(o.getFontSize() != null ? o.getFontSize() : 10f)
                .setOpacity(o.getOpacity())
                .setY(y);
    }

    /**
     * 图片（PNG/JPEG）→ 单页 PDF：页面尺寸=像素/72pt，图片满页铺放。
     */
    public static PdfDoc fromImage(byte[] image) {
        checkImage(image);
        return fromImages(java.util.Collections.singletonList(image));
    }

    /**
     * 多图片 → 多页 PDF（每图一页）。
     */
    public static PdfDoc fromImages(List<byte[]> images) {
        Guard.notEmpty(images, "images");
        PdfDoc target = new PdfDoc();
        try {
            for (byte[] img : images) {
                checkImage(img);
                PDImageXObject obj = target.decodeImage(img);
                PDPage page = new PDPage(new PDRectangle(obj.getWidth(), obj.getHeight()));
                target.doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(target.doc, page)) {
                    cs.drawImage(obj, 0, 0, obj.getWidth(), obj.getHeight());
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
            }
            return target;
        } catch (RuntimeException ex) {
            IoHelper.safeClose(target);
            throw ex;
        }
    }

    private static void checkImage(byte[] image) {
        if (image == null || image.length == 0)
            throw new NopException(ERR_PDF_IMAGE_INVALID);
    }

    private void appendDocument(PDDocument source) {
        try {
            new PDFMergerUtility().appendDocument(this.doc, source);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private static RuntimeException wrapWithFile(Exception e, File file) {
        if (e instanceof NopException ne)
            return ne.param("fileName", file.getName());
        RuntimeException rte = NopException.adapt(e);
        if (rte instanceof NopException ne)
            return ne.param("fileName", file.getName());
        return rte;
    }

    private void checkPage(int pageIdx1Based) {
        if (pageIdx1Based < 1 || pageIdx1Based > getNumberOfPages())
            throw new NopException(ERR_PDF_PAGE_OUT_OF_RANGE)
                    .param(ARG_PAGE, pageIdx1Based).param(ARG_PAGE_COUNT, getNumberOfPages());
    }

    private PDPageContentStream appendStream(PDPage page) throws IOException {
        return new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true, true);
    }

    private PDImageXObject decodeImage(byte[] image) {
        try {
            return PDImageXObject.createFromByteArray(doc, image, null);
        } catch (IOException e) {
            NopException ne = new NopException(ERR_PDF_IMAGE_INVALID);
            ne.initCause(e);
            throw ne;
        }
    }

    private PDFont resolveStampFont(PdfStampOptions opts, String text) {
        if (opts != null && opts.getFontFile() != null) {
            try {
                return PDType0Font.load(doc, opts.getFontFile());
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            try {
                helvetica.encode(new String(Character.toChars(cp)));
            } catch (IllegalArgumentException | IOException e) {
                String sample = StringHelper.limitLen(text, 20);
                if (isCjkCodePoint(cp))
                    throw new NopException(ERR_PDF_CJK_REQUIRES_FONT)
                            .param(ARG_FAILED_CHAR, "U+" + Integer.toHexString(cp).toUpperCase())
                            .param(ARG_TEXT_SAMPLE, sample);
                throw new NopException(ERR_PDF_TEXT_NOT_ENCODABLE)
                        .param(ARG_FAILED_CHAR, "U+" + Integer.toHexString(cp).toUpperCase())
                        .param(ARG_TEXT_SAMPLE, sample);
            }
            i += Character.charCount(cp);
        }
        return helvetica;
    }

    static boolean isCjkCodePoint(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    /**
     * 不透明度<1 时经 PDExtendedGraphicsState 注入（save 作用域内调用，restore 收口）。
     * 页面 Resources 为 null 时显式创建（3.0.8 构造器亦会自动建，此为防御双保险；
     * 硬性要求是不得静默跳过——plan 2481 R2-Minor-4）。
     */
    private void applyOpacity(PDPageContentStream cs, PDPage page, PdfStampOptions opts) throws IOException {
        float opacity = opts == null || opts.getOpacity() == null ? 1f : opts.getOpacity();
        if (opacity >= 1f)
            return;
        if (page.getResources() == null)
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(opacity);
        gs.setStrokingAlphaConstant(opacity);
        page.getResources().add(gs);
        cs.setGraphicsStateParameters(gs);
    }

    private PdfStampOptions watermarkDefaults(PdfStampOptions opts) {
        // 拷贝语义：不就地修改调用方 opts（closure audit Minor-A）
        PdfStampOptions o = opts == null ? new PdfStampOptions()
                : new PdfStampOptions()
                        .setX(opts.getX()).setY(opts.getY())
                        .setDegrees(opts.getDegrees())
                        .setOpacity(opts.getOpacity())
                        .setFontSize(opts.getFontSize())
                        .setFontColor(opts.getFontColor())
                        .setFontFile(opts.getFontFile())
                        .setImageWidth(opts.getImageWidth())
                        .setImageHeight(opts.getImageHeight());
        if (o.getDegrees() == null)
            o.setDegrees(WATERMARK_DEGREES);
        if (o.getOpacity() == null)
            o.setOpacity(WATERMARK_OPACITY);
        if (o.getFontSize() == null)
            o.setFontSize(WATERMARK_FONT_SIZE);
        return o;
    }

    private float textWidth(PDFont font, String text, float fontSize) {
        try {
            return font.getStringWidth(text) / 1000f * fontSize;
        } catch (IOException e) {
            return 0;
        }
    }

    static PDColor colorOf(String hex) {
        if (hex == null || hex.isEmpty())
            return new PDColor(new float[]{0, 0, 0}, PDDeviceRGB.INSTANCE);
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        Guard.checkArgument(s.length() == 6, "fontColor must be RRGGBB hex");
        int rgb = Integer.parseInt(s, 16);
        return new PDColor(new float[]{((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f}, PDDeviceRGB.INSTANCE);
    }

    public void save(File file) {
        try {
            doc.save(file);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * WI12：保存到输出流
     */
    public void save(java.io.OutputStream os) {
        try {
            doc.save(os);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }
}
