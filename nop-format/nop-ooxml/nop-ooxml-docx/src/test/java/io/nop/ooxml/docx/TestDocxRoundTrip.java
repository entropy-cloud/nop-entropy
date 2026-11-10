package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.ooxml.docx.corpus.DocxCorpusBuilder;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.ooxml.docx.verify.DocxVisualVerifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 red-line acceptance: for every corpus sample,
 * {@code docx bytes -> OfficeDocModelParser -> OfficeDocModelWriter -> docx bytes
 * -> OfficeDocModelParser} must produce a semantically equivalent model (G2), compared
 * through typed per-block-type comparators — never byte equality.
 *
 * <p><b>Registered exemptions</b> (G2 exemption list; each item is also annotated at
 * the affected assertion site):</p>
 * <ul>
 * <li>watermark VML shape: the model does not carry raw VML XNode content; the
 * backgroundWatermarkDoc sample only asserts that header blocks survive with their
 * text, not the VML form.</li>
 * <li>comments: the model carries no block-level comment anchoring; comment markers
 * are parse-time annotations (xpt:table detection / skip records) and are not
 * regenerated.</li>
 * <li>template bindings (block.model / run.model exprs): generation writes static
 * content; dynamic template expansion is the WI7 consumer concern (plan Non-Goal:
 * renderType=xpt and template semantics are not regenerated).</li>
 * <li>named style font details: WordParagraphStyle has no font field, so the rPr of
 * named paragraph styles in styles.xml is not modeled and not compared.</li>
 * <li>background color precision: compared as an exact string round trip; hue
 * normalization differences of the rendering application are out of scope.</li>
 * <li>non-standalone inline drawings: the parser names them as skips and the model
 * has no carrier; not present in the corpus samples.</li>
 * </ul>
 */
