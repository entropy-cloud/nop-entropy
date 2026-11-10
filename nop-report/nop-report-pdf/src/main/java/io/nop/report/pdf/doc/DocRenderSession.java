package io.nop.report.pdf.doc;

import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeStyleResolver;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.report.pdf.font.FontManager;
import io.nop.report.pdf.renderer.PdfRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One OfficeDocModel -> PDF rendering session. Layout and paint share the single
 * {@link PDDocument} owned by the session: a PDFont is bound to its document, so each
 * (fontName, bold, italic) triple is resolved through {@link FontManager} exactly once
 * and reused for both measuring and drawing.
 *
 * <p>The session only calls {@link FontManager} resolution; it never mutates the
 * singleton's aliases or discovery state.</p>
 */
public class DocRenderSession implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(DocRenderSession.class);

    private final OfficeDocModel doc;
    private final PDDocument document;
    private final PdfRenderer pdfRenderer;
    private final OfficeStyleResolver styleResolver;

    private final Map<String, FontManager.FontResolution> fontCache = new HashMap<>();
    private final Map<PDFont, Map<Integer, Float>> widthCache = new IdentityHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int fontResolveCount;

    public DocRenderSession(OfficeDocModel doc) {
        this.doc = doc;
        this.document = new PDDocument();
        this.pdfRenderer = new PdfRenderer(document);
        this.styleResolver = new OfficeStyleResolver(doc);
    }

    public OfficeDocModel getDoc() {
        return doc;
    }

    public PDDocument getDocument() {
        return document;
    }

    public PdfRenderer getPdfRenderer() {
        return pdfRenderer;
    }

    public OfficeStyleResolver getStyleResolver() {
        return styleResolver;
    }

    /** Named degradations recorded while laying out (row truncation, list glyph defaults...). */
    public List<String> getWarnings() {
        return warnings;
    }

    public void warn(String code, String detail) {
        String message = code + ":" + detail;
        warnings.add(message);
        LOG.warn("{}", message);
    }

    /** How many times a font triple actually went through FontManager (cache misses). */
    public int getFontResolveCount() {
        return fontResolveCount;
    }

    // ---- fonts ---------------------------------------------------------------

    /**
     * Resolve (fontName, bold, italic) once per session. A null font name maps to the
     * session default font with the requested bold/italic dimensions synthesized.
     */
    public FontManager.FontResolution resolveFont(String fontName, boolean bold, boolean italic) {
        String key = fontName + "|" + bold + "|" + italic;
        FontManager.FontResolution res = fontCache.get(key);
        if (res == null) {
            fontResolveCount++;
            if (fontName == null || fontName.isEmpty()) {
                res = new FontManager.FontResolution(pdfRenderer.getDefaultFont(), bold, italic);
            } else {
                res = FontManager.instance().resolveFont(fontName, bold, italic, document);
            }
            fontCache.put(key, res);
        }
        return res;
    }

    /**
     * Font used for a concrete text: the style font, or the CJK fallback when the style
     * font cannot encode it. Missing glyph without fallback keeps the existing error
     * semantics of {@link PdfRenderer#fontForText(String, PDFont)}.
     */
    public FontManager.FontResolution fontForText(String text, DocTextStyle style) {
        FontManager.FontResolution res = resolveFont(style.getFontName(), style.isBold(), style.isItalic());
        return pdfRenderer.fontForText(text, res, style.isBold(), style.isItalic());
    }

    /** Advance width of one code point in pt at the given font size. */
    public float charWidth(PDFont font, int codePoint, float fontSize) {
        Map<Integer, Float> widths = widthCache.computeIfAbsent(font, k -> new HashMap<>());
        Float units = widths.get(codePoint);
        if (units == null) {
            try {
                units = font.getStringWidth(new String(Character.toChars(codePoint)));
            } catch (IOException | IllegalArgumentException e) {
                units = 0f;
            }
            widths.put(codePoint, units);
        }
        return units / 1000f * fontSize;
    }

    public float textWidth(PDFont font, String text, float fontSize) {
        float width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += charWidth(font, cp, fontSize);
            i += Character.charCount(cp);
        }
        return width;
    }

    // ---- styles --------------------------------------------------------------

    /** Document default text attributes: the default run style when one is declared. */
    public DocTextStyle baseTextStyle() {
        DocTextStyle style = new DocTextStyle();
        OfficeStyle def = styleResolver.defaultStyle(WordRunStyle.class);
        if (def != null)
            style.apply(resolveNamedRunStyle(def.getId(), (WordRunStyle) def));
        return style;
    }

    /** {@code base} overlaid by the run's named style chain, then by its inline fields. */
    public DocTextStyle textStyle(DocTextStyle base, WordRunStyle inline) {
        DocTextStyle style = base.copy();
        if (inline == null)
            return style;
        String namedId = namedStyleId(inline.getId(), inline.getBasedOn(), WordRunStyle.class);
        if (namedId != null)
            style.apply(styleResolver.resolveRunStyle(namedId));
        style.apply(inline);
        return style;
    }

    private WordRunStyle resolveNamedRunStyle(String id, WordRunStyle fallback) {
        if (id != null && doc.getStyle(id) instanceof WordRunStyle)
            return styleResolver.resolveRunStyle(id);
        return fallback;
    }

    /**
     * Effective paragraph style: default paragraph style, overlaid by the named chain
     * referenced through the inline style's id/basedOn, overlaid by inline non-null fields.
     */
    public WordParagraphStyle paragraphStyle(WordParagraphStyle inline) {
        WordParagraphStyle merged = new WordParagraphStyle();
        OfficeStyle def = styleResolver.defaultStyle(WordParagraphStyle.class);
        if (def != null) {
            WordParagraphStyle resolved = def.getId() != null && doc.getStyle(def.getId()) == def
                    ? styleResolver.resolveParagraphStyle(def.getId()) : (WordParagraphStyle) def;
            overlay(merged, resolved);
        }
        if (inline != null) {
            String namedId = namedStyleId(inline.getId(), inline.getBasedOn(), WordParagraphStyle.class);
            if (namedId != null)
                overlay(merged, styleResolver.resolveParagraphStyle(namedId));
            overlay(merged, inline);
        }
        return merged;
    }

    public WordCellStyle cellStyle(String styleId) {
        if (styleId == null || !(doc.getStyle(styleId) instanceof WordCellStyle))
            return null;
        return styleResolver.resolveCellStyle(styleId);
    }

    public WordListStyle listStyle(String styleId) {
        if (styleId == null || !(doc.getStyle(styleId) instanceof WordListStyle))
            return null;
        return styleResolver.resolveListStyle(styleId);
    }

    private String namedStyleId(String id, String basedOn, Class<?> type) {
        if (id != null && type.isInstance(doc.getStyle(id)))
            return id;
        if (basedOn != null && type.isInstance(doc.getStyle(basedOn)))
            return basedOn;
        return null;
    }

    private static void overlay(WordParagraphStyle target, WordParagraphStyle source) {
        if (source.getAlign() != null)
            target.setAlign(source.getAlign());
        if (source.getFirstLineIndent() != null)
            target.setFirstLineIndent(source.getFirstLineIndent());
        if (source.getLeftIndent() != null)
            target.setLeftIndent(source.getLeftIndent());
        if (source.getRightIndent() != null)
            target.setRightIndent(source.getRightIndent());
        if (source.getLineSpacing() != null)
            target.setLineSpacing(source.getLineSpacing());
        if (source.getSpaceBefore() != null)
            target.setSpaceBefore(source.getSpaceBefore());
        if (source.getSpaceAfter() != null)
            target.setSpaceAfter(source.getSpaceAfter());
        if (source.getTitleLevel() != null)
            target.setTitleLevel(source.getTitleLevel());
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
