package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.collections.KeyedList;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 Phase 3 (plan 2524): module-local test of the PDF outlet
 * {@link WordDocPdfTemplates}. The outlet accepts an already expanded
 * {@link OfficeDocModel} only (decision U1 / dependency reality: the expander
 * lives in nop-report-docx and a compile edge back is a Maven reactor cycle);
 * the dynamic expand -&gt; renderPdf chain is verified end to end in
 * nop-report-docx (TestWordDocTemplates.testExpandedModelFeedsPdfOutlet).
 */
public class TestWordDocPdfTemplates extends JunitBaseTestCase {

    /** A too-tall explicit row height triggers the named truncation warning. */
    private static final double OVERSIZED_ROW_HEIGHT = 2000.0;

    @Test
    public void testRenderStaticModelToPdf() throws Exception {
        WordDocPdfOutput out = WordDocPdfTemplates.renderPdf(staticDoc());

        assertTrue(out.getPdfBytes().length > 4, "pdf must not be empty");
        assertEquals("%PDF", new String(out.getPdfBytes(), 0, 4, StandardCharsets.US_ASCII),
                "pdf magic");
        assertTrue(out.getWarnings().isEmpty(), "no degradations expected: " + out.getWarnings());
        try (PDDocument document = Loader.loadPDF(out.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Quarterly"), "static paragraph text must appear: " + text);
            assertTrue(text.contains("Total") && text.contains("42"),
                    "table content must appear: " + text);
        }
    }

    /**
     * The result carries the renderer's named degradations: a single table row
     * taller than the page content height is placed clipped with a warning
     * (layout contract, plan R2) - the outlet must surface it.
     */
    @Test
    public void testWarningsAreCarriedOnTheResult() throws Exception {
        OfficeDocModel docModel = staticDoc();
        WordTable table = table("Total", "42");
        table.getRow(0).setHeight(OVERSIZED_ROW_HEIGHT);
        addBody(docModel, table);

        WordDocPdfOutput out = WordDocPdfTemplates.renderPdf(docModel);
        assertEquals(1, out.getWarnings().size(), "the truncation warning must be carried: "
                + out.getWarnings());
        assertTrue(out.getWarnings().get(0).contains("doc-table-row-truncated"),
                "named degradation code must appear: " + out.getWarnings());
        assertTrue(out.getPdfBytes().length > 4);
        try (PDDocument document = Loader.loadPDF(out.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Total"), "clipped row must still paint: " + text);
        }
    }

    @Test
    public void testNullModelFailsFast() {
        NopException e = assertThrows(NopException.class,
                () -> WordDocPdfTemplates.renderPdf(null));
        assertEquals("nop.err.report.pdf.doc.render-model-required", e.getErrorCode());
    }

    /**
     * Pins the documented boundary of the model-only entry: a model that still
     * carries unexpanded template semantics is rendered in its template form -
     * callers must expand through the docx outlet model stage first.
     */
    @Test
    public void testUnexpandedTemplateModelRendersTemplateForm() throws Exception {
        OfficeDocModel docModel = staticDoc();
        WordTable template = table("name", "*=^data!amount");
        template.makeModel().setRenderType("xpt");
        addBody(docModel, template);

        WordDocPdfOutput out = WordDocPdfTemplates.renderPdf(docModel);
        try (PDDocument document = Loader.loadPDF(out.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("*=^data!amount"),
                    "without expansion the marker text is rendered as-is: " + text);
        }
    }

    // ------------------------------------------------------------------ builders

    private static OfficeDocModel staticDoc() {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setPages(new KeyedList<>(p -> p.getName()));
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("page1");
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72.0);
        margins.setBottom(72.0);
        margins.setLeft(72.0);
        margins.setRight(72.0);
        page.setMargins(margins);
        page.setBody(new ArrayList<>());
        page.setHeader(new ArrayList<>());
        page.setFooter(new ArrayList<>());
        docModel.getPages().add(page);
        addBody(docModel, paragraph("Quarterly"), table("Total", "42"));
        return docModel;
    }

    private static void addBody(OfficeDocModel docModel, OfficeBlock... blocks) {
        docModel.getPages().get(0).getBody().addAll(Arrays.asList(blocks));
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

    private static WordTable table(String left, String right) {
        WordTable table = new WordTable();
        table.setId("t1");
        table.getCols().add(col(150.0));
        table.getCols().add(col(150.0));
        WordTableRow row = new WordTableRow();
        row.getCells().add(cell(left));
        row.getCells().add(cell(right));
        table.getRows().add(row);
        return table;
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
}
