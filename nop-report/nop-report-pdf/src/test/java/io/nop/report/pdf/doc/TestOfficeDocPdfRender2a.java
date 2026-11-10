package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.pdf.verify.PdfImageRenderer;
import io.nop.pdf.verify.RegionProbe;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 2a acceptance: layout unit tests (no painting) plus rendered-PDF assertions
 * (text extraction, mediaBox, pixel probes for bold / color / underline / strikeout /
 * highlight / background, per-page header/footer, unknown and unimplemented blocks).
 *
 * <p>All fonts resolve through the session cache; the FontManager singleton's global
 * state is never modified.</p>
 */
public class TestOfficeDocPdfRender2a extends JunitBaseTestCase {

    // ---- model helpers -------------------------------------------------------

    private static OfficeDocModel doc(OfficeBlock... blocks) {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setWidth(595.3);
        doc.setHeight(841.9);
        doc.setBackground("FFFFFF");
        doc.setPages(new io.nop.commons.collections.KeyedList<>(p -> p.getName()));
        OfficeDocPageModel page = new OfficeDocPageModel();
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72);
        margins.setBottom(72);
        margins.setLeft(72);
        margins.setRight(72);
        margins.setHeader(36);
        margins.setFooter(36);
        page.setMargins(margins);
        page.setBody(new ArrayList<>());
        page.setHeader(new ArrayList<>());
        page.setFooter(new ArrayList<>());
        for (OfficeBlock block : blocks)
            page.getBody().add(block);
        doc.getPages().add(page);
        return doc;
    }

    private static OfficeParagraphModel paragraph(OfficeRun... runs) {
        OfficeParagraphModel paragraph = new OfficeParagraphModel();
        for (OfficeRun run : runs)
            paragraph.addRun(run);
        return paragraph;
    }

    private static OfficeRunModel run(String text) {
        return run(text, null);
    }

    private static int runSeq;

    private static OfficeRunModel run(String text, WordRunStyle style) {
        OfficeRunModel run = new OfficeRunModel();
        // runs live in a KeyedList keyed by id: null ids would collapse into one entry
        run.setId("r" + (++runSeq));
        run.setT(text);
        run.setStyle(style);
        return run;
    }

    private static WordRunStyle style(OfficeFont font) {
        WordRunStyle style = new WordRunStyle();
        style.setFont(font);
        return style;
    }

    private static OfficeFont font() {
        OfficeFont font = new OfficeFont();
        font.setFontSize(24f);
        return font;
    }

    private static String renderText(OfficeDocModel doc) throws Exception {
        byte[] pdf = new OfficeDocPdfRenderer().render(doc);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    // ---- layout unit tests (no painting) -------------------------------------

    @Test
    public void testMultiRunWrapLineCountAndText() throws Exception {
        OfficeDocModel doc = doc(
                paragraph(run("Hello bold world, this long paragraph must wrap into several lines",
                        style(new OfficeFont() {{
                            setFontSize(12f);
                            setBold(true);
                        }})),
                        run(" and regular text keeps flowing with the default size", style(font()))));
        try (DocRenderSession session = new DocRenderSession(doc)) {
            DocLayoutEngine engine = new DocLayoutEngine(session);
            List<DocPage> pages = engine.layout();
            assertEquals(1, pages.size());
            List<String> lines = pages.get(0).getBodyLineTexts();
            assertTrue(lines.size() > 1, "narrow page must wrap into several lines, got " + lines.size());
            String joined = String.join("", lines).replace(" ", "");
            String expected = ("Hello bold world, this long paragraph must wrap into several lines"
                    + " and regular text keeps flowing with the default size").replace(" ", "");
            assertEquals(expected, joined, "line texts must preserve the full text in order, got " + lines);
        }
    }

    @Test
    public void testFontResolvedOncePerTriple() throws Exception {
        OfficeDocModel doc = doc(
                paragraph(run("first", style(font()))),
                paragraph(run("second", style(font()))),
                paragraph(run("third", style(font()))));
        try (DocRenderSession session = new DocRenderSession(doc)) {
            new DocLayoutEngine(session).layout();
            // one (fontName=null, bold=false, italic=false) triple resolved exactly once (R2)
            assertEquals(1, session.getFontResolveCount());
        }
    }

    @Test
    public void testCjkWrapByCharacter() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++)
            sb.append("中");
        OfficeDocModel doc = doc(paragraph(run(sb.toString(), style(font()))));
        try (DocRenderSession session = new DocRenderSession(doc)) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            List<String> lines = pages.get(0).getBodyLineTexts();
            assertTrue(lines.size() > 3, "CJK text must wrap character by character, got " + lines.size());
            // CJK glyph advance = fontSize, so each line holds exactly width/24 chars
            DocPageGeometry geometry = pages.get(0).getGeometry();
            int perLine = (int) (geometry.getContentWidth() / font().getFontSize());
            assertEquals(perLine, lines.get(0).length());
            assertEquals(60, lines.stream().mapToInt(String::length).sum());
        }
    }

    @Test
    public void testLongParagraphPagination() throws Exception {
        List<OfficeBlock> blocks = new ArrayList<>();
        for (int i = 0; i < 60; i++)
            blocks.add(paragraph(run("body line " + i + " filler",
                    style(new OfficeFont() {{
                        setFontSize(12f);
                    }}))));
        OfficeDocModel doc = doc(blocks.toArray(new OfficeBlock[0]));
        try (DocRenderSession session = new DocRenderSession(doc)) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            assertTrue(pages.size() > 1, "long flow must paginate, got " + pages.size() + " pages");
            DocPageGeometry geometry = pages.get(0).getGeometry();
            float lineAdvance = 12f * DocLineBreaker.DEFAULT_LINE_SPACING;
            int maxLinesPerPage = (int) Math.ceil(geometry.getContentHeight() / lineAdvance) + 1;
            for (DocPage page : pages) {
                assertTrue(page.getBodyLineCount() > 0, "no empty pages");
                assertTrue(page.getBodyLineCount() <= maxLinesPerPage,
                        "page overflows content height: " + page.getBodyLineCount());
            }
            // last content lands on the last page
            List<String> lastLines = pages.get(pages.size() - 1).getBodyLineTexts();
            assertTrue(lastLines.get(lastLines.size() - 1).contains("59"));
        }
    }

    @Test
    public void testTabAndForcedBreak() throws Exception {
        OfficeTabModel tab = new OfficeTabModel();
        tab.setId("tab");
        OfficeBreakModel br = new OfficeBreakModel();
        br.setId("br");
        OfficeParagraphModel paragraph = paragraph(
                run("a", style(font())),
                tab,
                run("b", style(font())),
                br,
                run("c", style(font())));
        OfficeDocModel doc = doc(paragraph);
        try (DocRenderSession session = new DocRenderSession(doc)) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            List<String> lines = pages.get(0).getBodyLineTexts();
            // the tab advances "b" on the same line, the br forces "c" onto the next line
            assertEquals(2, lines.size());
            assertEquals("ab", lines.get(0));
            assertEquals("c", lines.get(1));
            DocBox.Paragraph box = (DocBox.Paragraph) pages.get(0).getBody().get(0).getBox();
            List<DocLine.Segment> segments = box.getLines().get(0).getSegments();
            assertEquals(2, segments.size());
            assertEquals(DocLineBreaker.TAB_WIDTH, segments.get(1).getX(), 0.1f);
        }
    }

    @Test
    public void testTitleFontSizeByLevel() throws Exception {
        OfficeDocModel doc = doc(title("T1", 1));
        try (DocRenderSession session = new DocRenderSession(doc)) {
            // no style: title block defaults to level 1 (18pt bold)
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            DocLine.Segment segment = firstSegment(pages);
            assertEquals(18f, segment.getStyle().getFontSize());
            assertTrue(segment.getStyle().isBold());
        }

        WordParagraphStyle level2 = new WordParagraphStyle();
        level2.setTitleLevel(2);
        try (DocRenderSession session = new DocRenderSession(doc(title("T2", 2)))) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            DocLine.Segment segment = firstSegment(pages);
            assertEquals(16f, segment.getStyle().getFontSize());
            assertTrue(segment.getStyle().isBold());
        }
    }

    private OfficeTitleModel title(String text, Integer level) {
        OfficeTitleModel title = new OfficeTitleModel();
        WordParagraphStyle style = new WordParagraphStyle();
        style.setTitleLevel(level);
        title.setStyle(style);
        title.addRun(run(text, style(new OfficeFont())));
        return title;
    }

    private DocLine.Segment firstSegment(List<DocPage> pages) {
        for (DocBox.Placed placed : pages.get(0).getBody()) {
            if (placed.getBox() instanceof DocBox.Paragraph paragraph
                    && !paragraph.getLines().isEmpty()
                    && !paragraph.getLines().get(0).getSegments().isEmpty())
                return paragraph.getLines().get(0).getSegments().get(0);
        }
        throw new IllegalStateException("no segment laid out");
    }

    // ---- render assertions ---------------------------------------------------

    @Test
    public void testTextExtractionOrderAndPages() throws Exception {
        OfficeDocModel doc = doc(
                paragraph(run("first paragraph", style(font()))),
                paragraph(run("第二段中文内容", style(font()))),
                paragraph(run("third paragraph", style(font()))));
        String text = renderText(doc);
        int first = text.indexOf("first paragraph");
        int second = text.indexOf("第二段中文内容");
        int third = text.indexOf("third paragraph");
        assertTrue(first >= 0 && second >= 0 && third >= 0);
        assertTrue(first < second && second < third, "reading order must be preserved, got: " + text);
    }

    @Test
    public void testMediaBoxEqualsSectionGeometry() throws Exception {
        OfficeDocModel doc = doc(paragraph(run("x", style(font()))));
        byte[] pdf = new OfficeDocPdfRenderer().render(doc);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            PDRectangle mediaBox = document.getPage(0).getMediaBox();
            DocPageGeometry geometry = DocPageGeometry.of(doc, doc.getPages().get(0), 0);
            assertEquals(geometry.getPageWidth(), mediaBox.getWidth(), 0.1f);
            assertEquals(geometry.getPageHeight(), mediaBox.getHeight(), 0.1f);
        }
    }

    @Test
    public void testMultiPageAndHeaderFooterOnEveryPage() throws Exception {
        List<OfficeBlock> body = new ArrayList<>();
        body.add(new OfficeTitleModel() {{
            addRun(run("Chapter One", style(new OfficeFont())));
        }});
        for (int i = 0; i < 60; i++)
            body.add(paragraph(run("body line " + i, style(font()))));
        OfficeDocModel doc = doc(body.toArray(new OfficeBlock[0]));
        doc.getPages().get(0).getHeader().add(paragraph(run("Doc Header", style(font()))));
        doc.getPages().get(0).getFooter().add(paragraph(run("Doc Footer", style(font()))));

        byte[] pdf = new OfficeDocPdfRenderer().render(doc);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(document);
                assertTrue(text.contains("Doc Header"), "page " + i + " missing header");
                assertTrue(text.contains("Doc Footer"), "page " + i + " missing footer");
            }
        }
    }

    /** One-page doc with a single 24pt paragraph of the given text, for pixel probes. */
    private byte[] renderSingleParagraph(WordRunStyle style, String text) throws Exception {
        OfficeDocModel doc = doc(paragraph(run(text, style)));
        byte[] pdf = new OfficeDocPdfRenderer().render(doc);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
        }
        return pdf;
    }

    private static final int DPI = 110;
    private static final float SCALE = DPI / 72f;

    /** Region of the first body line, from the content left edge, in pt -> px. */
    private BufferedImage pageImage(byte[] pdf, int pageIndex) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return PdfImageRenderer.renderPage(document, pageIndex, DPI);
        }
    }

    private Color averageFirstLine(BufferedImage img) {
        int x = Math.round(72 * SCALE);
        int y = Math.round(72 * SCALE);
        int w = Math.round(120 * SCALE);
        int h = Math.round(24 * SCALE);
        return RegionProbe.averageColor(img, x, y, w, h);
    }

    @Test
    public void testBoldProbeDarkerThanRegular() throws Exception {
        // default font (思源宋体) has no bold variant: synthetic bold strokes darken the region
        WordRunStyle bold = style(new OfficeFont() {{
            setFontSize(24f);
            setBold(true);
        }});
        WordRunStyle regular = style(font());
        Color boldAvg = averageFirstLine(pageImage(renderSingleParagraph(bold, "测试Test80"), 0));
        Color regularAvg = averageFirstLine(pageImage(renderSingleParagraph(regular, "测试Test80"), 0));
        int boldLum = boldAvg.getRed() + boldAvg.getGreen() + boldAvg.getBlue();
        int regularLum = regularAvg.getRed() + regularAvg.getGreen() + regularAvg.getBlue();
        assertTrue(boldLum < regularLum,
                "synthetic bold must darken the text region: bold=" + boldAvg + " regular=" + regularAvg);
    }

    @Test
    public void testTextColorProbe() throws Exception {
        WordRunStyle red = style(new OfficeFont() {{
            setFontSize(24f);
            setFontColor("FF0000");
        }});
        Color avg = averageFirstLine(pageImage(renderSingleParagraph(red, "红色Red"), 0));
        assertTrue(avg.getRed() > avg.getGreen() && avg.getRed() > avg.getBlue(),
                "red text must shift the region average toward red: " + avg);
    }

    @Test
    public void testUnderlineAndStrikeoutProbe() throws Exception {
        // "A   A" so the probe column sits in the gap: only the decoration crosses it
        WordRunStyle underline = style(new OfficeFont() {{
            setFontSize(24f);
            setUnderlineStyle(OfficeFontUnderline.SINGLE);
        }});
        BufferedImage img = pageImage(renderSingleParagraph(underline, "A   A"), 0);
        // first line: top 72pt, line height 24*1.2, baseline = 72 + (28.8-24)/2 + 24*0.85
        float baseline = 72f + (24f * 1.2f - 24f) / 2 + 24f * DocLineBreaker.ASCENT_RATIO;
        float underlineY = baseline + 24f * DocPagePainter.UNDERLINE_OFFSET;
        int probeX = Math.round((72f + 25f) * SCALE); // inside the gap between the two glyphs
        int stroke = RegionProbe.measureHorizontalStrokeWidth(img, probeX,
                Math.round((underlineY - 2) * SCALE), Math.round((underlineY + 2) * SCALE),
                Color.WHITE, 40);
        assertTrue(stroke >= 1, "underline line must cross the probe column");

        WordRunStyle strike = style(new OfficeFont() {{
            setFontSize(24f);
            setStrikeout(true);
        }});
        BufferedImage img2 = pageImage(renderSingleParagraph(strike, "A   A"), 0);
        float strikeY = baseline - 24f * DocPagePainter.STRIKEOUT_OFFSET;
        int stroke2 = RegionProbe.measureHorizontalStrokeWidth(img2, probeX,
                Math.round((strikeY - 2) * SCALE), Math.round((strikeY + 2) * SCALE),
                Color.WHITE, 40);
        assertTrue(stroke2 >= 1, "strikeout line must cross the probe column");
    }

    @Test
    public void testHighlightProbe() throws Exception {
        WordRunStyle highlighted = new WordRunStyle();
        highlighted.setFont(font());
        highlighted.setHighlightColor("yellow");
        BufferedImage img = pageImage(renderSingleParagraph(highlighted, "A   A"), 0);
        float baseline = 72f + (24f * 1.2f - 24f) / 2 + 24f * DocLineBreaker.ASCENT_RATIO;
        int x = Math.round((72f + 25f) * SCALE);
        int yTop = Math.round((baseline - 24f) * SCALE);
        int yBottom = Math.round((baseline + 3f) * SCALE);
        Color avg = RegionProbe.averageColor(img, x - 3, yTop, 6, yBottom - yTop);
        assertTrue(avg.getRed() > 200 && avg.getGreen() > 200 && avg.getBlue() < 100,
                "yellow highlight must fill behind the glyphs, gap average=" + avg);
    }

    @Test
    public void testBackgroundProbe() throws Exception {
        OfficeDocModel doc = doc(paragraph(run("x", style(font()))));
        doc.setBackground("EEEEEE");
        BufferedImage img = pageImage(new OfficeDocPdfRenderer().render(doc), 0);
        Color avg = RegionProbe.averageColor(img,
                Math.round(300 * SCALE), Math.round(400 * SCALE), 40, 40);
        assertTrue(avg.getRed() > 200 && avg.getRed() < 250
                        && Math.abs(avg.getRed() - avg.getGreen()) < 6
                        && Math.abs(avg.getGreen() - avg.getBlue()) < 6,
                "page background must be light gray, got " + avg);
    }

    @Test
    public void testUnknownBlockThrows() throws Exception {
        OfficeDocModel doc = doc(paragraph(run("a", style(font()))));
        doc.getPages().get(0).getBody().add(new OfficeBlock() {
        });
        try {
            new OfficeDocPdfRenderer().render(doc);
            fail("unknown block type must fail fast");
        } catch (NopException e) {
            assertEquals("nop.err.report.pdf.doc.unknown-block", e.getErrorCode());
        }
    }

    @Test
    public void testNamedStyleResolution() throws Exception {
        WordParagraphStyle named = new WordParagraphStyle();
        named.setId("myTitle");
        named.setTitleLevel(2);
        WordRunStyle namedRun = new WordRunStyle();
        namedRun.setId("brand");
        OfficeFont brandFont = new OfficeFont();
        brandFont.setFontColor("008000");
        namedRun.setFont(brandFont);
        OfficeDocModel doc = doc(paragraph(run("styled", style(font()))));
        doc.addStyle(named);
        doc.addStyle(namedRun);

        OfficeTitleModel title = new OfficeTitleModel();
        WordParagraphStyle inline = new WordParagraphStyle();
        inline.setId("myTitle");
        title.setStyle(inline);
        WordRunStyle runRef = new WordRunStyle();
        runRef.setId("brand");
        title.addRun(run("Head", runRef));
        doc.getPages().get(0).getBody().add(0, title);

        try (DocRenderSession session = new DocRenderSession(doc)) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            // first body block is the title
            DocBox.Paragraph box = (DocBox.Paragraph) pages.get(0).getBody().get(0).getBox();
            DocLine.Segment segment = box.getLines().get(0).getSegments().get(0);
            // title level 2 -> 16pt, but the named run style overrides size/color
            assertEquals(16f, segment.getStyle().getFontSize());
            assertEquals("008000", segment.getStyle().getColor());
            assertTrue(segment.getStyle().isBold());
        }
        assertNotNull(renderText(doc));
    }
}
