package io.nop.ooxml.docx.verify;

import io.nop.ooxml.docx.corpus.DocxCorpusBuilder;
import io.nop.pdf.verify.PdfImageRenderer;
import io.nop.pdf.verify.RegionProbe;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Visual acceptance smoke test for the docx corpus: sample docx → LibreOffice PDF →
 * page PNG → golden compare (machine-local snapshots under {@code _tmp/docx-golden/})
 * plus RegionProbe pixel assertions.
 *
 * <p>Named adjudication: when LibreOffice is not installed on the machine, these
 * fixtures are skipped via assumption (logged), not silently passed.</p>
 */
public class TestDocxVisualVerifier {

    private static final String GOLDEN_FIXTURE = "wi0-docx-corpus";

    @Test
    public void testSimpleDocumentRendersAndMatchesGolden() throws Exception {
        DocxVisualVerifier verifier = newVerifier();
        byte[] pdf = convertSample(verifier, DocxCorpusBuilder::simpleDocument, "simple");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> pages = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            Assertions.assertEquals(1, pages.size(), "simple sample should render exactly one page");

            BufferedImage page = pages.get(0);
            // text ink exists on the page: some dark pixels present
            assertTrue(hasDarkInk(page), "simple sample page must contain rendered text ink");
            // white background outside text: top-left corner sample stays white-ish
            java.awt.Color corner = RegionProbe.averageColor(page, 2, 2, 12, 12);
            assertTrue(corner.getRed() > 230 && corner.getGreen() > 230 && corner.getBlue() > 230,
                    "page corner must stay white background: " + corner);

            assertGolden(verifier, "simple", pages);
        }
    }

    @Test
    public void testMultiSectionDocumentRendersTwoPages() throws Exception {
        DocxVisualVerifier verifier = newVerifier();
        byte[] pdf = convertSample(verifier, DocxCorpusBuilder::multiSectionDoc, "multi-section");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> pages = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            assertTrue(pages.size() >= 2, "two-section sample must render at least two pages");
            for (BufferedImage page : pages) {
                assertTrue(hasDarkInk(page), "every section page must contain body/header ink");
            }
            assertGolden(verifier, "multi-section", pages);
        }
    }

    @Test
    public void testMergedTableDocumentRendersTable() throws Exception {
        DocxVisualVerifier verifier = newVerifier();
        byte[] pdf = convertSample(verifier, DocxCorpusBuilder::mergedTableDoc, "merged-table");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> pages = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            Assertions.assertEquals(1, pages.size(), "merged-table sample should fit one page");
            BufferedImage page = pages.get(0);
            assertTrue(hasDarkInk(page), "merged-table page must contain rendered ink");
            // table borders + shading introduce colored (non-pure-white) area: assert
            // a non-trivial share of pixels is non-white
            assertTrue(nonWhiteRatio(page) > 0.005, "table page must contain drawn table area");
            assertGolden(verifier, "merged-table", pages);
        }
    }

    // ------------------------------------------------------------------ helpers

    private DocxVisualVerifier newVerifier() {
        assumeTrue(DocxVisualVerifier.isSofficeAvailable(),
                "LibreOffice soffice not available on this machine (named adjudication): "
                        + "docx visual golden fixtures skipped; set -D" + DocxVisualVerifier.SOFFICE_PATH_PROPERTY
                        + " or install LibreOffice to run them");
        return DocxVisualVerifier.forDefaultDir(GOLDEN_FIXTURE);
    }

    private byte[] convertSample(DocxVisualVerifier verifier, java.util.function.Supplier<byte[]> builder,
                                 String name) throws Exception {
        Path docx = Files.createTempFile("docx-visual-" + name, ".docx");
        Files.write(docx, builder.get());
        try {
            return verifier.convertToPdf(docx);
        } finally {
            Files.deleteIfExists(docx);
        }
    }

    private void assertGolden(DocxVisualVerifier verifier, String sample, List<BufferedImage> pages) {
        for (int i = 0; i < pages.size(); i++) {
            String pageName = sample + "-page-" + (i + 1);
            assertTrue(verifier.verifyGolden(pageName, pages.get(i)),
                    "golden regression for " + pageName + ": " + verifier.getLastResult()
                            + " (rerun with -D" + DocxVisualVerifier.UPDATE_MODE_PROPERTY
                            + "=true to refresh snapshots at " + verifier.getGoldenRoot() + ")");
        }
    }

    private static int[] rgb(int argb) {
        return new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
    }

    private static boolean hasDarkInk(BufferedImage page) {
        int w = page.getWidth();
        int h = page.getHeight();
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int[] c = rgb(page.getRGB(x, y));
                if (c[0] < 120 && c[1] < 120 && c[2] < 120)
                    return true;
            }
        }
        return false;
    }

    private static double nonWhiteRatio(BufferedImage page) {
        int w = page.getWidth();
        int h = page.getHeight();
        long nonWhite = 0;
        long total = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int[] c = rgb(page.getRGB(x, y));
                total++;
                if (c[0] < 245 || c[1] < 245 || c[2] < 245)
                    nonWhite++;
            }
        }
        return total == 0 ? 0 : (double) nonWhite / total;
    }
}
