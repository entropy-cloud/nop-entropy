package io.nop.report.docx;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.office.doc.model.NopOfficeDocModelException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeCodeBindingModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeImageTemplateModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.report.pdf.doc.WordDocPdfOutput;
import io.nop.report.pdf.doc.WordDocPdfTemplates;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 Phase 3 (plan 2524): unified outlet compile chain.
 *
 * <ul>
 * <li>dynamic input (model entry, and docx-bytes entry with real template
 * annotations) through {@link WordDocTemplates#generate}, with the generated
 * docx re-parsed and the expansion asserted end to end;</li>
 * <li>U3: static input uses the same entry, skips expand/bind and is
 * byte-idempotent;</li>
 * <li>U2: parse skips are carried on the result; the legacy
 * {@code WordTemplateParser} chain stays alive as the xpl escape hatch through
 * {@link WordDocTemplates#generateViaLegacyTemplate};</li>
 * <li>the model stage feeds the PDF outlet
 * ({@code io.nop.report.pdf.doc.WordDocPdfTemplates}) with expanded data.</li>
 * </ul>
 *
 * <p>Note on the docx-bytes entry: template semantics are compiled from docx
 * annotations at parse time and are not written back by
 * {@code OfficeDocModelWriter} (owner doc, generation contract), so a dynamic
 * docx cannot be obtained by writing a programmatic template model. The
 * bytes-entry sample is therefore assembled with the real annotation forms
 * (xpl: marker hyperlinks, xpt:table comment).</p>
 */
public class TestWordDocTemplates extends JunitBaseTestCase {

    /** Fixed zip entry time for the programmatic annotated template package. */
    private static final long FIXED_ZIP_TIME = 1767225600000L;

    private static Map<String, Object> dataScope() {
        return Map.of("data", Arrays.asList(
                Map.of("name", "a", "amount", 100),
                Map.of("name", "b", "amount", 200)));
    }

    // ------------------------------------------------------------------ model entry (dynamic)

    /**
     * Programmatic dynamic model (xpt table + c:for paragraph with run value
     * expression + code128 image binding) -> generate -> output docx re-parsed:
     * expansion, loop materialization and image binding must all be present.
     */
    @Test
    public void testDynamicModelGenerateToDocxEndToEnd() {
        OfficeDocModel model = doc(verticalTable(), loopParagraph(), boundImage());
        Map<String, Object> scope = Map.of(
                "data", dataScope().get("data"),
                "rows", Arrays.asList("r1", "r2"));

        WordDocOutput out = WordDocTemplates.generate(model, scope);
        assertEquals(1, out.getExpandedTableCount(), "one xpt table expanded");
        assertEquals(1, out.getBoundImageCount(), "one image bound");
        assertTrue(out.getParseSkips().isEmpty(), "model entry has no parse skips");

        // E4: the input template table keeps its marker rows
        WordTable templateTable = (WordTable) model.getPages().get(0).getBody().get(0);
        assertEquals(2, templateTable.getRowCount());
        assertEquals("*=^data!name", templateTable.getRow(1).getCell(0).getText());

        OfficeDocModel reparsed = parse(out.getDocxBytes());
        assertEquals(1, reparsed.getPages().size());
        List<OfficeBlock> body = reparsed.getPages().get(0).getBody();
        assertEquals(4, body.size(), "expanded table + 2 loop copies + bound image");

        WordTable table = (WordTable) body.get(0);
        assertEquals(3, table.getRowCount(), "header + 2 data rows");
        assertEquals("name", table.getRow(0).getCell(0).getText());
        assertEquals("a", table.getRow(1).getCell(0).getText());
        assertEquals("100", table.getRow(1).getCell(1).getText());
        assertEquals("b", table.getRow(2).getCell(0).getText());
        assertEquals("200", table.getRow(2).getCell(1).getText());

        assertEquals("r1", text((OfficeParagraphModel) body.get(1)),
                "loop copy 1 must carry the evaluated run value");
        assertEquals("r2", text((OfficeParagraphModel) body.get(2)),
                "loop copy 2 must carry the evaluated run value");

        OfficeImageModel image = (OfficeImageModel) body.get(3);
        assertTrue(image.getAssetRef().contains("__gen-img-"),
                "image must reference the generated asset: " + image.getAssetRef());
        OfficeImageAsset asset = findAsset(reparsed, "__gen-img-");
        assertNotNull(asset, "generated image asset must be written into the docx");
        byte[] png = asset.getData().toByteArray();
        assertEquals(0x89, png[0] & 0xFF, "generated asset must be a PNG");
        assertEquals(0x50, png[1] & 0xFF, "generated asset must be a PNG");
    }

    /**
     * The model stage result feeds the PDF outlet with the expanded data
     * (wiring proof for the two-outlet chain, decision U1).
     */
    @Test
    public void testExpandedModelFeedsPdfOutlet() throws Exception {
        WordDocModelOutput stage = WordDocTemplates.expandToModel(
                doc(verticalTable(), paragraph("Summary")), dataScope());
        assertEquals(1, stage.getExpandedTableCount());

        WordDocPdfOutput pdf = WordDocPdfTemplates.renderPdf(stage.getModel());
        assertNotNull(pdf.getWarnings(), "warnings list must always be present");
        try (PDDocument document = Loader.loadPDF(pdf.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Summary"), "static paragraph must appear: " + text);
            assertTrue(text.contains("100") && text.contains("200"),
                    "expanded table data must appear: " + text);
            assertFalse(text.contains("*=^data"), "template markers must be expanded away: " + text);
        }
    }

    // ------------------------------------------------------------------ bytes entry (dynamic annotations)

    /**
     * Template annotated docx (c:for paragraph markers, xpt:table comment, one
     * unknown inline element) -> generate(byte[]) -> output re-parsed: loop and
     * xpt expansion from the docx entry, and the parse skip is carried on the
     * result without blocking generation (U2).
     */
    @Test
    public void testDynamicDocxBytesGenerateEndToEndWithSkips() {
        byte[] templateDocx = annotatedTemplateDocx();
        Map<String, Object> scope = Map.of(
                "data", dataScope().get("data"),
                "rows", Arrays.asList(1, 2, 3));

        WordDocOutput out = WordDocTemplates.generate(templateDocx, scope);
        assertEquals(1, out.getExpandedTableCount());
        assertEquals(0, out.getBoundImageCount(), "no image bindings in the annotated template");
        assertEquals(List.of("unknown inline element skipped: w:weirdInline"), out.getParseSkips(),
                "the parse skip must be carried on the result");

        OfficeDocModel reparsed = parse(out.getDocxBytes());
        List<OfficeBlock> body = reparsed.getPages().get(0).getBody();
        assertEquals(5, body.size(), "3 loop copies + residual paragraph + expanded table");

        for (int i = 0; i < 3; i++) {
            assertEquals("Repeated", text((OfficeParagraphModel) body.get(i)),
                    "c:for must repeat the block, copy " + (i + 1));
        }
        WordTable table = (WordTable) body.get(4);
        assertEquals(3, table.getRowCount(), "header + 2 data rows");
        assertEquals("Name", table.getRow(0).getCell(0).getText());
        assertEquals("a", table.getRow(1).getCell(0).getText());
        assertEquals("100", table.getRow(1).getCell(1).getText());
        assertEquals("b", table.getRow(2).getCell(0).getText());
        assertEquals("200", table.getRow(2).getCell(1).getText());

        // same input through the model stage: identical metadata
        WordDocModelOutput stage = WordDocTemplates.expandToModel(templateDocx, scope);
        assertEquals(out.getParseSkips(), stage.getParseSkips());
        assertEquals(1, stage.getExpandedTableCount());
    }

    // ------------------------------------------------------------------ U3: static input

    /**
     * Static input (no template semantics) uses the same entry, skips expand and
     * bind, and generation is idempotent: repeated calls produce byte-identical
     * docx, on both the model and the docx-bytes entry.
     */
    @Test
    public void testStaticInputGenerateIsIdempotent() {
        OfficeDocModel model = doc(paragraph("hello"), plainTable());
        assertFalse(WordDocTemplates.hasTemplateSemantics(model), "static model must be detected");

        WordDocOutput first = WordDocTemplates.generate(model, null);
        WordDocOutput second = WordDocTemplates.generate(model, null);
        assertSame(model, first.getModel(), "static input is used as-is (no expansion, no copy)");
        assertArrayEquals(first.getDocxBytes(), second.getDocxBytes(),
                "static generation must be byte-idempotent");
        // Two calls milliseconds apart share one 2-second DOS timestamp window, so
        // byte equality alone would also hold with wall-clock entry times. Pin the
        // determinism mechanism itself: every entry carries the fixed time.
        assertZipEntryTimesAreFixed(first.getDocxBytes());
        assertEquals(0, first.getExpandedTableCount());
        assertEquals(0, first.getBoundImageCount());

        OfficeDocModel reparsed = parse(first.getDocxBytes());
        assertEquals(1, reparsed.getPages().size());
        List<OfficeBlock> body = reparsed.getPages().get(0).getBody();
        assertEquals(2, body.size());
        assertEquals("hello", text((OfficeParagraphModel) body.get(0)));
        assertEquals("static", ((WordTable) body.get(1)).getRow(0).getCell(0).getText());

        // bytes entry: the parser always attaches a doc-level generation shell
        // (importLibs etc.); it must not be misread as template semantics
        byte[] staticDocx = first.getDocxBytes();
        OfficeDocModel parsedStatic = parse(staticDocx);
        assertNotNull(parsedStatic.getModel(),
                "premise: the parser attaches a doc.model shell even to a static docx");
        assertFalse(WordDocTemplates.hasTemplateSemantics(parsedStatic),
                "a parsed static docx must stay static despite the parser's doc.model shell");
        WordDocOutput fromBytes = WordDocTemplates.generate(staticDocx, null);
        assertTrue(fromBytes.getParseSkips().isEmpty(), "no skips: " + fromBytes.getParseSkips());
        assertEquals(0, fromBytes.getExpandedTableCount());
        assertArrayEquals(fromBytes.getDocxBytes(),
                WordDocTemplates.generate(staticDocx, null).getDocxBytes(),
                "static generation from docx bytes must be byte-idempotent");
    }

    // ------------------------------------------------------------------ U3 detection matrix

    @Test
    public void testTemplateSemanticsDetection() {
        assertFalse(WordDocTemplates.hasTemplateSemantics(doc(paragraph("p"))), "static baseline");

        assertTrue(WordDocTemplates.hasTemplateSemantics(doc(verticalTable())), "xpt renderType");

        OfficeDocModel docLoop = doc(paragraph("p"));
        docLoop.makeModel().setLoopItemsName("pages");
        assertTrue(WordDocTemplates.hasTemplateSemantics(docLoop), "doc-level loop");

        OfficeDocModel docFilter = doc(paragraph("p"));
        docFilter.makeModel().setTestExpr((IEvalPredicate) ctx -> true);
        assertTrue(WordDocTemplates.hasTemplateSemantics(docFilter), "doc-level testExpr");

        OfficeDocModel pageFilter = doc(paragraph("p"));
        pageFilter.getPages().get(0).makeModel().setTestExpr((IEvalPredicate) ctx -> true);
        assertTrue(WordDocTemplates.hasTemplateSemantics(pageFilter), "page testExpr");

        OfficeParagraphModel looped = paragraph("item");
        looped.makeModel().setLoopItemsName("rows");
        assertTrue(WordDocTemplates.hasTemplateSemantics(doc(looped)), "paragraph loop");

        OfficeParagraphModel withValue = paragraph("item");
        ((OfficeRunModel) withValue.getRuns().get(0)).makeModel().setValueExpr(compileValue("${row}"));
        assertTrue(WordDocTemplates.hasTemplateSemantics(doc(withValue)), "run valueExpr");

        assertTrue(WordDocTemplates.hasTemplateSemantics(doc(boundImage())), "image binding");

        WordTable rowFiltered = plainTable();
        rowFiltered.getRow(0).makeModel().setTestExpr((IEvalPredicate) ctx -> true);
        assertTrue(WordDocTemplates.hasTemplateSemantics(doc(rowFiltered)), "table row testExpr");

        // header / footer sequences are walked as well
        OfficeDocModel headerTemplated = doc(paragraph("p"));
        headerTemplated.getPages().get(0).getHeader().add(looped);
        assertTrue(WordDocTemplates.hasTemplateSemantics(headerTemplated), "header block loop");

        // a doc.model generation shell (what the parser always attaches) is not semantics
        OfficeDocModel shellOnly = doc(paragraph("p"));
        shellOnly.makeModel().setDump(true);
        shellOnly.makeModel().setImportLibs(List.of("lib"));
        assertFalse(WordDocTemplates.hasTemplateSemantics(shellOnly), "doc.model shell only");
    }

    // ------------------------------------------------------------------ U2: legacy escape hatch

    /**
     * The legacy chain stays alive as the xpl escape hatch: a real template docx
     * using a complete xpl tag ({@code <docx-gen:r-br value="${text}"/>}, a form
     * the model chain cannot express) generates through
     * {@link WordDocTemplates#generateViaLegacyTemplate} with the tag evaluated
     * (not echoed) and the scope value rendered.
     */
    @Test
    public void testGenerateViaLegacyTemplateEscapeHatch() throws Exception {
        byte[] templateDocx = ResourceHelper.readBytes(classpathResource("docx/test-br.docx"));
        Path output = getTargetFile("wi9-legacy-result.docx").toPath();
        Files.deleteIfExists(output);

        WordDocTemplates.generateViaLegacyTemplate(templateDocx,
                Map.of("text", "abc\r\ndata"), output);

        assertTrue(Files.exists(output), "output file must exist");
        assertTrue(Files.size(output) > 0, "output file must not be empty");

        OfficePackage pkg = new OfficePackage();
        try {
            pkg.loadFromFile(output.toFile());
            String xml = pkg.getFile("word/document.xml").buildXml(null).xml();
            assertFalse(xml.contains("docx-gen:r-br"), "xpl tag must be evaluated, not echoed: " + xml);
            assertTrue(xml.contains("abc"), "scope value must be rendered: " + xml);
            assertTrue(xml.contains("data"), "scope value must be rendered: " + xml);
            assertTrue(xml.contains("<w:br"), "r-br must turn the line break into w:br: " + xml);
        } finally {
            pkg.close();
        }
    }

    // ------------------------------------------------------------------ fail fast

    @Test
    public void testInvalidInputFailsFast() {
        byte[] validDocx = WordDocTemplates.generate(doc(paragraph("p")), null).getDocxBytes();
        Path output = getTargetFile("wi9-legacy-invalid.docx").toPath();

        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.generate((byte[]) null, null));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.generate(new byte[0], null));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.generate((OfficeDocModel) null, null));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.expandToModel((byte[]) null, null));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.expandToModel((OfficeDocModel) null, null));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.generateViaLegacyTemplate(null, null, output));
        assertThrows(NopOfficeDocModelException.class,
                () -> WordDocTemplates.generateViaLegacyTemplate(validDocx, null, null));
    }

    // ------------------------------------------------------------------ model builders

    private static OfficeDocModel doc(OfficeBlock... blocks) {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setPages(new KeyedList<>(p -> p.getName()));
        docModel.getPages().add(page(blocks));
        return docModel;
    }

    private static OfficeDocPageModel page(OfficeBlock... blocks) {
        OfficeDocPageModel p = new OfficeDocPageModel();
        p.setName("page1");
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72.0);
        margins.setBottom(72.0);
        margins.setLeft(72.0);
        margins.setRight(72.0);
        p.setMargins(margins);
        p.setBody(new ArrayList<>());
        p.setHeader(new ArrayList<>());
        p.setFooter(new ArrayList<>());
        p.getBody().addAll(Arrays.asList(blocks));
        return p;
    }

    private static OfficeParagraphModel paragraph(String text) {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r");
        run.setT(text);
        OfficeParagraphModel p = new OfficeParagraphModel();
        p.setId("p");
        p.addRun(run);
        return p;
    }

    private static String text(OfficeParagraphModel p) {
        return ((OfficeRunModel) p.getRuns().get(0)).getT();
    }

    /** xpt vertical template table: header row + one marker data row. */
    private static WordTable verticalTable() {
        WordTable table = new WordTable();
        table.setId("t-vertical");
        table.getCols().add(col(100.0));
        table.getCols().add(col(100.0));

        WordTableRow header = new WordTableRow();
        header.getCells().add(cell("name"));
        header.getCells().add(cell("amount"));
        table.getRows().add(header);

        WordTableRow dataRow = new WordTableRow();
        dataRow.getCells().add(cell("*=^data!name"));
        dataRow.getCells().add(cell("*=^data!amount"));
        table.getRows().add(dataRow);
        table.makeModel().setRenderType("xpt");
        return table;
    }

    private static WordTable plainTable() {
        WordTable table = new WordTable();
        table.setId("t-plain");
        table.getCols().add(col(80.0));
        WordTableRow row = new WordTableRow();
        row.getCells().add(cell("static"));
        table.getRows().add(row);
        return table;
    }

    /** c:for paragraph whose run carries a value expression over the loop variable. */
    private static OfficeParagraphModel loopParagraph() {
        OfficeParagraphModel item = paragraph("item");
        item.makeModel().setLoopItemsName("rows");
        item.makeModel().setLoopVarName("row");
        ((OfficeRunModel) item.getRuns().get(0)).makeModel().setValueExpr(compileValue("${row}"));
        return item;
    }

    /** image block with a code128 binding (in-module rendering, no extra beans). */
    private static OfficeImageModel boundImage() {
        OfficeCodeBindingModel binding = new OfficeCodeBindingModel();
        binding.setCodeType("code128");
        binding.setCodeExpr((IEvalAction) ctx -> "ORDER-9");
        OfficeImageTemplateModel templateModel = new OfficeImageTemplateModel();
        templateModel.setCodeBinding(binding);

        OfficeImageModel image = new OfficeImageModel();
        image.setId("img1");
        image.setWidth(100.0);
        image.setHeight(50.0);
        image.setModel(templateModel);
        return image;
    }

    private static WordTableColumnConfig col(double width) {
        WordTableColumnConfig col = new WordTableColumnConfig();
        col.setWidth(width);
        return col;
    }

    private static WordTableCell cell(String value) {
        WordTableCell c = new WordTableCell();
        c.setId("c");
        c.setValue(value);
        return c;
    }

    private static IEvalAction compileValue(String source) {
        XLangCompileTool tool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        return tool.compileTemplateExpr(null, source);
    }

    // ------------------------------------------------------------------ helpers

    private static OfficeDocModel parse(byte[] bytes) {
        ByteArrayResource resource = new ByteArrayResource("/tmp/wi9-outlet/sample.docx", bytes, 0L);
        return new OfficeDocModelParser().parseFromResource(resource);
    }

    private static OfficeImageAsset findAsset(OfficeDocModel doc, String namePart) {
        if (doc.getAssets() == null)
            return null;
        for (OfficeImageAsset asset : doc.getAssets()) {
            if (asset.getName() != null && asset.getName().contains(namePart))
                return asset;
        }
        return null;
    }

    /**
     * Every zip entry of an outlet-generated docx must be stamped with the fixed
     * entry time rather than the wall clock (the U3 byte-determinism mechanism).
     */
    private static void assertZipEntryTimesAreFixed(byte[] docx) {
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertEquals(FIXED_ZIP_TIME, entry.getTime(),
                        "zip entry must carry the fixed time: " + entry.getName());
                entries++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read the generated docx", e);
        }
        assertTrue(entries > 0, "generated docx must contain zip entries");
    }

    // ------------------------------------------------------------------ annotated template docx assembly

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String CT_PREFIX = "application/vnd.openxmlformats-officedocument.wordprocessingml.";

    /**
     * Builds a minimal template-annotated docx package the way Word stores the
     * annotations: template markers as external {@code xpl:} hyperlink
     * relationships, the xpt:table marker as a comment on the first cell of the
     * first row. Body: p(c:for), p(unknown inline element), table(xpt). Same
     * annotation forms as {@code DocxCorpusBuilder.templateAnnotationDoc} (the
     * nop-ooxml-docx test corpus is not reachable from this module).
     */
    private static byte[] annotatedTemplateDocx() {
        String forBegin = "xpl:" + pct("<c:for var=\"row\" items=\"${rows}\">");
        String forEnd = "xpl:" + pct("</c:for>");

        String documentXml = XML_DECL
                + "<w:document xmlns:w=\"" + W_NS + "\" xmlns:r=\"" + R_NS + "\">"
                + "<w:body>"
                // 1. c:for paragraph: begin + end markers in one paragraph
                + "<w:p>" + tplLink("rIdForBeg", "for-begin") + run("Repeated")
                + tplLink("rIdForEnd", "for-end") + "</w:p>"
                // 2. unknown inline element -> named parse skip, paragraph survives empty
                + "<w:p><w:weirdInline/></w:p>"
                // 3. xpt:table template table (first row first cell carries the comment)
                + "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"3000\"/></w:tblGrid>"
                + "<w:tr>" + tc("<w:commentRangeStart w:id=\"0\"/>" + run("Name")
                + "<w:commentRangeEnd w:id=\"0\"/>" + commentRefRun("0"))
                + tc(run("Amount")) + "</w:tr>"
                + "<w:tr>" + tc(run("*=^data!name")) + tc(run("*=^data!amount")) + "</w:tr>"
                + "</w:tbl>"
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1440\" w:right=\"1800\" w:bottom=\"1440\" w:left=\"1800\"/>"
                + "</w:sectPr>"
                + "</w:body></w:document>";

        String stylesXml = XML_DECL + "<w:styles xmlns:w=\"" + W_NS + "\"/>";
        String commentsXml = XML_DECL + "<w:comments xmlns:w=\"" + W_NS + "\">"
                + "<w:comment w:id=\"0\" w:author=\"wi9\" w:initials=\"W\"><w:p>"
                + run("xpt:table") + "</w:p></w:comment></w:comments>";
        String documentRels = XML_DECL + "<Relationships xmlns=\"" + RELS_NS + "\">"
                + rel("rIdStyles", R_NS + "/styles", "styles.xml", null)
                + rel("rIdComments", R_NS + "/comments", "comments.xml", null)
                + rel("rIdForBeg", R_NS + "/hyperlink", forBegin, "External")
                + rel("rIdForEnd", R_NS + "/hyperlink", forEnd, "External")
                + "</Relationships>";
        String contentTypes = XML_DECL
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" "
                + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\""
                + CT_PREFIX + "document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"" + CT_PREFIX + "styles+xml\"/>"
                + "<Override PartName=\"/word/comments.xml\" ContentType=\"" + CT_PREFIX + "comments+xml\"/>"
                + "</Types>";
        String rootRels = XML_DECL + "<Relationships xmlns=\"" + RELS_NS + "\">"
                + rel("rId1", R_NS + "/officeDocument", "word/document.xml", null)
                + "</Relationships>";

        String[][] parts = {
                {"[Content_Types].xml", contentTypes},
                {"_rels/.rels", rootRels},
                {"word/document.xml", documentXml},
                {"word/styles.xml", stylesXml},
                {"word/_rels/document.xml.rels", documentRels},
                {"word/comments.xml", commentsXml},
        };

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            for (String[] part : parts) {
                ZipEntry entry = new ZipEntry(part[0]);
                entry.setTime(FIXED_ZIP_TIME);
                zip.putNextEntry(entry);
                zip.write(part[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to assemble the annotated template docx", e);
        }
        return bos.toByteArray();
    }

    /** Percent-encodes a template marker target the way Word stores it in document.xml.rels. */
    private static String pct(String source) {
        return source.replace("<", "%3c").replace(">", "%3e")
                .replace("\"", "%22").replace(" ", "%20")
                .replace("{", "%7b").replace("}", "%7d");
    }

    private static String run(String text) {
        return "<w:r><w:t xml:space=\"preserve\">" + text + "</w:t></w:r>";
    }

    private static String tc(String paragraphContent) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr><w:p>"
                + paragraphContent + "</w:p></w:tc>";
    }

    private static String tplLink(String relId, String label) {
        return "<w:hyperlink r:id=\"" + relId + "\" w:history=\"1\">" + run(label) + "</w:hyperlink>";
    }

    private static String commentRefRun(String id) {
        return "<w:r><w:rPr><w:rStyle w:val=\"CommentReference\"/></w:rPr>"
                + "<w:commentReference w:id=\"" + id + "\"/></w:r>";
    }

    private static String rel(String id, String type, String target, String targetMode) {
        return "<Relationship Id=\"" + id + "\" Type=\"" + type + "\" Target=\"" + target + "\""
                + (targetMode == null ? "" : " TargetMode=\"" + targetMode + "\"") + "/>";
    }
}
