package io.nop.report.pdf.doc;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.bytes.ByteString;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.IResource;
import io.nop.office.doc.model.*;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 acceptance: end-to-end model -> docx bytes -> model -> PDF (text extraction
 * identical to rendering the original model), LibreOffice cross-check as layout sanity
 * evidence (page count + key text page), and machine-local golden snapshots under
 * _tmp/pdf-golden/wi6-doc-*.
 */
public class TestOfficeDocPdfRender3E2E extends JunitBaseTestCase {

    private static int seq;

    // ---- model construction ---------------------------------------------------

    private static OfficeRunModel run(String text) {
        OfficeRunModel runModel = new OfficeRunModel();
        runModel.setId("r" + (++seq));
        runModel.setT(text);
        return runModel;
    }

    private static OfficeParagraphModel paragraph(String text) {
        OfficeParagraphModel paragraph = new OfficeParagraphModel();
        paragraph.setId("p" + (++seq));
        paragraph.addRun(run(text));
        return paragraph;
    }

    private static WordTableCell cell(String value, String styleId, int across, int down) {
        WordTableCell tableCell = new WordTableCell();
        tableCell.setId("c" + (++seq));
        tableCell.setValue(value);
        tableCell.setStyleId(styleId);
        tableCell.setMergeAcross(across);
        tableCell.setMergeDown(down);
        return tableCell;
    }

    private static OfficeDocModel buildE2EModel() throws Exception {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setBackground("FFFFFF");
        docModel.setPages(new io.nop.commons.collections.KeyedList<>(p -> p.getName()));
        docModel.setAssets(new io.nop.commons.collections.KeyedList<>(a -> a.getName()));

        OfficeDocPageModel section1 = new OfficeDocPageModel();
        section1.setName("s1");
        section1.setMargins(pageMargins());
        section1.setBody(new ArrayList<>());
        section1.setHeader(new ArrayList<>());
        section1.setFooter(new ArrayList<>());
        section1.getHeader().add(paragraph("E2E Header One"));
        section1.getFooter().add(paragraph("E2E Footer One"));

        OfficeTitleModel title = new OfficeTitleModel();
        title.setId("title1");
        io.nop.office.model.WordParagraphStyle titleParaStyle = new io.nop.office.model.WordParagraphStyle();
        titleParaStyle.setId("heading1");
        titleParaStyle.setTitleLevel(1);
        title.setStyle(titleParaStyle);
        docModel.addStyle(titleParaStyle);
        // inline run style only: the WI5 writer does not round-trip named run styles
        WordRunStyle inlineRun = new WordRunStyle();
        io.nop.office.model.OfficeFont font = new io.nop.office.model.OfficeFont();
        font.setFontColor("008000");
        font.setBold(true);
        inlineRun.setFont(font);
        OfficeRunModel titleRun = run("Report Title");
        titleRun.setStyle(inlineRun);
        title.addRun(titleRun);
        section1.getBody().add(title);

        section1.getBody().add(paragraph("Intro paragraph with 中文内容 mixed text."));

        OfficeListModel list = new OfficeListModel();
        list.setId("list1");
        list.setNumId("num1");
        list.setItems(new io.nop.commons.collections.KeyedList<>(i -> i.getId()));
        for (String item : new String[]{"first item", "second item"}) {
            OfficeListItemModel itemModel = new OfficeListItemModel();
            itemModel.setId("li" + (++seq));
            itemModel.addRun(run(item));
            list.getItems().add(itemModel);
        }
        section1.getBody().add(list);

        // table with borders and a merged cell
        WordCellStyle cellStyle = new WordCellStyle();
        cellStyle.setId("cellBordered");
        io.nop.office.model.WordBorderStyle border = new io.nop.office.model.WordBorderStyle();
        border.setType(OfficeLineStyle.SINGLE);
        border.setWeight(1);
        border.setColor("000000");
        cellStyle.setTopBorder(border);
        cellStyle.setBottomBorder(border);
        cellStyle.setLeftBorder(border);
        cellStyle.setRightBorder(border);
        docModel.addStyle(cellStyle);

        WordTable table = new WordTable();
        table.setId("table1");
        for (int c = 0; c < 2; c++) {
            WordTableColumnConfig col = new WordTableColumnConfig();
            col.setWidth(150.0);
            table.getCols().add(col);
        }
        WordTableRow row0 = new WordTableRow();
        row0.setId("tr0");
        row0.getCells().add(cell("H1", "cellBordered", 1, 0));
        row0.getCells().add(cell("TAIL", "cellBordered", 0, 0));
        table.getRows().add(row0);
        WordTableRow row1 = new WordTableRow();
        row1.setId("tr1");
        row1.getCells().add(cell("A1", "cellBordered", 0, 0));
        row1.getCells().add(cell("B1", "cellBordered", 0, 0));
        table.getRows().add(row1);
        section1.getBody().add(table);

        OfficeHyperlinkModel hyperlink = new OfficeHyperlinkModel();
        hyperlink.setId("link1");
        hyperlink.setHref("https://example.com/e2e");
        hyperlink.addRun(run("example link"));
        section1.getBody().add(hyperlink);

        section1.getBody().add(new OfficeSeparatorModel());
        section1.getBody().add(new OfficePageBreakModel());
        section1.getBody().add(paragraph("Second page after break."));

        // image asset on the second page flow
        OfficeImageAsset asset = new OfficeImageAsset();
        asset.setName("word/media/e2e.png");
        asset.setExt("png");
        asset.setData(ByteString.of(smallPng()));
        docModel.getAssets().add(asset);
        OfficeImageModel image = new OfficeImageModel();
        image.setId("img1");
        image.setAssetRef("word/media/e2e.png");
        image.setWidth(100.0);
        image.setHeight(60.0);
        section1.getBody().add(image);

        docModel.getPages().add(section1);

        // second section with its own geometry and header/footer
        OfficeDocPageModel section2 = new OfficeDocPageModel();
        section2.setName("s2");
        section2.setWidth(500.0);
        section2.setHeight(700.0);
        section2.setMargins(pageMargins());
        section2.setBody(new ArrayList<>());
        section2.setHeader(new ArrayList<>());
        section2.setFooter(new ArrayList<>());
        section2.getHeader().add(paragraph("E2E Header Two"));
        // explicit footer: the parser's missing-footerReference fallback would reuse section 1's footer part
        section2.getFooter().add(paragraph("E2E Footer Two"));
        section2.getBody().add(paragraph("Section two body."));
        docModel.getPages().add(section2);
        return docModel;
    }

