package io.nop.report.docx.expand;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.report.core.XptErrors;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableRow;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.report.pdf.doc.OfficeDocPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 Phase 2/3: OfficeDocModelExpander 单测（模型直构，见 plan 2522）。
 */
public class TestOfficeDocModelExpander extends JunitBaseTestCase {
    static int seq = 0;

    // ---- model builders ------------------------------------------------

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
        p.setName("page" + (++seq));
        p.setMargins(defaultMargins());
        p.setBody(new ArrayList<>());
        p.setHeader(new ArrayList<>());
        p.setFooter(new ArrayList<>());
        for (OfficeBlock block : blocks)
            p.getBody().add(block);
        return p;
    }

    private static OfficePageMargins defaultMargins() {
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72.0);
        margins.setBottom(72.0);
        margins.setLeft(72.0);
        margins.setRight(72.0);
        return margins;
    }

    private static OfficeParagraphModel paragraph(String text) {
        OfficeRunModel run = new OfficeRunModel();
        run.setId("r" + (++seq));
        run.setT(text);
        OfficeParagraphModel p = new OfficeParagraphModel();
        p.setId("p" + (++seq));
        p.addRun(run);
        return p;
    }

    private static String text(OfficeParagraphModel p) {
        return ((OfficeRunModel) p.getRuns().get(0)).getT();
    }

    private static WordTableCell cell(String value) {
        WordTableCell c = new WordTableCell();
        c.setId("c" + (++seq));
        c.setValue(value);
        return c;
    }

    private static WordTableColumnConfig col(double width) {
        WordTableColumnConfig col = new WordTableColumnConfig();
        col.setWidth(width);
        return col;
    }

    private static List<Map<String, Object>> data() {
        return Arrays.asList(
                Map.of("name", "a", "amount", 100),
                Map.of("name", "b", "amount", 200),
                Map.of("name", "c", "amount", 300));
    }

    private static List<Map<String, Object>> groupData() {
        return Arrays.asList(
                Map.of("dept", "x", "amount", 100),
                Map.of("dept", "x", "amount", 200),
                Map.of("dept", "y", "amount", 300));
    }

    // ---- vertical expansion --------------------------------------------

    @Test
    public void testVerticalExpand() {
        WordTable table = verticalTable();
        OfficeDocModel docModel = doc(table, paragraph("after"));
        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel, Map.of("data", data()));

        WordTable expanded = (WordTable) out.getPages().get(0).getBody().get(0);
        assertEquals(4, expanded.getRowCount(), "header row + 3 data rows");
        assertEquals("name", expanded.getRow(0).getCell(0).getText());
        assertEquals("a", expanded.getRow(1).getCell(0).getText());
        assertEquals("100", expanded.getRow(1).getCell(1).getText());
        assertEquals("c", expanded.getRow(3).getCell(0).getText());
        assertEquals("300", expanded.getRow(3).getCell(1).getText());

        // E4: expanded table keeps its template model (renderType="xpt")
        assertNotNull(expanded.getModel());
        assertEquals("xpt", expanded.getModel().getRenderType());

        // E4: input model untouched - template table still has the marker text
        assertEquals("*=^data!name", table.getRow(1).getCell(0).getText());
        assertEquals(2, table.getRowCount());

        // non-table blocks preserved after the expanded table
        OfficeParagraphModel after = (OfficeParagraphModel) out.getPages().get(0).getBody().get(1);
        assertEquals("after", text(after));
    }

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

    // ---- horizontal expansion ------------------------------------------

    @Test
    public void testHorizontalExpand() {
        WordTable table = new WordTable();
        table.setId("t-horizontal");
        table.getCols().add(col(80.0));
        table.getCols().add(col(80.0));

        WordTableRow labelRow = new WordTableRow();
        labelRow.getCells().add(cell("field"));
        labelRow.getCells().add(cell("*=>data!name"));
        table.getRows().add(labelRow);
        table.makeModel().setRenderType("xpt");

        OfficeDocModel out = new OfficeDocModelExpander().expand(doc(table), Map.of("data", data()));
        WordTable expanded = (WordTable) out.getPages().get(0).getBody().get(0);
        assertEquals(1, expanded.getRowCount(), "horizontal expansion adds columns, not rows");
        assertEquals(4, expanded.getColCount(), "label col + 3 data cols");
        assertEquals("field", expanded.getRow(0).getCell(0).getText());
        assertEquals("a", expanded.getRow(0).getCell(1).getText());
        assertEquals("c", expanded.getRow(0).getCell(3).getText());
    }

    // ---- group (@) expansion -------------------------------------------

    @Test
    public void testGroupExpand() {
        WordTable table = new WordTable();
        table.setId("t-group");
        table.getCols().add(col(80.0));
        table.getCols().add(col(80.0));

        WordTableRow header = new WordTableRow();
        header.getCells().add(cell("dept"));
        header.getCells().add(cell("amount"));
        table.getRows().add(header);

        WordTableRow groupRow = new WordTableRow();
        // @ 分组聚合：group 单元格按 dept 分组展开（标记语法见 XptModelInitializer.parseCellExpr）
        groupRow.getCells().add(cell("*=^data!dept@xptRt.ds(\"data\").group(\"dept\")"));
        groupRow.getCells().add(cell("*=^data!amount"));
        table.getRows().add(groupRow);
        table.makeModel().setRenderType("xpt");

        OfficeDocModel out = new OfficeDocModelExpander().expand(doc(table), Map.of("data", groupData()));
        WordTable expanded = (WordTable) out.getPages().get(0).getBody().get(0);

        // group cell value stays on the group start cell (xpt engine semantics);
        // member rows carry the amount values, one row per data item
        assertEquals(5, expanded.getRowCount());
        assertEquals("dept", expanded.getRow(0).getCell(0).getText());
        assertEquals("amount", expanded.getRow(0).getCell(1).getText());
        assertEquals("x", expanded.getRow(1).getCell(0).getText());
        assertEquals("100", expanded.getRow(1).getCell(1).getText());
        assertEquals("y", expanded.getRow(4).getCell(0).getText());
        assertEquals("300", expanded.getRow(4).getCell(1).getText());
        List<String> amountTexts = new ArrayList<>();
        for (int r = 0; r < expanded.getRowCount(); r++) {
            io.nop.core.model.table.ICell amountCell = expanded.getRow(r).getCell(1);
            if (amountCell != null && amountCell.getText() != null)
                amountTexts.add(amountCell.getText());
        }
        assertTrue(amountTexts.containsAll(Arrays.asList("100", "200", "300")),
                "all group member amounts must appear: " + amountTexts);
    }

    // ---- conditional filters -------------------------------------------

    @Test
    public void testTestExprFilter() {
        OfficeParagraphModel kept = paragraph("kept");
        OfficeParagraphModel removed = paragraph("removed");
        removed.makeModel().setTestExpr((IEvalPredicate) ctx -> false);

        WordTable plain = plainTable();
        WordTable filteredTable = verticalTable();
        filteredTable.makeModel().setTestExpr((IEvalPredicate) ctx -> false);

        OfficeDocModel out = new OfficeDocModelExpander()
                .expand(doc(kept, removed, plain, verticalTable(), filteredTable), Map.of("data", data()));

        List<OfficeBlock> body = out.getPages().get(0).getBody();
        // filteredTable excluded, removed paragraph excluded, plain table kept as-is,
        // xpt table expanded to 4 rows
        assertEquals(3, body.size());
        assertEquals("kept", text((OfficeParagraphModel) body.get(0)));
        WordTable expandedPlain = (WordTable) body.get(1);
        assertEquals("*=^data!name", expandedPlain.getRow(0).getCell(0).getText(),
                "plain table must not be expanded (E5)");
        assertEquals(1, expandedPlain.getRowCount());
        assertEquals(4, ((WordTable) body.get(2)).getRowCount());
    }

    private static WordTable plainTable() {
        WordTable table = new WordTable();
        table.setId("t-plain");
        table.getCols().add(col(80.0));
        WordTableRow row = new WordTableRow();
        row.getCells().add(cell("*=^data!name"));
        table.getRows().add(row);
        return table;
    }

    // ---- doc / page / block loops --------------------------------------

    @Test
    public void testDocLoop() {
        OfficeDocModel docModel = doc(paragraph("body"), paragraph("body2"));
        docModel.makeModel().setLoopItemsName("pages");
        docModel.makeModel().setLoopVarName("pageVar");
        docModel.makeModel().setLoopIndexName("pageIndex");

        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel,
                Map.of("pages", Arrays.asList(Map.of("n", 1), Map.of("n", 2))));
        assertEquals(2, out.getPages().size(), "doc loop replicates all pages");
        assertEquals(2, out.getPages().get(0).getBody().size());
        assertEquals(2, out.getPages().get(1).getBody().size());
    }

    @Test
    public void testDocTestExprFalse() {
        OfficeDocModel docModel = doc(paragraph("body"));
        docModel.makeModel().setTestExpr((IEvalPredicate) ctx -> false);
        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel, Map.of());
        assertEquals(0, out.getPages().size(), "doc-level testExpr false filters whole doc");
    }

    @Test
    public void testPageLoop() {
        OfficeDocModel docModel = doc(paragraph("p1"));
        OfficeDocPageModel second = page(paragraph("p2"));
        docModel.getPages().add(second);

        docModel.getPages().get(0).makeModel().setLoopItemsName("sections");
        docModel.getPages().get(0).makeModel().setLoopVarName("sec");
        docModel.getPages().get(0).makeModel().setLoopIndexName("secIndex");

        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel,
                Map.of("sections", Arrays.asList("s1", "s2")));
        // page 0 loops into 2 copies, page 1 rendered once
        assertEquals(3, out.getPages().size());
        assertEquals(1, out.getPages().get(0).getBody().size());
        assertEquals(1, out.getPages().get(2).getBody().size());
    }

    @Test
    public void testPageTestExprFalse() {
        OfficeDocModel docModel = doc(paragraph("keep"));
        OfficeDocPageModel dropped = page(paragraph("drop"));
        dropped.makeModel().setTestExpr((IEvalPredicate) ctx -> false);
        docModel.getPages().add(dropped);

        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel, Map.of());
        assertEquals(1, out.getPages().size());
        assertEquals("keep", text((OfficeParagraphModel) out.getPages().get(0).getBody().get(0)));
    }

    @Test
    public void testBlockLoop() {
        OfficeParagraphModel item = paragraph("item");
        item.makeModel().setLoopItemsName("rows");
        item.makeModel().setLoopVarName("row");
        item.makeModel().setLoopIndexName("rowIndex");

        OfficeDocModel out = new OfficeDocModelExpander().expand(doc(item),
                Map.of("rows", Arrays.asList("r1", "r2", "r3")));
        List<OfficeBlock> body = out.getPages().get(0).getBody();
        assertEquals(3, body.size(), "block loop repeats the whole block");
        for (OfficeBlock block : body) {
            assertEquals("item", text((OfficeParagraphModel) block));
        }
    }

    /**
     * The loop variable must be visible to block-model expressions evaluated inside the
     * loop: a run valueExpr referencing ${row} resolves per-iteration value.
     */
    @Test
    public void testBlockLoopVarVisibleToModelExpressions() {
        OfficeParagraphModel item = paragraph("item");
        item.makeModel().setLoopItemsName("rows");
        item.makeModel().setLoopVarName("row");
        // run templateExpr "${row}" compiled against a compile scope; emulate the parsed
        // form by compiling a tiny xpl template the same way the parser does
        OfficeRunModel run = (OfficeRunModel) item.getRuns().get(0);
        run.makeModel().setValueExpr(compileValue("${row}"));

        OfficeDocModel out = new OfficeDocModelExpander().expand(doc(item),
                Map.of("rows", Arrays.asList("r1", "r2")));
        List<OfficeBlock> body = out.getPages().get(0).getBody();
        assertEquals(2, body.size());
        assertEquals("r1", text((OfficeParagraphModel) body.get(0)), "iteration 1 sees row=r1");
        assertEquals("r2", text((OfficeParagraphModel) body.get(1)), "iteration 2 sees row=r2");
    }

    // ---- fail fast (E5) -------------------------------------------------

    @Test
    public void testMissingDatasetFailsFast() {
        WordTable table = new WordTable();
        table.setId("t-missing");
        table.getCols().add(col(80.0));
        WordTableRow row = new WordTableRow();
        row.getCells().add(cell("*=^missingDs!field"));
        table.getRows().add(row);
        table.makeModel().setRenderType("xpt");

        // scope has no missingDs variable - makeDataSet must fail fast
        io.nop.api.core.exceptions.NopException ex = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> new OfficeDocModelExpander().expand(doc(table), Map.of("data", data())));
        assertEquals(XptErrors.ERR_XPT_MISSING_VAR_DS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testInvalidCellExprFailsFast() {
        WordTable table = new WordTable();
        table.setId("t-invalid");
        table.getCols().add(col(80.0));
        WordTableRow row = new WordTableRow();
        row.getCells().add(cell("*=^data!!bad field"));
        table.getRows().add(row);
        table.makeModel().setRenderType("xpt");

        assertThrows(Exception.class,
                () -> new OfficeDocModelExpander().expand(doc(table), Map.of("data", data())));
    }

    // ---- exit wiring 1: expanded model -> docx -> reparse ----------------

    @Test
    public void testExpandToDocxRoundTrip() throws Exception {
        OfficeDocModel out = new OfficeDocModelExpander()
                .expand(doc(verticalTable(), paragraph("tail")), Map.of("data", data()));

        IResource target = getTargetResource("wi7-expand-result.docx");
        new OfficeDocModelWriter().writeToResource(out, target);

        OfficeDocModel reparsed = new OfficeDocModelParser().parseFromResource(target);
        assertEquals(1, reparsed.getPages().size());
        List<OfficeBlock> body = reparsed.getPages().get(0).getBody();
        assertEquals(2, body.size());
        WordTable table = (WordTable) body.get(0);
        assertEquals(4, table.getRowCount());
        assertEquals("a", table.getRow(1).getCell(0).getText());
        assertEquals("300", table.getRow(3).getCell(1).getText());
        assertEquals("tail", text((OfficeParagraphModel) body.get(1)));
    }

    // ---- exit wiring 2: expanded model -> PDF ---------------------------

    @Test
    public void testExpandToPdf() throws Exception {
        OfficeDocModel out = new OfficeDocModelExpander()
                .expand(doc(verticalTable()), Map.of("data", data()));

        byte[] pdf = new OfficeDocPdfRenderer().render(out);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("a"), "expanded data must appear in PDF: " + text);
            assertTrue(text.contains("300"), "expanded data must appear in PDF: " + text);
            assertTrue(text.contains("name"), "header row must appear in PDF: " + text);
        }
    }

    // ---- input preservation ---------------------------------------------

    @Test
    public void testExpandDoesNotMutateInput() {
        OfficeDocModel docModel = doc(verticalTable());
        OfficeDocModel out = new OfficeDocModelExpander().expand(docModel, Map.of("data", data()));

        assertTrue(out != docModel);
        assertEquals(1, docModel.getPages().size());
        assertEquals(2, ((WordTable) docModel.getPages().get(0).getBody().get(0)).getRowCount());
        assertEquals("*=^data!name",
                ((WordTable) docModel.getPages().get(0).getBody().get(0)).getRow(1).getCell(0).getText());
    }


    private static IEvalAction compileValue(String source) {
        return XLangCompileToolTestHelper.compileTemplateExpr(source);
    }

    /** minimal bridge so tests do not build a full compile scope */
    static final class XLangCompileToolTestHelper {
        static io.nop.core.lang.eval.IEvalAction compileTemplateExpr(String source) {
            io.nop.xlang.api.XLangCompileTool tool = io.nop.xlang.api.XLang.newCompileTool()
                    .allowUnregisteredScopeVar(true);
            return tool.compileTemplateExpr(null, source);
        }
    }
}
