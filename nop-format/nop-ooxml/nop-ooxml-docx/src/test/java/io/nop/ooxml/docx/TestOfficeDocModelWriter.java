package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.office.model.constants.OfficeHorizontalAlignment;
import io.nop.ooxml.docx.output.OfficeDocXmlBuilder;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOfficeDocModelWriter extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private final OfficeDocXmlBuilder builder = new OfficeDocXmlBuilder();

    // ---- 1. testBuildSimpleParagraph ----

    @Test
    public void testBuildSimpleParagraph() {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");

        OfficeRunModel run = new OfficeRunModel();
        run.setId("p0-r0");
        run.setT("Hello World");
        para.addR(run);

        XNode node = builder.buildParagraph(para);
        assertEquals("w:p", node.getTagName());
        assertEquals(1, node.getChildCount());

        XNode rNode = node.childByTag("w:r");
        assertNotNull(rNode);
        XNode tNode = rNode.childByTag("w:t");
        assertNotNull(tNode);
        assertEquals("Hello World", tNode.contentText());
    }

    // ---- 2. testBuildParagraphWithAlignment ----

    @Test
    public void testBuildParagraphWithAlignment() {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");

        WordParagraphStyle style = new WordParagraphStyle();
        style.setAlign(OfficeHorizontalAlignment.CENTER);
        para.setStyle(style);

        OfficeRunModel run = new OfficeRunModel();
        run.setId("p0-r0");
        run.setT("Centered");
        para.addR(run);

        XNode node = builder.buildParagraph(para);
        XNode pPr = node.childByTag("w:pPr");
        assertNotNull(pPr);
        XNode jc = pPr.childByTag("w:jc");
        assertNotNull(jc);
        assertEquals("center", jc.attrText("w:val"));
    }

    // ---- 3. testBuildParagraphWithSpacing ----

    @Test
    public void testBuildParagraphWithSpacing() {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");

        WordParagraphStyle style = new WordParagraphStyle();
        style.setSpaceBefore(12.0);
        style.setSpaceAfter(6.0);
        para.setStyle(style);

        XNode node = builder.buildParagraph(para);
        XNode pPr = node.childByTag("w:pPr");
        assertNotNull(pPr);
        XNode spacing = pPr.childByTag("w:spacing");
        assertNotNull(spacing);
        assertEquals(240, spacing.attrInt("w:before").intValue());
        assertEquals(120, spacing.attrInt("w:after").intValue());
    }

    // ---- 4. testBuildRunWithBold ----

    @Test
    public void testBuildRunWithBold() {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r0");
        run.setT("Bold text");

        WordRunStyle style = new WordRunStyle();
        OfficeFont font = new OfficeFont();
        font.setBold(true);
        style.setFont(font);
        run.setStyle(style);

        XNode node = builder.buildRun(run);
        XNode rPr = node.childByTag("w:rPr");
        assertNotNull(rPr);
        assertNotNull(rPr.childByTag("w:b"));
    }

    // ---- 5. testBuildRunWithFont ----

    @Test
    public void testBuildRunWithFont() {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r0");
        run.setT("Styled");

        WordRunStyle style = new WordRunStyle();
        OfficeFont font = new OfficeFont();
        font.setFontSize(14f);
        font.setFontName("Arial");
        font.setFontColor("FF0000");
        style.setFont(font);
        run.setStyle(style);

        XNode node = builder.buildRun(run);
        XNode rPr = node.childByTag("w:rPr");
        assertNotNull(rPr);

        XNode sz = rPr.childByTag("w:sz");
        assertNotNull(sz);
        assertEquals(28, sz.attrInt("w:val").intValue());

        XNode rFonts = rPr.childByTag("w:rFonts");
        assertNotNull(rFonts);
        assertEquals("Arial", rFonts.attrText("w:ascii"));
        assertEquals("Arial", rFonts.attrText("w:eastAsia"));

        XNode color = rPr.childByTag("w:color");
        assertNotNull(color);
        assertEquals("FF0000", color.attrText("w:val"));
    }

    // ---- 6. testBuildRunWithUnderline ----

    @Test
    public void testBuildRunWithUnderline() {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r0");
        run.setT("Underlined");

        WordRunStyle style = new WordRunStyle();
        OfficeFont font = new OfficeFont();
        font.setUnderlineStyle(OfficeFontUnderline.SINGLE);
        style.setFont(font);
        run.setStyle(style);

        XNode node = builder.buildRun(run);
        XNode rPr = node.childByTag("w:rPr");
        XNode u = rPr.childByTag("w:u");
        assertNotNull(u);
        assertEquals("single", u.attrText("w:val"));
    }

    // ---- 7. testBuildRunWithHighlight ----

    @Test
    public void testBuildRunWithHighlight() {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r0");
        run.setT("Highlighted");

        WordRunStyle style = new WordRunStyle();
        style.setHighlightColor("yellow");
        run.setStyle(style);

        XNode node = builder.buildRun(run);
        XNode rPr = node.childByTag("w:rPr");
        XNode highlight = rPr.childByTag("w:highlight");
        assertNotNull(highlight);
        assertEquals("yellow", highlight.attrText("w:val"));
    }

    // ---- 8. testBuildTable2x2 ----

    @Test
    public void testBuildTable2x2() {
        WordTable table = new WordTable();
        table.setId("tbl1");

        WordTableCell c00 = new WordTableCell();
        c00.setValue("A1");
        table.setCell(0, 0, c00);

        WordTableCell c01 = new WordTableCell();
        c01.setValue("B1");
        table.setCell(0, 1, c01);

        WordTableCell c10 = new WordTableCell();
        c10.setValue("A2");
        table.setCell(1, 0, c10);

        WordTableCell c11 = new WordTableCell();
        c11.setValue("B2");
        table.setCell(1, 1, c11);

        XNode node = builder.buildTable(table);
        assertEquals("w:tbl", node.getTagName());

        List<XNode> rows = node.childrenByTag("w:tr");
        assertEquals(2, rows.size());

        List<XNode> firstRowCells = rows.get(0).childrenByTag("w:tc");
        assertEquals(2, firstRowCells.size());

        assertCellText(firstRowCells.get(0), "A1");
        assertCellText(firstRowCells.get(1), "B1");

        List<XNode> secondRowCells = rows.get(1).childrenByTag("w:tc");
        assertEquals(2, secondRowCells.size());
        assertCellText(secondRowCells.get(0), "A2");
        assertCellText(secondRowCells.get(1), "B2");
    }

    // ---- 9. testBuildTableWithHorizontalMerge ----

    @Test
    public void testBuildTableWithHorizontalMerge() {
        WordTable table = new WordTable();
        table.setId("tbl1");

        WordTableCell c00 = new WordTableCell();
        c00.setValue("Merged");
        c00.setMergeAcross(1);
        table.setCell(0, 0, c00);

        XNode node = builder.buildTable(table);
        XNode tr = node.childByTag("w:tr");
        XNode tc = tr.childByTag("w:tc");
        XNode tcPr = tc.childByTag("w:tcPr");
        assertNotNull(tcPr);

        XNode gridSpan = tcPr.childByTag("w:gridSpan");
        assertNotNull(gridSpan);
        assertEquals(2, gridSpan.attrInt("w:val").intValue());
    }

    // ---- 10. testBuildTableWithVerticalMerge ----

    @Test
    public void testBuildTableWithVerticalMerge() {
        WordTable table = new WordTable();
        table.setId("tbl1");

        WordTableCell c00 = new WordTableCell();
        c00.setValue("Top");
        c00.setMergeDown(1);
        table.setCell(0, 0, c00);

        XNode node = builder.buildTable(table);
        List<XNode> rows = node.childrenByTag("w:tr");
        assertEquals(2, rows.size());

        // First row: vMerge restart
        XNode firstRowTc = rows.get(0).childByTag("w:tc");
        XNode firstTcPr = firstRowTc.childByTag("w:tcPr");
        assertNotNull(firstTcPr);
        XNode vMerge = firstTcPr.childByTag("w:vMerge");
        assertNotNull(vMerge);
        assertEquals("restart", vMerge.attrText("w:val"));

        // Second row: vMerge continuation (proxy cell)
        XNode secondRowTc = rows.get(1).childByTag("w:tc");
        XNode secondTcPr = secondRowTc.childByTag("w:tcPr");
        assertNotNull(secondTcPr);
        XNode contMerge = secondTcPr.childByTag("w:vMerge");
        assertNotNull(contMerge);
        assertNull(contMerge.attrText("w:val"));
    }

    // ---- 11. testBuildTableColumnWidths ----

    @Test
    public void testBuildTableColumnWidths() {
        WordTable table = new WordTable();
        table.setId("tbl1");

        List<WordTableColumnConfig> cols = new ArrayList<>();
        WordTableColumnConfig col1 = new WordTableColumnConfig();
        col1.setWidth(72.0);
        cols.add(col1);

        WordTableColumnConfig col2 = new WordTableColumnConfig();
        col2.setWidth(36.0);
        cols.add(col2);
        table.setCols(cols);

        WordTableCell c = new WordTableCell();
        c.setValue("X");
        table.setCell(0, 0, c);
        table.setCell(0, 1, new WordTableCell());

        assertEquals(2, table.getCols().size());

        XNode node = builder.buildTable(table);
        XNode tblGrid = node.childByTag("w:tblGrid");
        assertNotNull(tblGrid);

        List<XNode> gridCols = tblGrid.childrenByTag("w:gridCol");
        assertEquals(2, gridCols.size());
        assertEquals(1440, gridCols.get(0).attrInt("w:w").intValue());
        assertEquals(720, gridCols.get(1).attrInt("w:w").intValue());
    }

    // ---- 12. testBuildPageSettings ----

    @Test
    public void testBuildPageSettings() {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setWidth(595.0);
        doc.setHeight(842.0);

        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("page1");
        page.setOrientation("portrait");
        page.setBody(Collections.emptyList());
        page.setHeader(Collections.emptyList());
        page.setFooter(Collections.emptyList());
        doc.addPage(page);

        XNode documentNode = builder.buildDocumentXml(doc);
        XNode body = documentNode.childByTag("w:body");
        XNode sectPr = body.childByTag("w:sectPr");
        assertNotNull(sectPr);

        XNode pgSz = sectPr.childByTag("w:pgSz");
        assertNotNull(pgSz);
        assertEquals(11900, pgSz.attrInt("w:w").intValue());
        assertEquals(16840, pgSz.attrInt("w:h").intValue());
        assertEquals("portrait", pgSz.attrText("w:orient"));
    }

    // ---- 13. testRoundTripFromParser ----

    @Test
    public void testRoundTripFromParser() throws Exception {
        OfficeDocModel original = new OfficeDocModel();
        original.setWidth(595.0);
        original.setHeight(842.0);

        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("page1");
        page.setOrientation("portrait");
        page.setBody(new ArrayList<>());
        page.setHeader(Collections.emptyList());
        page.setFooter(Collections.emptyList());

        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");
        OfficeRunModel run = new OfficeRunModel();
        run.setId("p0-r0");
        run.setT("Hello World");
        para.addR(run);
        page.getBody().add(para);

        WordTable table = new WordTable();
        table.setId("tbl1");
        WordTableColumnConfig col1 = new WordTableColumnConfig();
        col1.setWidth(72.0);
        table.getCols().add(col1);
        WordTableColumnConfig col2 = new WordTableColumnConfig();
        col2.setWidth(72.0);
        table.getCols().add(col2);
        WordTableCell cell1 = new WordTableCell();
        cell1.setValue("A1");
        table.setCell(0, 0, cell1);
        WordTableCell cell2 = new WordTableCell();
        cell2.setValue("B1");
        table.setCell(0, 1, cell2);
        page.getBody().add(table);

        original.addPage(page);

        File tempFile = File.createTempFile("round-trip-", ".docx");
        try {
            tempFile.deleteOnExit();
            IResource outResource = new FileResource(tempFile);

            OfficeDocModelWriter writer = new OfficeDocModelWriter();
            writer.writeToResource(original, outResource);

            OfficeDocModel reparsed = new OfficeDocModelParser().parseFromResource(outResource);
            assertDataEquals(original, reparsed);
        } finally {
            tempFile.delete();
        }
    }

    // ---- 14. testBuildEmptyParagraph ----

    @Test
    public void testBuildEmptyParagraph() {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");

        XNode node = builder.buildParagraph(para);
        assertEquals("w:p", node.getTagName());
        assertNull(node.childByTag("w:pPr"));
        assertEquals(0, node.childrenByTag("w:r").size());
    }

    // ---- 15. testBuildNullStyleSkipped ----

    @Test
    public void testBuildNullStyleSkipped() {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setId("p0");

        OfficeRunModel run = new OfficeRunModel();
        run.setId("p0-r0");
        run.setT("No style");
        para.addR(run);

        XNode pNode = builder.buildParagraph(para);
        assertNull(pNode.childByTag("w:pPr"));

        XNode rNode = pNode.childByTag("w:r");
        assertNull(rNode.childByTag("w:rPr"));
        assertEquals("No style", rNode.childByTag("w:t").contentText());
    }

    // ---- helpers ---------------------------------------------------------

    private void assertCellText(XNode tcNode, String expected) {
        XNode p = tcNode.childByTag("w:p");
        XNode r = p.childByTag("w:r");
        if (r == null) {
            if (expected == null)
                return;
            throw new AssertionError("Expected '" + expected + "' but cell has no run");
        }
        XNode t = r.childByTag("w:t");
        assertEquals(expected, t != null ? t.contentText() : null);
    }

    static void assertDataEquals(OfficeDocModel expected, OfficeDocModel actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), 0.1, "width mismatch");
        assertEquals(expected.getHeight(), actual.getHeight(), 0.1, "height mismatch");
        assertEquals(expected.getPages().size(), actual.getPages().size(), "page count mismatch");

        for (int pageIndex = 0; pageIndex < expected.getPages().size(); pageIndex++) {
            OfficeDocPageModel expPage = expected.getPages().get(pageIndex);
            OfficeDocPageModel actPage = actual.getPages().get(pageIndex);

            assertEquals(expPage.getBody().size(), actPage.getBody().size(),
                    "body block count mismatch on page " + pageIndex);

            for (int blockIndex = 0; blockIndex < expPage.getBody().size(); blockIndex++) {
                OfficeBlock expBlock = expPage.getBody().get(blockIndex);
                OfficeBlock actBlock = actPage.getBody().get(blockIndex);

                if (expBlock instanceof OfficeParagraphModel && actBlock instanceof OfficeParagraphModel) {
                    assertParagraphEquals((OfficeParagraphModel) expBlock, (OfficeParagraphModel) actBlock);
                } else if (expBlock instanceof WordTable && actBlock instanceof WordTable) {
                    assertTableEquals((WordTable) expBlock, (WordTable) actBlock);
                }
            }
        }
    }

    static void assertParagraphEquals(OfficeParagraphModel expected, OfficeParagraphModel actual) {
        String expText = concatenateRunTexts(expected);
        String actText = concatenateRunTexts(actual);
        assertEquals(expText, actText, "paragraph text mismatch");
    }

    static String concatenateRunTexts(OfficeParagraphModel para) {
        if (para.getRuns() == null || para.getRuns().isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (OfficeRunModel run : para.getRuns()) {
            if (run.getT() != null) {
                sb.append(run.getT());
            }
        }
        return sb.toString();
    }

    static void assertTableEquals(WordTable expected, WordTable actual) {
        int expRowCount = expected.getRows() != null ? expected.getRows().size() : 0;
        int actRowCount = actual.getRows() != null ? actual.getRows().size() : 0;
        assertEquals(expRowCount, actRowCount, "table row count mismatch");

        for (int r = 0; r < expRowCount; r++) {
            WordTableRow expRow = expected.getRows().get(r);
            WordTableRow actRow = actual.getRows().get(r);

            int expCellCount = expRow.getCells() != null ? expRow.getCells().size() : 0;
            int actCellCount = actRow.getCells() != null ? actRow.getCells().size() : 0;

            for (int c = 0; c < Math.min(expCellCount, actCellCount); c++) {
                WordTableCell expCell = expRow.getCells().get(c);
                WordTableCell actCell = actRow.getCells().get(c);

                if (!expCell.isProxyCell() && !actCell.isProxyCell()) {
                    assertEquals(expCell.getText(), actCell.getText(),
                            "cell text mismatch at row=" + r + " col=" + c);
                    assertEquals(expCell.getMergeAcross(), actCell.getMergeAcross(),
                            "mergeAcross mismatch at row=" + r + " col=" + c);
                    assertEquals(expCell.getMergeDown(), actCell.getMergeDown(),
                            "mergeDown mismatch at row=" + r + " col=" + c);
                }
            }
        }

        // Compare column widths
        int expColCount = expected.getCols() != null ? expected.getCols().size() : 0;
        int actColCount = actual.getCols() != null ? actual.getCols().size() : 0;
        assertEquals(expColCount, actColCount, "table column count mismatch");

        for (int c = 0; c < expColCount; c++) {
            WordTableColumnConfig expCol = expected.getCols().get(c);
            WordTableColumnConfig actCol = actual.getCols().get(c);
            if (expCol.getWidth() != null && actCol.getWidth() != null) {
                assertEquals(expCol.getWidth(), actCol.getWidth(), 0.5,
                        "column width mismatch at col=" + c);
            }
        }
    }
}
