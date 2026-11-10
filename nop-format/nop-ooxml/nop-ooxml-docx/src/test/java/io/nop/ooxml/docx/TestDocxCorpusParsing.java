package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.ooxml.docx.corpus.DocxCorpusBuilder;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_PACKAGE_INVALID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_PART_MISSING;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_XML_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI0 corpus regression: parses the 11 deterministic corpus samples produced by
 * {@link DocxCorpusBuilder} through the full docx -> WordOfficePackage ->
 * OfficeDocModelParser -> typed model path and asserts the WI1 block/inline/section/
 * style semantics. Also covers the P6 tolerance degradation paths.
 */
public class TestDocxCorpusParsing extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(
                io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static OfficeDocModelParser lastParser;

    private static OfficeDocModel parse(byte[] bytes) {
        ByteArrayResource resource = new ByteArrayResource("/tmp/wi3-corpus/sample.docx", bytes, 0L);
        lastParser = new OfficeDocModelParser();
        return lastParser.parseFromResource(resource);
    }

    private static java.util.List<String> lastSkips() {
        return lastParser == null ? null : lastParser.getLastParseSkips();
    }

    private static List<OfficeBlock> body(OfficeDocModel doc) {
        assertEquals(1, doc.getPages().size());
        return doc.getPages().get(0).getBody();
    }

    private static String runText(OfficeRun run) {
        assertTrue(run instanceof OfficeRunModel, "expected text run, got " + run.getClass());
        return ((OfficeRunModel) run).getT();
    }

    private static String runType(OfficeRun run) {
        if (run instanceof OfficeRunModel) return "r";
        if (run instanceof OfficeTabModel) return "tab";
        if (run instanceof OfficeBreakModel) return "br";
        if (run instanceof OfficeLinkModel) return "link";
        return run.getClass().getSimpleName();
    }

    private static String styleType(OfficeStyle style) {
        if (style instanceof io.nop.office.model.WordParagraphStyle) {
            return ((io.nop.office.model.WordParagraphStyle) style).getType();
        }
        if (style instanceof WordRunStyle) {
            return ((WordRunStyle) style).getType();
        }
        if (style instanceof WordCellStyle) {
            return ((WordCellStyle) style).getType();
        }
        return style.getClass().getSimpleName();
    }

    private static String typeOf(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) return "p";
        if (block instanceof OfficeTitleModel) return "title";
        if (block instanceof OfficeListModel) return "list";
        if (block instanceof OfficeImageModel) return "image";
        if (block instanceof OfficeHyperlinkModel) return "hyperlink";
        if (block instanceof OfficeSeparatorModel) return "separator";
        if (block instanceof io.nop.office.doc.model.OfficePageBreakModel) return "pageBreak";
        if (block instanceof WordTable) return "table";
        return block.getClass().getSimpleName();
    }

    private static String headerText(List<OfficeBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        collectText(blocks, sb);
        return sb.toString();
    }

    private static void collectText(List<OfficeBlock> blocks, StringBuilder sb) {
        for (OfficeBlock block : blocks) {
            if (block instanceof OfficeParagraphModel) {
                for (OfficeRun run : ((OfficeParagraphModel) block).getRuns()) {
                    if (run instanceof OfficeRunModel) {
                        String t = ((OfficeRunModel) run).getT();
                        if (t != null) {
                            sb.append(t);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- corpus samples

    @Test
    public void testSimpleDocument() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.simpleDocument());
        List<OfficeBlock> blocks = body(doc);
        assertEquals(2, blocks.size());
        assertEquals("p", typeOf(blocks.get(0)));
        assertEquals("p", typeOf(blocks.get(1)));

        OfficeParagraphModel para = (OfficeParagraphModel) blocks.get(0);
        assertEquals("Hello Nop docx corpus.", runText(para.getRuns().get(0)));
        assertEquals("center", para.getStyle().getAlign().getWmlText());

        OfficeParagraphModel para2 = (OfficeParagraphModel) blocks.get(1);
        assertEquals("Second indented paragraph.", runText(para2.getRuns().get(0)));

        // page setup: A4 portrait 11906x16838 twips, margins 1417 twips
        assertEquals(11906 / 20.0, doc.getWidth(), 0.01);
        assertEquals(16838 / 20.0, doc.getHeight(), 0.01);
        OfficeDocPageModel page = doc.getPages().get(0);
        assertNotNull(page.getMargins());
        assertEquals(1417 / 20.0, page.getMargins().getTop(), 0.01);
        assertEquals(1417 / 20.0, page.getMargins().getLeft(), 0.01);
        assertNull(page.getOrientation());
    }

    @Test
    public void testTitleAndListDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.titleAndListDoc());
        List<OfficeBlock> blocks = body(doc);

        assertEquals("title", typeOf(blocks.get(0)));
        OfficeTitleModel title = (OfficeTitleModel) blocks.get(0);
        assertEquals(Integer.valueOf(1), title.getStyle().getTitleLevel());
        assertEquals("Report Title", runText(title.getRuns().get(0)));
        // pStyle reference lives on style.id only (P1), never on type
        assertEquals("Title", title.getStyle().getId());
        assertEquals("title", typeOf(title));

        assertEquals("title", typeOf(blocks.get(1)));
        assertEquals(Integer.valueOf(1), ((OfficeTitleModel) blocks.get(1)).getStyle().getTitleLevel());

        // list aggregation: two consecutive numPr paragraphs with same numId
        assertTrue(blocks.get(2) instanceof OfficeListModel);
        OfficeListModel list = (OfficeListModel) blocks.get(2);
        assertEquals("list", typeOf(list));
        assertEquals("1", list.getNumId());
        assertEquals(2, list.getItems().size());
        assertEquals(0, list.getItems().get(0).getLevel());
        assertEquals(1, list.getItems().get(1).getLevel());
        assertEquals("first level item", runText(list.getItems().get(0).getRuns().get(0)));
        assertEquals("second level item", runText(list.getItems().get(1).getRuns().get(0)));

        // Heading1 paragraph interrupts the list, then a new list starts
        assertEquals("title", typeOf(blocks.get(3)));
        assertTrue(blocks.get(4) instanceof OfficeListModel);
        OfficeListModel list2 = (OfficeListModel) blocks.get(4);
        assertEquals("1", list2.getNumId());
        assertEquals(1, list2.getItems().size());
    }

    @Test
    public void testImageDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.imageDoc());
        List<OfficeBlock> blocks = body(doc);

        OfficeImageModel image = (OfficeImageModel) blocks.get(1);
        assertEquals("image", typeOf(image));
        assertEquals("word/media/image1.png", image.getAssetRef());
        // EMU -> pt: 1905000/12700 = 150, 952500/12700 = 75
        assertEquals(150.0, image.getWidth(), 0.01);
        assertEquals(75.0, image.getHeight(), 0.01);

        assertTrue(doc.hasAsset("word/media/image1.png"));
        assertNotNull(doc.getAsset("word/media/image1.png").getData());
        assertFalse(doc.getAsset("word/media/image1.png").getData().toByteArray().length == 0);
        assertEquals("png", doc.getAsset("word/media/image1.png").getExt());
    }

    @Test
    public void testHyperlinkDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.hyperlinkDoc());
        List<OfficeBlock> blocks = body(doc);

        OfficeHyperlinkModel hyperlink = (OfficeHyperlinkModel) blocks.get(0);
        assertEquals("hyperlink", typeOf(hyperlink));
        assertEquals("https://nop.example.com/corpus", hyperlink.getHref());
        assertNull(hyperlink.getAnchor());
        assertEquals(1, hyperlink.getRuns().size());
        assertEquals("corpus hyperlink", runText(hyperlink.getRuns().get(0)));

        assertEquals("p", typeOf(blocks.get(1)));
    }

    @Test
    public void testInlineTabBreakDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.inlineTabBreakDoc());
        List<OfficeBlock> blocks = body(doc);

        OfficeParagraphModel para = (OfficeParagraphModel) blocks.get(0);
        List<OfficeRun> runs = para.getRuns();
        assertEquals(5, runs.size());
        assertEquals("r", runType(runs.get(0)));
        assertEquals("before tab", runText(runs.get(0)));
        assertEquals("tab", runType(runs.get(1)));
        assertTrue(runs.get(1) instanceof OfficeTabModel);
        assertEquals("r", runType(runs.get(2)));
        assertEquals("after tab", runText(runs.get(2)));
        assertEquals("br", runType(runs.get(3)));
        assertTrue(runs.get(3) instanceof OfficeBreakModel);
        assertNull(((OfficeBreakModel) runs.get(3)).getBrType());
        assertEquals("r", runType(runs.get(4)));
        assertEquals("after line break", runText(runs.get(4)));
    }

    @Test
    public void testPageBreakAndSeparatorDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.pageBreakAndSeparatorDoc());
        List<OfficeBlock> blocks = body(doc);

        assertEquals(5, blocks.size());
        assertEquals("p", typeOf(blocks.get(0)));
        assertEquals("pageBreak", typeOf(blocks.get(1)));
        assertTrue(blocks.get(1) instanceof io.nop.office.doc.model.OfficePageBreakModel);
        assertEquals("p", typeOf(blocks.get(2)));
        assertEquals("separator", typeOf(blocks.get(3)));
        assertTrue(blocks.get(3) instanceof OfficeSeparatorModel);
        assertEquals("p", typeOf(blocks.get(4)));
        assertEquals("After separator.", runText(((OfficeParagraphModel) blocks.get(4)).getRuns().get(0)));
    }

    @Test
    public void testMultiSectionDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.multiSectionDoc());
        assertEquals(2, doc.getPages().size());

        OfficeDocPageModel page1 = doc.getPages().get(0);
        assertNull(page1.getOrientation());
        assertEquals(11906 / 20.0, page1.getWidth(), 0.01);
        assertEquals(16838 / 20.0, page1.getHeight(), 0.01);
        assertEquals("Section one body.",
                runText(((OfficeParagraphModel) page1.getBody().get(0)).getRuns().get(0)));
        assertTrue(headerText(page1.getHeader()).contains("Header One"));
        assertTrue(headerText(page1.getFooter()).contains("Footer One"));

        OfficeDocPageModel page2 = doc.getPages().get(1);
        assertEquals("landscape", page2.getOrientation());
        assertEquals(16838 / 20.0, page2.getWidth(), 0.01);
        assertEquals(11906 / 20.0, page2.getHeight(), 0.01);
        assertEquals("Section two body.",
                runText(((OfficeParagraphModel) page2.getBody().get(0)).getRuns().get(0)));
        assertTrue(headerText(page2.getHeader()).contains("Header Two"));
        assertTrue(headerText(page2.getFooter()).contains("Footer Two"));
    }

    @Test
    public void testMergedTableDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.mergedTableDoc());
        List<OfficeBlock> blocks = body(doc);

        WordTable table = (WordTable) blocks.get(0);
        assertEquals(3, table.getRows().size());
        assertEquals(400.0 / 20.0, table.getRows().get(0).getHeight(), 0.001, "trHeight atLeast 400 twips -> 20pt");
        assertEquals(360.0 / 20.0, table.getRows().get(1).getHeight(), 0.001, "trHeight atLeast 360 twips -> 18pt");

        // row 1: gridSpan=2 -> mergeAcross=1
        WordTableRow row0 = table.getRows().get(0);
        WordTableCell mergedAcrossCell = row0.getCells().get(0);
        assertEquals(1, mergedAcrossCell.getMergeAcross());
        assertEquals("merged across", mergedAcrossCell.getValue());
        assertNotNull(mergedAcrossCell.getStyleId());

        // row 2: vMerge restart with one continuation row -> mergeDown=1
        WordTableCell mergedDownCell = table.getRows().get(1).getCells().get(0);
        assertEquals(1, mergedDownCell.getMergeDown());
        assertEquals("merged down", mergedDownCell.getValue());

        // inline cell style registered in doc.styles with shading + borders
        String styleId = mergedAcrossCell.getStyleId();
        OfficeStyle style = doc.getStyle(styleId);
        assertNotNull(style);
        assertTrue(style instanceof WordCellStyle);
        WordCellStyle cellStyle = (WordCellStyle) style;
        assertEquals("cellStyle", cellStyle.getType());
        assertEquals("DCE6F1", cellStyle.getBackgroundColor());
        assertEquals("center", cellStyle.getVerticalAlign().getWmlText());
        assertNotNull(cellStyle.getTopBorder());
        assertEquals("333333", cellStyle.getTopBorder().getColor());
        assertNotNull(cellStyle.getBottomBorder());

        // continuation cells are dropped: only the two continue-row cells carry values
        long withValue = table.getRows().get(2).getCells().stream()
                .filter(java.util.Objects::nonNull)
                .filter(c -> !StringHelper.isEmpty(c.getValue())).count();
        assertEquals(2, withValue);
        assertNull(table.getRows().get(2).getCells().get(0).getValue());
    }

    @Test
    public void testFullRunStyleDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.fullRunStyleDoc());
        List<OfficeBlock> blocks = body(doc);

        OfficeParagraphModel para1 = (OfficeParagraphModel) blocks.get(0);
        List<OfficeRun> runs = para1.getRuns();
        assertEquals(7, runs.size());

        assertBold(runStyle(runs.get(0)), true);
        assertBold(runStyle(runs.get(1)), false);
        assertTrue(runStyle(runs.get(1)).getFont().isItalic());
        assertEquals(io.nop.office.model.constants.OfficeFontUnderline.SINGLE,
                runStyle(runs.get(2)).getFont().getUnderlineStyle());
        assertTrue(runStyle(runs.get(3)).getFont().isStrikeout());
        assertEquals("yellow", runStyle(runs.get(4)).getHighlightColor());
        assertEquals(io.nop.office.model.constants.OfficeFontVerticalAlign.superscript,
                runStyle(runs.get(5)).getFont().getVerticalAlign());
        assertEquals(io.nop.office.model.constants.OfficeFontVerticalAlign.subscript,
                runStyle(runs.get(6)).getFont().getVerticalAlign());

        OfficeParagraphModel para2 = (OfficeParagraphModel) blocks.get(1);
        WordRunStyle style = runStyle(para2.getRuns().get(0));
        assertEquals("Arial", style.getFont().getFontName());
        // w:sz=36 half-points -> 18pt
        assertEquals(18.0f, style.getFont().getFontSize(), 0.001f);
        assertEquals("C00000", style.getFont().getFontColor());
        assertBold(style, true);
    }

    private static WordRunStyle runStyle(OfficeRun run) {
        assertNotNull(((OfficeRunModel) run).getStyle(), "run should carry inline style");
        return ((OfficeRunModel) run).getStyle();
    }

    private static void assertBold(WordRunStyle style, boolean expected) {
        if (expected) {
            assertNotNull(style.getFont(), "expected font with bold");
            assertTrue(style.getFont().isBold());
        } else if (style.getFont() != null) {
            assertFalse(style.getFont().isBold());
        }
    }

    @Test
    public void testPageSetupDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.pageSetupDoc());
        OfficeDocPageModel page = doc.getPages().get(0);
        assertEquals("landscape", page.getOrientation());
        assertEquals(16838 / 20.0, page.getWidth(), 0.01);
        assertEquals(11906 / 20.0, page.getHeight(), 0.01);
        assertNotNull(page.getMargins());
        assertEquals(1417 / 20.0, page.getMargins().getTop(), 0.01);
        assertEquals(708 / 20.0, page.getMargins().getHeader(), 0.01);
        assertEquals(0.0, page.getMargins().getGutter(), 0.001);
        assertEquals(16838 / 20.0, doc.getWidth(), 0.01);
    }

    @Test
    public void testBackgroundWatermarkDoc() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.backgroundWatermarkDoc());
        // w:background has no model field (adjudicated Deferred) - must be skipped
        // without breaking the parse; the watermark stays inside the header blocks.
        assertEquals(1, doc.getPages().size());
        OfficeDocPageModel page = doc.getPages().get(0);
        assertFalse(page.getHeader().isEmpty());
        // watermark is VML textpath (no w:t runs): header keeps the pict-bearing run block
        assertFalse(page.getHeader().isEmpty(), "watermark sample keeps header blocks");
        assertNotNull(doc.getPages().get(0).getBody().get(0));
    }

    @Test
    public void testStylesFromStylesXml() {
        // every corpus sample embeds styles.xml with Title/Heading1 paragraph styles
        OfficeDocModel doc = parse(DocxCorpusBuilder.simpleDocument());
        OfficeStyle title = doc.getStyle("Title");
        assertNotNull(title);
        assertEquals("paragraphStyle", styleType(title));
        assertEquals("center", ((io.nop.office.model.WordParagraphStyle) title).getAlign().getWmlText());

        OfficeStyle heading = doc.getStyle("Heading1");
        assertNotNull(heading);
        assertEquals("paragraphStyle", styleType(heading));
        // outlineLvl=0 -> titleLevel 1
        assertEquals(Integer.valueOf(1), ((io.nop.office.model.WordParagraphStyle) heading).getTitleLevel());
    }

    // ------------------------------------------------------------- tolerance (P6)

    private static byte[] zipOf(List<String[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        for (String[] entry : entries) {
            zip.putNextEntry(new ZipEntry(entry[0]));
            zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        zip.finish();
        zip.close();
        return out.toByteArray();
    }

    @Test
    public void testCorruptZipFailsWithExplicitErrorCode() {
        byte[] corrupt = "this is not a zip file".getBytes(StandardCharsets.UTF_8);
        // the in-memory zip store tolerates non-zip payloads and yields an empty
        // package, so the parse degrades with an explicit docx parse error code
        // (never a raw ZipException)
        NopException e = assertThrows(NopException.class, () -> parse(corrupt));
        assertTrue(e.getErrorCode().startsWith("nop.err.ooxml.docx.parse-"));
    }

    @Test
    public void testMissingDocumentPartFailsWithExplicitErrorCode() throws Exception {
        byte[] bytes = zipOf(List.of(
                new String[]{"[Content_Types].xml", "<?xml version=\"1.0\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>"},
                new String[]{"word/styles.xml", "<?xml version=\"1.0\"?><w:styles xmlns:w=\"urn:x\"/>"}
        ));
        NopException e = assertThrows(NopException.class, () -> parse(bytes));
        assertEquals(ERR_DOCX_PARSE_PART_MISSING.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testTruncatedDocumentXmlFailsWithExplicitErrorCode() throws Exception {
        byte[] bytes = zipOf(List.of(
                new String[]{"[Content_Types].xml", "<?xml version=\"1.0\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>"},
                new String[]{"word/document.xml", "<?xml version=\"1.0\"?><w:document><w:body><w:p>"}
        ));
        NopException e = assertThrows(NopException.class, () -> parse(bytes));
        assertEquals(ERR_DOCX_PARSE_XML_INVALID.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testDanglingHyperlinkRelationshipDegrades() throws Exception {
        // hyperlink block references a relationship id that does not exist in the
        // rels part: must degrade to a hyperlink block without href + skip record,
        // never throw.
        byte[] bytes = zipOf(List.of(
                new String[]{"[Content_Types].xml", "<?xml version=\"1.0\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>"},
                new String[]{"word/document.xml", "<?xml version=\"1.0\"?>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<w:body><w:hyperlink r:id=\"rIdMissing\">"
                        + "<w:r><w:t>dangling link</w:t></w:r></w:hyperlink>"
                        + "<w:p><w:r><w:t>after</w:t></w:r></w:p></w:body></w:document>"},
                new String[]{"word/_rels/document.xml.rels", "<?xml version=\"1.0\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>"}
        ));

        OfficeDocModelParser parser = new OfficeDocModelParser();
        OfficeDocModel doc = parser.parseFromResource(
                new ByteArrayResource("/tmp/wi3-corpus/dangling.docx", bytes, 0L));

        List<OfficeBlock> blocks = body(doc);
        OfficeHyperlinkModel hyperlink = (OfficeHyperlinkModel) blocks.get(0);
        assertEquals("hyperlink", typeOf(hyperlink));
        assertNull(hyperlink.getHref());
        assertEquals("dangling link", runText(hyperlink.getRuns().get(0)));

        // degradation evidence: aggregated skip record exists
        assertFalse(parser.getLastParseSkips().isEmpty());
    }

    @Test
    public void testInlineLinkModelWithNestedRuns() {
        // inline (paragraph-level) plain hyperlink becomes OfficeLinkModel
        OfficeDocModel doc = parse(DocxCorpusBuilder.hyperlinkDoc());
        OfficeHyperlinkModel block = (OfficeHyperlinkModel) body(doc).get(0);
        // nested runs preserve type dispatch
        assertEquals(1, block.getRuns().size());
        assertEquals("r", runType(block.getRuns().get(0)));
    }

    @Test
    public void testInlineLinkType() {
        // paragraph-embedded hyperlink yields a link-type run
        List<String[]> entries = List.of(
                new String[]{"[Content_Types].xml", "<?xml version=\"1.0\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>"},
                new String[]{"word/document.xml", "<?xml version=\"1.0\"?>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<w:body><w:p><w:r><w:t>text before</w:t></w:r><w:hyperlink r:id=\"rId1\">"
                        + "<w:r><w:t>link text</w:t></w:r></w:hyperlink></w:p></w:body></w:document>"},
                new String[]{"word/_rels/document.xml.rels", "<?xml version=\"1.0\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" "
                        + "Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>"}
        );
        try {
            OfficeDocModel doc = parse(zipOf(entries));
            OfficeParagraphModel para = (OfficeParagraphModel) body(doc).get(0);
            assertEquals(2, para.getRuns().size());
            assertEquals("r", runType(para.getRuns().get(0)));
            OfficeRun link = para.getRuns().get(1);
            assertEquals("link", runType(link));
            assertTrue(link instanceof OfficeLinkModel);
            assertEquals("https://example.com", ((OfficeLinkModel) link).getHref());
            assertEquals("link text", runText(((OfficeLinkModel) link).getRuns().get(0)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testMixedBreakRunKeepsTextAndSkips() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.mixedBreakDoc());
        List<OfficeBlock> blocks = body(doc);
        assertEquals(1, blocks.size());
        OfficeParagraphModel para = (OfficeParagraphModel) blocks.get(0);
        assertEquals(1, para.getRuns().size(), "mixed run stays a text run");
        OfficeRunModel run = (OfficeRunModel) para.getRuns().get(0);
        // WordXmlHelper renders the kept w:br as a newline: text is fully preserved
        assertEquals("before break\nafter break", run.getT(), "br inside a text run must not drop text");

        // the undispatched br element is named in the skip ledger, not silently dropped
        java.util.List<String> skips = lastSkips();
        assertTrue(skips != null && skips.stream().anyMatch(s -> s.contains("w:br")),
                "mixed-run br recorded in skips: " + skips);
    }
}