public class TestDocxRoundTrip extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String SAMPLE_PATH = "/tmp/wi5-roundtrip/sample.docx";

    /** One corpus sample pushed through parse -> write -> parse. */
    private static class RoundTrip {
        final OfficeDocModel first;
        final OfficeDocModel second;
        final byte[] generatedDocx;

        RoundTrip(OfficeDocModel first, OfficeDocModel second, byte[] generatedDocx) {
            this.first = first;
            this.second = second;
            this.generatedDocx = generatedDocx;
        }
    }

    private static RoundTrip roundTrip(byte[] corpusDocx) throws Exception {
        OfficeDocModel first = parse(corpusDocx);

        File tempFile = File.createTempFile("wi5-roundtrip-", ".docx", new File("target").getAbsoluteFile());
        tempFile.deleteOnExit();
        try {
            OfficeDocModelWriter writer = new OfficeDocModelWriter();
            writer.writeToResource(first, new FileResource(tempFile));
            byte[] generated = Files.readAllBytes(tempFile.toPath());
            OfficeDocModel second = parse(generated);
            assertSemanticEquals(first, second);
            return new RoundTrip(first, second, generated);
        } finally {
            tempFile.delete();
        }
    }

    private static OfficeDocModel parse(byte[] bytes) {
        ByteArrayResource resource = new ByteArrayResource(SAMPLE_PATH, bytes, 0L);
        return new OfficeDocModelParser().parseFromResource(resource);
    }

    // ------------------------------------------------------------- corpus samples

    @Test
    public void testSimpleDocument() throws Exception {
        roundTrip(DocxCorpusBuilder.simpleDocument());
    }

    @Test
    public void testTitleAndListDoc() throws Exception {
        RoundTrip rt = roundTrip(DocxCorpusBuilder.titleAndListDoc());
        // capability wiring proof: title blocks + list numId/item levels survive
        assertEquals("title", blockType(rt.first.getPages().get(0).getBody().get(0)));
        OfficeListModel list = (OfficeListModel) rt.first.getPages().get(0).getBody().get(2);
        assertEquals("1", list.getNumId());
        assertEquals(2, list.getItems().size());
    }

    @Test
    public void testImageDoc() throws Exception {
        RoundTrip rt = roundTrip(DocxCorpusBuilder.imageDoc());
        // capability wiring proof: asset data survives byte-identically
        OfficeImageAsset asset = rt.first.getAsset("word/media/image1.png");
        assertNotNull(asset);
        assertTrue(Objects.equals(asset.getData(), rt.second.getAsset("word/media/image1.png").getData()),
                "image asset ByteString data must survive the round trip");
    }

    @Test
    public void testHyperlinkDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.hyperlinkDoc());
    }

    @Test
    public void testInlineTabBreakDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.inlineTabBreakDoc());
    }

    @Test
    public void testPageBreakAndSeparatorDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.pageBreakAndSeparatorDoc());
    }

    @Test
    public void testMultiSectionDoc() throws Exception {
        RoundTrip rt = roundTrip(DocxCorpusBuilder.multiSectionDoc());
        // capability wiring proof: two sections with per-section header/footer text
        assertEquals(2, rt.first.getPages().size());
        assertEquals("Header One", headerTextOf(rt.first.getPages().get(0)));
        assertEquals("Header Two", headerTextOf(rt.first.getPages().get(1)));
    }

    @Test
    public void testMergedTableDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.mergedTableDoc());
    }

    @Test
    public void testFullRunStyleDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.fullRunStyleDoc());
    }

    @Test
    public void testTitleLoopDoc() throws Exception {
        // exemption: the c:for template binding (title.model loop fields) is not
        // regenerated; only the static title content is compared (WI7 concern)
        roundTrip(DocxCorpusBuilder.titleLoopDoc());
    }

    @Test
    public void testMixedBreakDoc() throws Exception {
        // exemption: the w:br inside a text run cannot be dispatched in the model;
        // the run text (with the '\n' the parser produces for the break) is compared
        roundTrip(DocxCorpusBuilder.mixedBreakDoc());
    }

    @Test
    public void testPageSetupDoc() throws Exception {
        roundTrip(DocxCorpusBuilder.pageSetupDoc());
    }

    @Test
    public void testBackgroundWatermarkDoc() throws Exception {
        RoundTrip rt = roundTrip(DocxCorpusBuilder.backgroundWatermarkDoc());
        // capability wiring proof: background string survives
        assertEquals("EEF7FF", rt.first.getBackground());
        // exemption (watermark VML): the watermark text lives in a VML textpath
        // attribute, which the model does not carry; only the header block presence
        // is asserted (the header paragraph survives with an empty text run)
        assertTrue(rt.first.getPages().get(0).getHeader() != null
                && !rt.first.getPages().get(0).getHeader().isEmpty(),
                "watermark header must survive as a header block");
    }

    @Test
    public void testTemplateAnnotationDoc() throws Exception {
        // exemption (template bindings): paragraph.model / list.model / run.model
        // exprs and the xpt:table renderType marker are not regenerated; the static
        // block structure, texts and styles are compared
        roundTrip(DocxCorpusBuilder.templateAnnotationDoc());
    }

    @Test
    public void testDanglingCommentReferenceDoc() throws Exception {
        // exemption (comments): the dangling commentReference only produces a
        // parse-time skip record; the model carries plain cell text
        roundTrip(DocxCorpusBuilder.danglingCommentReferenceDoc());
    }

    /**
     * WI5 Phase 3 visual smoke: three writer-generated packages must at least be
     * openable by LibreOffice (convert to PDF). No golden comparison — the generated
     * package structure differs from the WI0 corpus fixtures by design.
     */
    @Test
    public void testGeneratedPackagesOpenInLibreOffice() throws Exception {
        Assumptions.assumeTrue(DocxVisualVerifier.isSofficeAvailable(),
                "LibreOffice soffice not available; visual smoke skipped");

        DocxVisualVerifier verifier = DocxVisualVerifier.forDefaultDir("wi5-roundtrip-smoke");
        List<byte[]> samples = new ArrayList<>();
        samples.add(roundTrip(DocxCorpusBuilder.simpleDocument()).generatedDocx);
        samples.add(roundTrip(DocxCorpusBuilder.multiSectionDoc()).generatedDocx);
        samples.add(roundTrip(DocxCorpusBuilder.mergedTableDoc()).generatedDocx);

        Path dir = Files.createTempDirectory("wi5-visual");
        for (int i = 0; i < samples.size(); i++) {
            Path docx = dir.resolve("sample" + (i + 1) + ".docx");
            Files.write(docx, samples.get(i));
            byte[] pdf = verifier.convertToPdf(docx);
            assertTrue(pdf.length > 0, "converted pdf must not be empty for sample " + (i + 1));
        }
    }

    // ------------------------------------------------------------- semantic equality

    private static void assertSemanticEquals(OfficeDocModel expected, OfficeDocModel actual) {
        currentExpectedDoc.set(expected);
        currentActualDoc.set(actual);
        try {
            doAssertSemanticEquals(expected, actual);
        } finally {
            currentExpectedDoc.remove();
            currentActualDoc.remove();
        }
    }

    private static void doAssertSemanticEquals(OfficeDocModel expected, OfficeDocModel actual) {
        String ctx = "doc";
        assertEquals(expected.getWidth(), actual.getWidth(), 0.01, ctx + " width");
        assertEquals(expected.getHeight(), actual.getHeight(), 0.01, ctx + " height");
        // background color precision exemption: exact string round trip
        assertEquals(norm(expected.getBackground()), norm(actual.getBackground()), "background");
        assertEquals(expected.getPages().size(), actual.getPages().size(), "page count");

        for (int i = 0; i < expected.getPages().size(); i++) {
            OfficeDocPageModel expPage = expected.getPages().get(i);
            OfficeDocPageModel actPage = actual.getPages().get(i);
            ctx = "page" + (i + 1);

            assertBlocksEquals(expPage.getBody(), actPage.getBody(), ctx + ".body");
            assertBlocksEquals(expPage.getHeader(), actPage.getHeader(), ctx + ".header");
            assertBlocksEquals(expPage.getFooter(), actPage.getFooter(), ctx + ".footer");

            assertNullableEquals(expPage.getWidth(), actPage.getWidth(), 0.01, ctx + ".width");
            assertNullableEquals(expPage.getHeight(), actPage.getHeight(), 0.01, ctx + ".height");
            assertEquals(norm(expPage.getOrientation()), norm(actPage.getOrientation()),
                    ctx + ".orientation");

            OfficePageMargins expMar = expPage.getMargins();
            OfficePageMargins actMar = actPage.getMargins();
            if (expMar == null) {
                assertTrue(actMar == null || isZeroMargins(actMar),
                        ctx + ".margins: expected none");
            } else {
                assertNotNull(actMar, ctx + ".margins missing");
                assertEquals(expMar.getTop(), actMar.getTop(), 0.01, ctx + ".margin.top");
                assertEquals(expMar.getRight(), actMar.getRight(), 0.01, ctx + ".margin.right");
                assertEquals(expMar.getBottom(), actMar.getBottom(), 0.01, ctx + ".margin.bottom");
                assertEquals(expMar.getLeft(), actMar.getLeft(), 0.01, ctx + ".margin.left");
            }
        }

        assertStylesEquals(expected, actual);
    }

    private static boolean isZeroMargins(OfficePageMargins margins) {
        return margins.getTop() == 0 && margins.getRight() == 0 && margins.getBottom() == 0
                && margins.getLeft() == 0;
    }

    private static void assertBlocksEquals(List<OfficeBlock> expected, List<OfficeBlock> actual,
                                           String ctx) {
        int expSize = expected == null ? 0 : expected.size();
        int actSize = actual == null ? 0 : actual.size();
        assertEquals(expSize, actSize, ctx + " block count");

        for (int i = 0; i < expSize; i++) {
            OfficeBlock exp = expected.get(i);
            OfficeBlock act = actual.get(i);
            String blockCtx = ctx + "[" + i + "]";

            assertEquals(blockType(exp), blockType(act), blockCtx + " block type");
            if (exp instanceof OfficeParagraphModel) {
                assertParagraphEquals((OfficeParagraphModel) exp, (OfficeParagraphModel) act, blockCtx);
            } else if (exp instanceof OfficeTitleModel) {
                OfficeTitleModel expTitle = (OfficeTitleModel) exp;
                OfficeTitleModel actTitle = (OfficeTitleModel) act;
                assertParagraphStyleEquals(expTitle.getStyle(), actTitle.getStyle(), blockCtx);
                assertRunsEquals(expTitle.getRuns(), actTitle.getRuns(), blockCtx);
            } else if (exp instanceof OfficeListModel) {
                assertListEquals((OfficeListModel) exp, (OfficeListModel) act, blockCtx);
            } else if (exp instanceof OfficeImageModel) {
                OfficeImageModel expImg = (OfficeImageModel) exp;
                OfficeImageModel actImg = (OfficeImageModel) act;
                assertEquals(expImg.getAssetRef(), actImg.getAssetRef(), blockCtx + ".assetRef");
                assertNullableEquals(expImg.getWidth(), actImg.getWidth(), 0.01, blockCtx + ".width");
                assertNullableEquals(expImg.getHeight(), actImg.getHeight(), 0.01, blockCtx + ".height");
            } else if (exp instanceof OfficeHyperlinkModel) {
                OfficeHyperlinkModel expHl = (OfficeHyperlinkModel) exp;
                OfficeHyperlinkModel actHl = (OfficeHyperlinkModel) act;
                assertEquals(norm(expHl.getHref()), norm(actHl.getHref()), blockCtx + ".href");
                assertEquals(norm(expHl.getAnchor()), norm(actHl.getAnchor()), blockCtx + ".anchor");
                assertRunsEquals(expHl.getRuns(), actHl.getRuns(), blockCtx);
            }
            // separator / pageBreak carry no payload beyond their type (asserted above)
            // table
            else if (exp instanceof WordTable) {
                assertTableEquals(currentExpectedDoc.get(), (WordTable) exp,
                        currentActualDoc.get(), (WordTable) act, blockCtx);
            }
        }
    }

    /**
     * Thread-local pass-through of the two models being compared so the table
     * comparator can dereference cell style ids in doc.styles.
     */
    private static final ThreadLocal<OfficeDocModel> currentExpectedDoc = new ThreadLocal<>();
    private static final ThreadLocal<OfficeDocModel> currentActualDoc = new ThreadLocal<>();

    private static void assertParagraphEquals(OfficeParagraphModel expected,
                                              OfficeParagraphModel actual, String ctx) {
        assertParagraphStyleEquals(expected.getStyle(), actual.getStyle(), ctx);
        assertRunsEquals(expected.getRuns(), actual.getRuns(), ctx);
    }

    private static void assertParagraphStyleEquals(WordParagraphStyle expected,
                                                   WordParagraphStyle actual, String ctx) {
        if (expected == null) {
            assertTrue(actual == null || isEmptyParagraphStyle(actual),
                    ctx + ".style: expected none, got " + actual);
            return;
        }
        assertNotNull(actual, ctx + ".style missing");
        assertEquals(norm(expected.getId()), norm(actual.getId()), ctx + ".style.id");
        if (expected.getAlign() != null) {
            assertEquals(expected.getAlign(), actual.getAlign(), ctx + ".style.align");
        }
        assertNullableEquals(expected.getSpaceBefore(), actual.getSpaceBefore(), 0.01,
                ctx + ".style.spaceBefore");
        assertNullableEquals(expected.getSpaceAfter(), actual.getSpaceAfter(), 0.01,
                ctx + ".style.spaceAfter");
        assertNullableEquals(expected.getLeftIndent(), actual.getLeftIndent(), 0.01,
                ctx + ".style.leftIndent");
        assertNullableEquals(expected.getRightIndent(), actual.getRightIndent(), 0.01,
                ctx + ".style.rightIndent");
        assertNullableEquals(expected.getFirstLineIndent(), actual.getFirstLineIndent(), 0.01,
                ctx + ".style.firstLineIndent");
        assertEquals(expected.getTitleLevel(), actual.getTitleLevel(), ctx + ".style.titleLevel");
    }

    private static boolean isEmptyParagraphStyle(WordParagraphStyle style) {
        return style.getAlign() == null && style.getSpaceBefore() == null
                && style.getSpaceAfter() == null && StringHelper.isEmpty(style.getId());
    }

    private static void assertRunsEquals(List<OfficeRun> expected, List<OfficeRun> actual,
                                         String ctx) {
        int expSize = expected == null ? 0 : expected.size();
        int actSize = actual == null ? 0 : actual.size();
        assertEquals(expSize, actSize, ctx + " run count");
        for (int i = 0; i < expSize; i++) {
            OfficeRun exp = expected.get(i);
            OfficeRun act = actual.get(i);
            String runCtx = ctx + ".run[" + i + "]";
            assertEquals(runType(exp), runType(act), runCtx + " run type");

            if (exp instanceof OfficeRunModel) {
                OfficeRunModel expRun = (OfficeRunModel) exp;
                OfficeRunModel actRun = (OfficeRunModel) act;
                assertEquals(norm(expRun.getT()), norm(actRun.getT()), runCtx + ".text");
                assertRunStyleEquals(expRun.getStyle(), actRun.getStyle(), runCtx);
            } else if (exp instanceof OfficeLinkModel) {
                OfficeLinkModel expLink = (OfficeLinkModel) exp;
                OfficeLinkModel actLink = (OfficeLinkModel) act;
                assertEquals(norm(expLink.getHref()), norm(actLink.getHref()), runCtx + ".href");
                assertEquals(norm(expLink.getAnchor()), norm(actLink.getAnchor()), runCtx + ".anchor");
                assertRunsEquals(expLink.getRuns(), actLink.getRuns(), runCtx);
            } else if (exp instanceof OfficeBreakModel) {
                assertEquals(norm(((OfficeBreakModel) exp).getBrType()),
                        norm(((OfficeBreakModel) act).getBrType()), runCtx + ".brType");
            }
            // OfficeTabModel carries no payload
        }
    }

    private static void assertRunStyleEquals(WordRunStyle expected, WordRunStyle actual,
                                             String ctx) {
        if (expected == null) {
            assertTrue(actual == null || isEmptyRunStyle(actual),
                    ctx + ".style: expected none, got " + actual);
            return;
        }
        assertNotNull(actual, ctx + ".style missing");
        assertEquals(norm(expected.getId()), norm(actual.getId()), ctx + ".style.id");
        assertEquals(norm(expected.getHighlightColor()), norm(actual.getHighlightColor()),
                ctx + ".style.highlight");

        OfficeFont expFont = expected.getFont();
        if (expFont == null) {
            assertTrue(actual.getFont() == null || isEmptyFont(actual.getFont()),
                    ctx + ".style.font: expected none");
            return;
        }
        OfficeFont actFont = actual.getFont();
        assertNotNull(actFont, ctx + ".style.font missing");
        assertEquals(expFont.isBold(), actFont.isBold(), ctx + ".font.bold");
        assertEquals(expFont.isItalic(), actFont.isItalic(), ctx + ".font.italic");
        assertEquals(expFont.isStrikeout(), actFont.isStrikeout(), ctx + ".font.strike");
        assertEquals(expFont.getUnderlineStyle(), actFont.getUnderlineStyle(),
                ctx + ".font.underline");
        assertEquals(expFont.getVerticalAlign(), actFont.getVerticalAlign(),
                ctx + ".font.vertAlign");
        assertNullableEquals(expFont.getFontSize(), actFont.getFontSize(), 0.01,
                ctx + ".font.size");
        assertEquals(norm(expFont.getFontColor()), norm(actFont.getFontColor()),
                ctx + ".font.color");
        assertEquals(norm(expFont.getFontName()), norm(actFont.getFontName()),
                ctx + ".font.name");
    }

    private static boolean isEmptyRunStyle(WordRunStyle style) {
        return style.getFont() == null && StringHelper.isEmpty(style.getHighlightColor())
                && StringHelper.isEmpty(style.getId());
    }

    private static boolean isEmptyFont(OfficeFont font) {
        return !font.isBold() && !font.isItalic() && !font.isStrikeout()
                && font.getUnderlineStyle() == null && font.getVerticalAlign() == null
                && font.getFontSize() == null && StringHelper.isEmpty(font.getFontColor())
                && StringHelper.isEmpty(font.getFontName());
    }

    private static void assertListEquals(OfficeListModel expected, OfficeListModel actual,
                                         String ctx) {
        assertEquals(norm(expected.getNumId()), norm(actual.getNumId()), ctx + ".numId");
        int expSize = expected.getItems() == null ? 0 : expected.getItems().size();
        int actSize = actual.getItems() == null ? 0 : actual.getItems().size();
        assertEquals(expSize, actSize, ctx + " item count");
        for (int i = 0; i < expSize; i++) {
            OfficeListItemModel expItem = expected.getItems().get(i);
            OfficeListItemModel actItem = actual.getItems().get(i);
            String itemCtx = ctx + ".item[" + i + "]";
            assertEquals(expItem.getLevel(), actItem.getLevel(), itemCtx + ".level");
            assertParagraphStyleEquals(expItem.getStyle(), actItem.getStyle(), itemCtx);
            assertRunsEquals(expItem.getRuns(), actItem.getRuns(), itemCtx);
        }
    }

    private static void assertTableEquals(OfficeDocModel expDoc, WordTable expected,
                                          OfficeDocModel actDoc, WordTable actual, String ctx) {
        int expRows = expected.getRows() == null ? 0 : expected.getRows().size();
        int actRows = actual.getRows() == null ? 0 : actual.getRows().size();
        assertEquals(expRows, actRows, ctx + " row count");

        for (int r = 0; r < Math.min(expRows, actRows); r++) {
            WordTableRow expRow = expected.getRows().get(r);
            WordTableRow actRow = actual.getRows().get(r);
            assertNullableEquals(expRow.getHeight(), actRow.getHeight(), 0.001,
                    ctx + ".row[" + r + "].height");

            // real cells only: continuation cells are model artifacts of the merge
            List<WordTableCell> expReal = realCells(expRow);
            List<WordTableCell> actReal = realCells(actRow);
            assertEquals(expReal.size(), actReal.size(),
                    ctx + ".row[" + r + "] real cell count");
            for (int c = 0; c < expReal.size(); c++) {
                WordTableCell expCell = expReal.get(c);
                WordTableCell actCell = actReal.get(c);
                String cellCtx = ctx + ".cell[r" + r + "c" + c + "]";
                assertEquals(norm(expCell.getValue()), norm(actCell.getValue()),
                        cellCtx + ".text");
                assertEquals(expCell.getMergeAcross(), actCell.getMergeAcross(),
                        cellCtx + ".mergeAcross");
                assertEquals(expCell.getMergeDown(), actCell.getMergeDown(),
                        cellCtx + ".mergeDown");
                assertCellStyleEquals(expDoc, expCell, actDoc, actCell, cellCtx);
            }
        }

        int expCols = expected.getCols() == null ? 0 : expected.getCols().size();
        int actCols = actual.getCols() == null ? 0 : actual.getCols().size();
        assertEquals(expCols, actCols, ctx + " column count");
        for (int c = 0; c < Math.min(expCols, actCols); c++) {
            WordTableColumnConfig expCol = expected.getCols().get(c);
            WordTableColumnConfig actCol = actual.getCols().get(c);
            assertNullableEquals(expCol.getWidth(), actCol.getWidth(), 0.01,
                    ctx + ".col[" + c + "].width");
        }
    }

    /**
     * Cell style comparison via dereferenced style values (G1): inline __cell_* styles
     * are generated back into tcPr and re-materialized on the second parse; the ids
     * are parse-scoped synthetics, so the referenced style FIELDS are what must match.
     */
    private static void assertCellStyleEquals(OfficeDocModel expDoc, WordTableCell expCell,
                                              OfficeDocModel actDoc, WordTableCell actCell,
                                              String ctx) {
        WordCellStyle expStyle = resolveCellStyle(expDoc, expCell);
        WordCellStyle actStyle = resolveCellStyle(actDoc, actCell);
        if (expStyle == null) {
            assertTrue(actStyle == null, ctx + ".cellStyle: expected none, got "
                    + (actStyle == null ? null : actStyle.getId()));
            return;
        }
        assertNotNull(actStyle, ctx + ".cellStyle missing (expected "
                + expStyle.getId() + ")");
        assertBorderEquals(expStyle.getTopBorder(), actStyle.getTopBorder(), ctx + ".border.top");
        assertBorderEquals(expStyle.getLeftBorder(), actStyle.getLeftBorder(), ctx + ".border.left");
        assertBorderEquals(expStyle.getBottomBorder(), actStyle.getBottomBorder(),
                ctx + ".border.bottom");
        assertBorderEquals(expStyle.getRightBorder(), actStyle.getRightBorder(),
                ctx + ".border.right");
        assertEquals(norm(expStyle.getBackgroundColor()), norm(actStyle.getBackgroundColor()),
                ctx + ".shading");
        if (expStyle.getVerticalAlign() != null) {
            assertEquals(expStyle.getVerticalAlign(), actStyle.getVerticalAlign(),
                    ctx + ".vAlign");
        }
    }

    /**
     * Resolves a cell's inline style (id __cell_*) in the owning doc.styles container.
     * Ids are parse-scoped synthetics (deterministic per parse), so both models use
     * the same id scheme; the comparison itself is field based.
     */
    private static WordCellStyle resolveCellStyle(OfficeDocModel doc, WordTableCell cell) {
        if (StringHelper.isEmpty(cell.getStyleId()) || doc.getStyles() == null) {
            return null;
        }
        OfficeStyle style = doc.getStyle(cell.getStyleId());
        return style instanceof WordCellStyle ? (WordCellStyle) style : null;
    }

    private static void assertBorderEquals(OfficeBorderStyle expected, OfficeBorderStyle actual,
                                           String ctx) {
        if (expected == null) {
            assertTrue(actual == null, ctx + ": expected no border, got " + actual);
            return;
        }
        assertNotNull(actual, ctx + " border missing");
        assertEquals(expected.getType(), actual.getType(), ctx + ".type");
        assertEquals(expected.getWeight(), actual.getWeight(), ctx + ".weight");
        assertEquals(norm(expected.getColor()), norm(actual.getColor()), ctx + ".color");
    }

    private static void assertStylesEquals(OfficeDocModel expected, OfficeDocModel actual) {
        if (expected.getStyles() == null) {
            return;
        }
        Map<String, OfficeStyle> actualStyles = new LinkedHashMap<>();
        if (actual.getStyles() != null) {
            for (OfficeStyle style : actual.getStyles()) {
                actualStyles.put(style.getId(), style);
            }
        }
        for (OfficeStyle expStyle : expected.getStyles()) {
            OfficeStyle actStyle = actualStyles.get(expStyle.getId());
            assertNotNull(actStyle, "style missing after round trip: " + expStyle.getId()
                    + " (exemption: named cell styles are not representable in styles.xml)");
            assertEquals(norm(getName(expStyle)), norm(getName(actStyle)),
                    "style name: " + expStyle.getId());
            assertEquals(norm(expStyle.getBasedOn()), norm(actStyle.getBasedOn()),
                    "style basedOn: " + expStyle.getId());
            assertEquals(expStyle.isDefault(), actStyle.isDefault(),
                    "style default flag: " + expStyle.getId());

            if (expStyle instanceof WordParagraphStyle) {
                assertParagraphStyleEquals((WordParagraphStyle) expStyle,
                        (WordParagraphStyle) actStyle, "style." + expStyle.getId());
            } else if (expStyle instanceof WordRunStyle) {
                assertRunStyleEquals(((WordRunStyle) expStyle), (WordRunStyle) actStyle,
                        "style." + expStyle.getId());
            } else if (expStyle instanceof WordListStyle) {
                WordListStyle expList = (WordListStyle) expStyle;
                WordListStyle actList = (WordListStyle) actStyle;
                // exemption (styles.xml representability): numFmt/lvlText/start have no
                // valid location inside a styles.xml w:style and are not regenerated
                assertNullableEquals(expList.getIndent(), actList.getIndent(), 0.01,
                        "style." + expStyle.getId() + ".indent");
            }
            // table styles: model carries id/name/basedOn/default only
        }
    }

    private static String getName(OfficeStyle style) {
        if (style instanceof WordParagraphStyle) {
            return ((WordParagraphStyle) style).getName();
        }
        if (style instanceof WordRunStyle) {
            return ((WordRunStyle) style).getName();
        }
        if (style instanceof WordListStyle) {
            return ((WordListStyle) style).getName();
        }
        return null;
    }

    // ------------------------------------------------------------- helpers

    private static List<WordTableCell> realCells(WordTableRow row) {
        List<WordTableCell> result = new ArrayList<>();
        if (row.getCells() != null) {
            for (WordTableCell cell : row.getCells()) {
                if (!cell.isProxyCell()) {
                    result.add(cell);
                }
            }
        }
        return result;
    }

    private static String headerTextOf(OfficeDocPageModel page) {
        StringBuilder sb = new StringBuilder();
        if (page.getHeader() != null) {
            for (OfficeBlock block : page.getHeader()) {
                if (block instanceof OfficeParagraphModel) {
                    for (OfficeRun run : ((OfficeParagraphModel) block).getRuns()) {
                        if (run instanceof OfficeRunModel && ((OfficeRunModel) run).getT() != null) {
                            sb.append(((OfficeRunModel) run).getT());
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String blockType(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) return "p";
        if (block instanceof OfficeTitleModel) return "title";
        if (block instanceof OfficeListModel) return "list";
        if (block instanceof OfficeImageModel) return "image";
        if (block instanceof OfficeHyperlinkModel) return "hyperlink";
        if (block instanceof OfficeSeparatorModel) return "separator";
        if (block instanceof OfficePageBreakModel) return "pageBreak";
        if (block instanceof WordTable) return "table";
        return block.getClass().getSimpleName();
    }

    private static String runType(OfficeRun run) {
        if (run instanceof OfficeRunModel) return "r";
        if (run instanceof OfficeTabModel) return "tab";
        if (run instanceof OfficeBreakModel) return "br";
        if (run instanceof OfficeLinkModel) return "link";
        return run.getClass().getSimpleName();
    }

    private static String norm(String value) {
        return value == null ? "" : value;
    }

    private static void assertNullableEquals(Number expected, Number actual, double delta,
                                             String message) {
        if (expected == null) {
            assertTrue(actual == null, message + ": expected null, got " + actual);
        } else {
            assertNotNull(actual, message + ": expected " + expected + ", got null");
            assertEquals(expected.doubleValue(), actual.doubleValue(), delta, message);
        }
    }

    /**
     * G1: the "__" style id prefix is reserved for parser-synthesized inline cell styles.
     * A user styles.xml claiming it must fail fast with the dedicated error code.
     */
    @Test
    public void testReservedStyleIdPrefixFailsFast() throws Exception {
        byte[] original = DocxCorpusBuilder.simpleDocument();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(original));
             java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if ("word/styles.xml".equals(entry.getName())) {
                    String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:style w:type=\"paragraph\" w:styleId=\"__user\"><w:name w:val=\"User\"/></w:style>"
                            + "</w:styles>";
                    zout.write(styles.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    zout.write(zin.readAllBytes());
                }
                zout.closeEntry();
            }
        }
        io.nop.core.resource.impl.ByteArrayResource resource =
                new io.nop.core.resource.impl.ByteArrayResource("/tmp/wi5-reserved/sample.docx", out.toByteArray(), 0L);
        io.nop.api.core.exceptions.NopException e = org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> new OfficeDocModelParser().parseFromResource(resource));
        org.junit.jupiter.api.Assertions.assertEquals(
                "nop.err.ooxml.docx.parse-reserved-style-id", e.getErrorCode());
    }

    /**
     * Regression: a named run style carrying a font made buildStyleNode detach children
     * while iterating (ConcurrentModificationException). The writer must emit the style
     * with its rPr intact.
     */
    @Test
    public void testNamedRunStyleWithFontWritesWithoutFailure() throws Exception {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setWidth(11906.0);
        doc.setHeight(16838.0);

        io.nop.office.model.WordRunStyle runStyle = new io.nop.office.model.WordRunStyle();
        runStyle.setId("fancy");
        runStyle.setName("Fancy");
        runStyle.setType("runStyle");
        io.nop.office.model.OfficeFont font = new io.nop.office.model.OfficeFont();
        font.setBold(true);
        font.setFontName("Calibri");
        font.setFontSize(12.0f);
        runStyle.setFont(font);
        doc.addStyle(runStyle);

        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("p");
        OfficeParagraphModel p = new OfficeParagraphModel();
        p.setId("p1");
        p.setType("p");
        io.nop.office.model.WordRunStyle inline = new io.nop.office.model.WordRunStyle();
        inline.setId("p1-r0");
        inline.setType("r");
        inline.setFont(font);
        p.addRun(new io.nop.office.doc.model.OfficeRunModel() {{
            setId("p1-r0");
            setType("r");
            setT("text");
            setStyle(inline);
        }});
        java.util.List<OfficeBlock> blocks = new java.util.ArrayList<>();
        blocks.add(p);
        page.setBody(blocks);
        doc.addPage(page);

        OfficeDocModelWriter writer = new OfficeDocModelWriter();
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("wi5-style-font", ".docx");
        writer.writeToResource(doc, new io.nop.core.resource.impl.FileResource(temp.toFile()));
        byte[] docx = java.nio.file.Files.readAllBytes(temp);
        java.nio.file.Files.deleteIfExists(temp);
        OfficeDocModel reparsed = parse(docx);
        OfficeStyle style = reparsed.getStyle("fancy");
        assertNotNull(style);
        io.nop.office.model.WordRunStyle reparsedRun = (io.nop.office.model.WordRunStyle) style;
        assertTrue(reparsedRun.getFont().isBold(), "named style font survives round-trip");
        assertEquals("Calibri", reparsedRun.getFont().getFontName());
    }
}