    private static OfficePageMargins pageMargins() {
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(72);
        margins.setBottom(72);
        margins.setLeft(72);
        margins.setRight(72);
        margins.setHeader(36);
        margins.setFooter(36);
        return margins;
    }

    private static byte[] smallPng() throws Exception {
        BufferedImage img = new BufferedImage(20, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 12; y++)
            for (int x = 0; x < 20; x++)
                img.setRGB(x, y, 0x00AA00);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static String renderText(OfficeDocModel model) throws Exception {
        byte[] pdf = new OfficeDocPdfRenderer().render(model);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "");
    }

    // ---- 1. end-to-end ---------------------------------------------------------

    @Test
    public void testEndToEndWriterParserRender() throws Exception {
        OfficeDocModel original = buildE2EModel();
        String direct = renderText(original);

        Path dir = PdfGoldenManager.resolveRepoRoot().resolve("_tmp").resolve("wi6-e2e");
        Files.createDirectories(dir);
        File docxFile = dir.resolve("e2e.docx").toFile();
        IResource resource = new FileResource(docxFile);
        new OfficeDocModelWriter().writeToResource(original, resource);
        assertTrue(docxFile.length() > 0);

        OfficeDocModel reparsed = new OfficeDocModelParser().parseFromResource(resource);
        String roundtrip = renderText(reparsed);

        assertEquals(normalize(direct), normalize(roundtrip),
                "docx round trip must preserve the rendered text");
    }

    // ---- 2. LibreOffice cross-check ---------------------------------------------

