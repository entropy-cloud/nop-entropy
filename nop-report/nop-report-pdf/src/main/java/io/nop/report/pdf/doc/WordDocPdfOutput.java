package io.nop.report.pdf.doc;

import java.util.List;

/**
 * Result of {@link WordDocPdfTemplates#renderPdf}: the rendered PDF bytes plus
 * the named degradations recorded by the renderer (WI9, plan 2524).
 */
public class WordDocPdfOutput {
    private final byte[] pdfBytes;
    private final List<String> warnings;

    public WordDocPdfOutput(byte[] pdfBytes, List<String> warnings) {
        this.pdfBytes = pdfBytes;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** The rendered PDF document. The array is not copied; do not modify it. */
    public byte[] getPdfBytes() {
        return pdfBytes;
    }

    /**
     * Named degradations recorded during the render (mirror of
     * {@code OfficeDocPdfRenderer.getLastWarnings()}); empty when none occurred.
     */
    public List<String> getWarnings() {
        return warnings;
    }
}
