package io.nop.pdf.verify;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link PDDocument} pages to images at a fixed DPI for visual acceptance checks.
 *
 * <p>DPI adjudication (plan 2463): the default is 110 DPI, matching the existing
 * {@code TestPdfExportAudit} baseline. Rendering is pixel-deterministic for the same
 * document on the same JVM/platform; the same is NOT true across platforms when text
 * uses non-embedded standard-14 fonts, which are substituted by environment fonts.</p>
 */
public class PdfImageRenderer {

    /**
     * Default rendering DPI, aligned with the TestPdfExportAudit baseline (110 DPI).
     */
    public static final int DEFAULT_DPI = 110;

    private PdfImageRenderer() {
    }

    /**
     * Render a single page to a {@link BufferedImage}.
     *
     * @param doc       the loaded document
     * @param pageIndex zero-based page index
     * @param dpi       rasterization DPI
     * @return the rendered page image
     */
    public static BufferedImage renderPage(PDDocument doc, int pageIndex, int dpi) {
        Guard.notNull(doc, "doc");
        Guard.checkArgument(pageIndex >= 0 && pageIndex < doc.getNumberOfPages(),
                "pageIndex out of range", pageIndex);
        Guard.checkArgument(dpi > 0, "dpi must be positive", dpi);
        try {
            return new PDFRenderer(doc).renderImageWithDPI(pageIndex, dpi);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Render all pages of the document in document order.
     *
     * @param doc the loaded document
     * @param dpi rasterization DPI
     * @return one image per page, list size equals document page count
     */
    public static List<BufferedImage> renderAll(PDDocument doc, int dpi) {
        Guard.notNull(doc, "doc");
        Guard.checkArgument(dpi > 0, "dpi must be positive", dpi);
        PDFRenderer renderer = new PDFRenderer(doc);
        List<BufferedImage> ret = new ArrayList<>(doc.getNumberOfPages());
        for (int i = 0, n = doc.getNumberOfPages(); i < n; i++) {
            try {
                ret.add(renderer.renderImageWithDPI(i, dpi));
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        return ret;
    }
}