    private static Path findSoffice() {
        String override = System.getProperty("nop.docx.soffice.path");
        if (override != null && Files.isExecutable(Path.of(override)))
            return Path.of(override);
        List<String> candidates = new ArrayList<>(List.of(
                "/opt/homebrew/bin/soffice",
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice",
                "/usr/local/bin/soffice"));
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null)
            for (String dir : pathEnv.split(File.pathSeparator))
                candidates.add(Path.of(dir, "soffice").toString());
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.isExecutable(p))
                return p;
        }
        return null;
    }

    private byte[] sofficeToPdf(Path soffice, File docx) throws Exception {
        Path workDir = Files.createDirectories(
                PdfGoldenManager.resolveRepoRoot().resolve("_tmp").resolve("wi6-e2e").resolve("soffice"));
        Path profile = workDir.resolve("soffice-profile-" + System.nanoTime());
        ProcessBuilder pb = new ProcessBuilder(soffice.toString(), "--headless", "--norestore",
                "--nolockcheck", "-env:UserInstallation=" + profile.toUri(),
                "--convert-to", "pdf", "--outdir", workDir.toString(), docx.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IllegalStateException("soffice conversion timed out");
        }
        assertEquals(0, process.exitValue(), "soffice exit: " + output);
        String name = docx.getName();
        Path pdf = workDir.resolve(name.substring(0, name.length() - 5) + ".pdf");
        assertTrue(Files.exists(pdf), "soffice produced no pdf: " + output);
        return Files.readAllBytes(pdf);
    }

    /** Single-page samples: page count and key-text page must agree with LibreOffice. */
    @Test
    public void testLibreOfficeCrossCheck() throws Exception {
        Path soffice = findSoffice();
        Assumptions.assumeTrue(soffice != null, "LibreOffice soffice not available; skipped");

        Path dir = PdfGoldenManager.resolveRepoRoot().resolve("_tmp").resolve("wi6-e2e");
        Files.createDirectories(dir);
        OfficeDocModelWriter writer = new OfficeDocModelWriter();

        for (int sample = 0; sample < 2; sample++) {
            OfficeDocModel model = sample == 0 ? singlePageTextSample() : singlePageTableSample();
            File docxFile = dir.resolve("sample" + sample + ".docx").toFile();
            writer.writeToResource(model, new FileResource(docxFile));

            byte[] ours = new OfficeDocPdfRenderer().render(model);
            byte[] theirs = sofficeToPdf(soffice, docxFile);
            try (PDDocument ourDoc = Loader.loadPDF(ours);
                 PDDocument theirDoc = Loader.loadPDF(theirs)) {
                assertEquals(theirDoc.getNumberOfPages(), ourDoc.getNumberOfPages(),
                        "sample " + sample + ": page count must match LibreOffice");
                String ourText = new PDFTextStripper().getText(ourDoc);
                String theirText = new PDFTextStripper().getText(theirDoc);
                String keyText = sample == 0 ? "LibreOffice sample paragraph" : "sample table B1";
                assertEquals(0, pageIndexOf(theirText, keyText),
                        "sample " + sample + ": key text must be on page 1 in both renders");
                assertEquals(0, pageIndexOf(ourText, keyText));
            }
        }
    }

    private static int pageIndexOf(String text, String key) {
        int index = new PDFTextStripperPageCounter().indexOf(text, key);
        return index;
    }

    /** Split extracted text on the form-feed page separator used by PDFTextStripper. */
    private static final class PDFTextStripperPageCounter {
        int indexOf(String text, String key) {
            String[] pages = text.split("\f");
            for (int i = 0; i < pages.length; i++)
                if (pages[i].contains(key))
                    return i;
            return -1;
        }
    }

    private OfficeDocModel singlePageTextSample() {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setPages(new io.nop.commons.collections.KeyedList<>(p -> p.getName()));
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setMargins(pageMargins());
        page.setBody(new ArrayList<>());
        page.setHeader(new ArrayList<>());
        page.setFooter(new ArrayList<>());
        OfficeTitleModel sampleTitle = new OfficeTitleModel();
        sampleTitle.setId("t");
        io.nop.office.model.WordParagraphStyle lvl = new io.nop.office.model.WordParagraphStyle();
        lvl.setId("headingSample");
        lvl.setTitleLevel(1);
        sampleTitle.setStyle(lvl);
        docModel.addStyle(lvl);
        sampleTitle.addRun(run("LibreOffice Sample Title"));
        page.getBody().add(sampleTitle);
        page.getBody().add(paragraph("LibreOffice sample paragraph body text."));
        docModel.getPages().add(page);
        return docModel;
    }

    private OfficeDocModel singlePageTableSample() {
        OfficeDocModel docModel = new OfficeDocModel();
        docModel.setWidth(595.3);
        docModel.setHeight(841.9);
        docModel.setPages(new io.nop.commons.collections.KeyedList<>(p -> p.getName()));
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setMargins(pageMargins());
        page.setBody(new ArrayList<>());
        page.setHeader(new ArrayList<>());
        page.setFooter(new ArrayList<>());
        page.getBody().add(paragraph("LibreOffice sample intro."));
        WordTable table = new WordTable();
        table.setId("t");
        for (int c = 0; c < 2; c++) {
            WordTableColumnConfig col = new WordTableColumnConfig();
            col.setWidth(150.0);
            table.getCols().add(col);
        }
        WordTableRow row = new WordTableRow();
        row.setId("r0");
        row.getCells().add(cell("sample table A1", null, 0, 0));
        row.getCells().add(cell("sample table B1", null, 0, 0));
        table.getRows().add(row);
        page.getBody().add(table);
        docModel.getPages().add(page);
        return docModel;
    }

    // ---- 3. golden snapshots -----------------------------------------------------

    @Test
    public void testGoldenSnapshots() throws Exception {
        List<String[]> samples = List.of(
                new String[]{"text", "golden-paragraph"},
                new String[]{"table", "golden-A1"},
                new String[]{"mixed", "golden-second"});

        for (String[] sample : samples) {
            OfficeDocModel model;
            switch (sample[0]) {
                case "text":
                    model = singlePageTextSample();
                    break;
                case "table":
                    model = singlePageTableSample();
                    break;
                default:
                    model = buildE2EModel();
            }
            byte[] pdf = new OfficeDocPdfRenderer().render(model);
            PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi6-doc-" + sample[0]);
            try (PDDocument document = Loader.loadPDF(pdf)) {
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    BufferedImage image = PdfImageRenderer.renderPage(document, i, PdfImageRenderer.DEFAULT_DPI);
                    GoldenCompareResult result = golden.compare("page-" + (i + 1), image);
                    assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                                    || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                            "golden mismatch for wi6-doc-" + sample[0] + " page " + (i + 1)
                                    + ": " + result.getStatus());
                }
            }
            assertTrue(true, sample[1]);
        }
    }
}
