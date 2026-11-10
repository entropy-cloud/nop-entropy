package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.bytes.ByteString;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.pdf.verify.RegionProbe;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 2b acceptance: table (text order, merge position, border stroke, shading,
 * row split across pages), image (pixel + size), separator, page break, list glyphs,
 * link annotations, multi-section geometry and headers.
 */
public class TestOfficeDocPdfRender2b extends JunitBaseTestCase {

    private static final int DPI = 110;
    private static final float SCALE = DPI / 72f;

    // ---- helpers -------------------------------------------------------------

    private static OfficeDocModel doc(OfficeBlock... blocks) {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setBackground("FFFFFF");
        docModel.setPages(new io.nop.commons.collections.KeyedList<>(p -> p.getName()));
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setMargins(defaultMargins());
        page.setBody(new java.util.ArrayList<>());
        page.setHeader(new java.util.ArrayList<>());
        page.setFooter(new java.util.ArrayList<>());
        for (OfficeBlock block : blocks)
            page.getBody().add(block);
        docModel.getPages().add(page);
        return docModel;
    }

    private static OfficePageMargins defaultMargins() {
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72);
        margins.setBottom(72);
        margins.setLeft(72);
        margins.setRight(72);
        margins.setHeader(36);
        margins.setFooter(36);
        return margins;
    }

    private static int runSeq;

    private static OfficeRunModel run(String text) {
        OfficeRunModel runModel = new OfficeRunModel();
        runModel.setId("r" + (++runSeq));
        runModel.setT(text);
        return runModel;
    }

    private static OfficeParagraphModel paragraph(OfficeRun... runs) {
        OfficeParagraphModel paragraph = new OfficeParagraphModel();
        for (OfficeRun runModel : runs)
            paragraph.addRun(runModel);
        return paragraph;
    }

    private static WordTableCell cell(String value, String styleId, int mergeAcross, int mergeDown) {
        WordTableCell tableCell = new WordTableCell();
        tableCell.setId("c" + (++runSeq));
        tableCell.setValue(value);
        tableCell.setStyleId(styleId);
        tableCell.setMergeAcross(mergeAcross);
        tableCell.setMergeDown(mergeDown);
        return tableCell;
    }

    private static byte[] redPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, 0xFF0000);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private BufferedImage pageImage(byte[] pdf, int pageIndex) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return io.nop.pdf.verify.PdfImageRenderer.renderPage(document, pageIndex, DPI);
        }
    }

    // ---- table ---------------------------------------------------------------

    private OfficeDocModel tableDoc() {
        WordCellStyle bordered = new WordCellStyle();
        bordered.setId("bordered");
        bordered.setTopBorder(newBorderStyle());
        bordered.setBottomBorder(newBorderStyle());
        bordered.setLeftBorder(newBorderStyle());
        bordered.setRightBorder(newBorderStyle());

        WordCellStyle shaded = new WordCellStyle();
        shaded.setId("shaded");
        shaded.setTopBorder(newBorderStyle());
        shaded.setBottomBorder(newBorderStyle());
        shaded.setLeftBorder(newBorderStyle());
        shaded.setRightBorder(newBorderStyle());
        shaded.setBackgroundColor("FFFF00");

        WordTable table = new WordTable();
        table.setId("t1");
        for (int c = 0; c < 3; c++) {
            WordTableColumnConfig col = new WordTableColumnConfig();
            // column config has no id
            col.setWidth(100.0);
            table.getCols().add(col);
        }
        String[][] values = {
                {"A1", "B1", "C1"},
                {"A2", "B2", "C2"},
                {"A3", "B3", "C3"}};
        for (int r = 0; r < 3; r++) {
            WordTableRow row = new WordTableRow();
            row.setId("row" + r);
            for (int c = 0; c < 3; c++)
                row.getCells().add(cell(values[r][c], r == 0 ? "shaded" : "bordered", 0, 0));
            table.getRows().add(row);
        }
        // merged cell: A4 spans two columns
        WordTableRow mergeRow = new WordTableRow();
        mergeRow.setId("rowMerge");
        mergeRow.getCells().add(cell("MERGED", "bordered", 1, 0));
        mergeRow.getCells().add(cell("RIGHT", "bordered", 0, 0));
        table.getRows().add(mergeRow);

        OfficeDocModel docModel = doc(table);
        docModel.addStyle(bordered);
        docModel.addStyle(shaded);
        return docModel;
    }

    private static io.nop.office.model.WordBorderStyle newBorderStyle() {
        io.nop.office.model.WordBorderStyle border = new io.nop.office.model.WordBorderStyle();
        border.setType(OfficeLineStyle.SINGLE);
        border.setWeight(1);
        border.setColor("000000");
        return border;
    }

    @Test
    public void testTableTextOrderAndMerge() throws Exception {
        byte[] pdf = new OfficeDocPdfRenderer().render(tableDoc());
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            int a1 = text.indexOf("A1");
            int c3 = text.indexOf("C3");
            int merged = text.indexOf("MERGED");
            assertTrue(a1 >= 0 && c3 >= 0 && merged >= 0);
            assertTrue(a1 < c3 && c3 < merged, "table reading order, got: " + text);
            assertEquals(1, document.getNumberOfPages());
        }
        // merged cell occupies the first two columns (x of MERGED < x of RIGHT)
        try (DocRenderSession session = new DocRenderSession(tableDoc())) {
            List<DocPage> pages = new DocLayoutEngine(session).layout();
            DocBox.Table box = (DocBox.Table) pages.get(0).getBody().get(0).getBox();
            DocBox.Table.TableRow row = box.getRows().get(3);
            // model columns are normalized to the content width: 451.3 / 3 per column
            float colW = 451.3f / 3;
            assertEquals(2 * colW, row.getCells().get(0).getWidth(), 0.2f); // spans col0+col1
            assertEquals(2, row.getCells().get(0).getColSpan());
            assertEquals(colW, row.getCells().get(1).getWidth(), 0.2f);
            assertEquals(2, row.getCells().get(1).getCol());
        }
    }

    @Test
    public void testTableBorderAndShadingProbes() throws Exception {
        byte[] pdf = new OfficeDocPdfRenderer().render(tableDoc());
        BufferedImage img = pageImage(pdf, 0);
        // table starts at margin 72pt; rows are >= MIN_ROW_HEIGHT; the top border sits
        // just below y=72 — probe a stroke crossing the first horizontal border line
        int probeX = Math.round(120 * SCALE);
        int stroke = RegionProbe.measureHorizontalStrokeWidth(img, probeX,
                Math.round(70 * SCALE), Math.round(78 * SCALE), Color.WHITE, 40);
        assertTrue(stroke >= 1, "table border must draw a horizontal stroke near the top");

        // shading: interior of the shaded first cell is yellow
        Color avg = RegionProbe.averageColor(img,
                Math.round(80 * SCALE), Math.round(78 * SCALE), 20, 8);
        assertTrue(avg.getRed() > 200 && avg.getGreen() > 200 && avg.getBlue() < 100,
                "cell shading must fill yellow, got " + avg);
    }

    @Test
    public void testTableSplitsAcrossPages() throws Exception {
        WordTable table = new WordTable();
        table.setId("big");
        for (int c = 0; c < 2; c++) {
            WordTableColumnConfig col = new WordTableColumnConfig();
            // column config has no id
            col.setWidth(200.0);
            table.getCols().add(col);
        }
        for (int r = 0; r < 60; r++) {
            WordTableRow row = new WordTableRow();
            row.setId("row" + r);
            row.getCells().add(cell("cell-r" + r + "-a", null, 0, 0));
            row.getCells().add(cell("cell-r" + r + "-b", null, 0, 0));
            table.getRows().add(row);
        }
        byte[] pdf = new OfficeDocPdfRenderer().render(doc(table));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1, "many-row table must split across pages");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String page2 = stripper.getText(document);
            assertTrue(page2.contains("cell-r59-a"), "last row must land on page 2: " + page2);
        }
    }

    @Test
    public void testOverTallRowTruncationWarning() throws Exception {
        WordTable table = new WordTable();
        table.setId("tall");
        WordTableColumnConfig col = new WordTableColumnConfig();
        // column config has no id
        col.setWidth(450.0);
        table.getCols().add(col);
        WordTableRow row = new WordTableRow();
        row.setId("row0");
        row.setHeight(3000.0);
        row.getCells().add(cell("tall", null, 0, 0));
        table.getRows().add(row);
        OfficeDocModel docModel = doc(table);
        OfficeDocPdfRenderer renderer = new OfficeDocPdfRenderer();
        byte[] pdf = renderer.render(docModel);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
        }
        assertTrue(renderer.getLastWarnings().stream().anyMatch(w -> w.startsWith("doc-table-row-truncated")),
                "over-tall row must produce a named warning: " + renderer.getLastWarnings());
    }

    // ---- image / separator / page break --------------------------------------

    @Test
    public void testImageRendersPixels() throws Exception {
        byte[] png = redPng(60, 40);
        OfficeDocModel docModel = doc(paragraph(run("before image")));
        docModel.setAssets(new io.nop.commons.collections.KeyedList<>(a -> a.getName()));
        OfficeImageAsset asset = new OfficeImageAsset();
        asset.setName("word/media/image1.png");
        asset.setExt("png");
        asset.setData(ByteString.of(png));
        docModel.getAssets().add(asset);

        OfficeImageModel image = new OfficeImageModel();
        image.setId("img");
        image.setAssetRef("word/media/image1.png");
        image.setWidth(120.0);
        image.setHeight(80.0);
        docModel.getPages().get(0).getBody().add(image);

        byte[] pdf = new OfficeDocPdfRenderer().render(docModel);
        BufferedImage img = pageImage(pdf, 0);
        // drawn at (72,72) 120x80pt; probe the interior
        Color avg = RegionProbe.averageColor(img,
                Math.round(90 * SCALE), Math.round(85 * SCALE), 30, 20);
        assertTrue(avg.getRed() > 200 && avg.getGreen() < 100 && avg.getBlue() < 100,
                "image area must be red, got " + avg);

        // missing asset fails fast
        image.setAssetRef("word/media/missing.png");
        try {
            new OfficeDocPdfRenderer().render(docModel);
            fail("missing image asset must fail fast");
        } catch (NopException e) {
            assertEquals("nop.err.report.pdf.doc.image-asset-not-found", e.getErrorCode());
        }
    }

    @Test
    public void testSeparatorDrawsLine() throws Exception {
        OfficeDocModel docModel = doc(paragraph(run("above")), new OfficeSeparatorModel(),
                paragraph(run("below")));
        byte[] pdf = new OfficeDocPdfRenderer().render(docModel);
        BufferedImage img = pageImage(pdf, 0);
        // paragraph at 11pt: line 72..85.2, separator box 85.2..99.2, line at ~92.2
        int probeX = Math.round(200 * SCALE);
        int stroke = RegionProbe.measureHorizontalStrokeWidth(img, probeX,
                Math.round(84 * SCALE), Math.round(100 * SCALE), Color.WHITE, 40);
        assertTrue(stroke >= 1, "separator must draw a horizontal line");
    }

    @Test
    public void testPageBreakForcesNewPage() throws Exception {
        OfficeDocModel docModel = doc(paragraph(run("page one")), new OfficePageBreakModel(),
                paragraph(run("page two")));
        byte[] pdf = new OfficeDocPdfRenderer().render(docModel);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1 = stripper.getText(document);
            assertTrue(page1.contains("page one") && !page1.contains("page two"));
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String page2 = stripper.getText(document);
            assertTrue(page2.contains("page two"));
        }
    }

    // ---- lists ----------------------------------------------------------------

    @Test
    public void testBulletAndNumberedListGlyphs() throws Exception {
        OfficeListModel bullets = new OfficeListModel();
        bullets.setId("bullets");
        bullets.setItems(new io.nop.commons.collections.KeyedList<>(i -> i.getId()));
        for (int i = 0; i < 2; i++) {
            OfficeListItemModel item = new OfficeListItemModel();
            item.setId("bi" + i);
            item.addRun(run("bullet item " + i));
            bullets.getItems().add(item);
        }
        OfficeListModel numbers = new OfficeListModel();
        numbers.setId("numbers");
        numbers.setNumId("num1");
        numbers.setItems(new io.nop.commons.collections.KeyedList<>(i -> i.getId()));
        for (int i = 0; i < 2; i++) {
            OfficeListItemModel item = new OfficeListItemModel();
            item.setId("ni" + i);
            item.addRun(run("numbered item " + i));
            numbers.getItems().add(item);
        }
        byte[] pdf = new OfficeDocPdfRenderer().render(doc(bullets, numbers));
        String text = renderText(pdf);
        assertTrue(text.contains("• bullet item 0"), "bullet glyph missing: " + text);
        assertTrue(text.contains("• bullet item 1"));
        assertTrue(text.contains("1. numbered item 0"), "decimal numbering missing: " + text);
        assertTrue(text.contains("2. numbered item 1"));
    }

    @Test
    public void testListStyleGlyphFromNumFmt() throws Exception {
        WordListStyle listStyle = new WordListStyle();
        listStyle.setId("romanList");
        listStyle.setNumFmt("upperRoman");
        listStyle.setLvlText("%1.");
        listStyle.setStart(3);
        OfficeListModel list = new OfficeListModel();
        list.setId("list");
        list.setStyleId("romanList");
        list.setItems(new io.nop.commons.collections.KeyedList<>(i -> i.getId()));
        OfficeListItemModel item = new OfficeListItemModel();
        item.setId("i0");
        item.addRun(run("roman item"));
        list.getItems().add(item);
        OfficeDocModel docModel = doc(list);
        docModel.addStyle(listStyle);
        String text = renderText(new OfficeDocPdfRenderer().render(docModel));
        assertTrue(text.contains("III. roman item"), "list style numFmt/start must drive the glyph: " + text);
    }

    // ---- hyperlink ------------------------------------------------------------

    @Test
    public void testLinkAnnotationUri() throws Exception {
        OfficeLinkModel link = new OfficeLinkModel();
        link.setId("link");
        link.setHref("https://example.com/doc");
        link.addRun(run("click here"));
        byte[] pdf = new OfficeDocPdfRenderer().render(doc(paragraph(run("see "), link),
                paragraph(new OfficeRunModel() {{
                    setId("x");
                }})));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            boolean found = false;
            for (PDAnnotation annotation : document.getPage(0).getAnnotations()) {
                if (annotation instanceof org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink linkAnno
                        && linkAnno.getAction() instanceof PDActionURI uri
                        && "https://example.com/doc".equals(uri.getURI()))
                    found = true;
            }
            assertTrue(found, "link annotation with URI must exist");
        }
    }

    private String renderText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    // ---- multi-section ----------------------------------------------------------

    @Test
    public void testMultiSectionGeometryAndHeaders() throws Exception {
        OfficeDocModel docModel = doc(paragraph(run("s1 body")));
        docModel.getPages().get(0).getHeader().add(paragraph(run("Header One")));

        OfficeDocPageModel section2 = new OfficeDocPageModel();
        section2.setName("s2");
        section2.setWidth(500.0);
        section2.setHeight(700.0);
        section2.setMargins(defaultMargins());
        section2.setBody(new java.util.ArrayList<>());
        section2.setHeader(new java.util.ArrayList<>());
        section2.setFooter(new java.util.ArrayList<>());
        section2.getBody().add(paragraph(run("s2 body")));
        section2.getHeader().add(paragraph(run("Header Two")));
        docModel.getPages().add(section2);

        byte[] pdf = new OfficeDocPdfRenderer().render(docModel);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages());
            PDRectangle box1 = document.getPage(0).getMediaBox();
            PDRectangle box2 = document.getPage(1).getMediaBox();
            assertEquals(595.3f, box1.getWidth(), 0.1f);
            assertEquals(500.0f, box2.getWidth(), 0.1f);
            assertEquals(700.0f, box2.getHeight(), 0.1f);
            String text1 = pageText(document, 0);
            String text2 = pageText(document, 1);
            assertTrue(text1.contains("Header One") && !text1.contains("Header Two"));
            assertTrue(text2.contains("Header Two") && !text2.contains("Header One"));
        }
    }

    private String pageText(PDDocument document, int index) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(index + 1);
        stripper.setEndPage(index + 1);
        return stripper.getText(document);
    }

    // ---- header/footer with table -----------------------------------------------

    @Test
    public void testHeaderSupportsTableBlock() throws Exception {
        OfficeDocModel docModel = doc(paragraph(run("body")));
        WordTable table = new WordTable();
        table.setId("ht");
        WordTableColumnConfig col = new WordTableColumnConfig();
        // column config has no id
        col.setWidth(200.0);
        table.getCols().add(col);
        WordTableRow row = new WordTableRow();
        row.setId("row");
        row.getCells().add(cell("HEADER TABLE", null, 0, 0));
        table.getRows().add(row);
        docModel.getPages().get(0).getHeader().add(table);
        String text = renderText(new OfficeDocPdfRenderer().render(docModel));
        assertTrue(text.contains("HEADER TABLE"), "header table text must render: " + text);
    }

    // ---- unknown block still fails ----------------------------------------------

    @Test
    public void testUnknownBlockThrows() throws Exception {
        OfficeDocModel docModel = doc(paragraph(run("a")));
        docModel.getPages().get(0).getBody().add(new OfficeBlock() {
        });
        try {
            new OfficeDocPdfRenderer().render(docModel);
            fail("unknown block type must fail fast");
        } catch (NopException e) {
            assertEquals("nop.err.report.pdf.doc.unknown-block", e.getErrorCode());
        }
    }

    @Test
    public void testUnknownRunThrows() throws Exception {
        OfficeDocModel docModel = doc(paragraph(new UnknownRun()));
        try {
            new OfficeDocPdfRenderer().render(docModel);
            fail("unknown run type must fail fast");
        } catch (NopException e) {
            assertEquals("nop.err.report.pdf.doc.unknown-run", e.getErrorCode());
        }
    }

    private static final class UnknownRun implements OfficeRun {
        @Override
        public String getId() {
            return "unknown";
        }
    }
}
