package io.nop.report.docx;

import io.nop.office.doc.model.OfficeDocModel;

import java.util.List;

/**
 * Result of {@link WordDocTemplates#generate}: the generated docx bytes plus the
 * model-stage metadata (parse skips, expanded table count, bound image count).
 */
public class WordDocOutput extends WordDocModelOutput {
    private final byte[] docxBytes;

    public WordDocOutput(byte[] docxBytes, OfficeDocModel model, List<String> parseSkips,
                         int expandedTableCount, int boundImageCount) {
        super(model, parseSkips, expandedTableCount, boundImageCount);
        this.docxBytes = docxBytes;
    }

    /** The generated docx package. The array is not copied; do not modify it. */
    public byte[] getDocxBytes() {
        return docxBytes;
    }
}
