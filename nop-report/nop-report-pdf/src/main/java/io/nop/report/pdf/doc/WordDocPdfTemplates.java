package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.office.doc.model.OfficeDocModel;

import java.io.IOException;

import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_DOC_RENDER_MODEL_REQUIRED;

/**
 * PDF outlet of the unified Word generation surface (WI9, plan 2524; decision
 * U1 in ai-dev/design/word-editor/online-word-model-design.md section 21):
 * renders an {@link OfficeDocModel} to PDF bytes and carries the renderer's
 * named degradations on the result.
 *
 * <p>This outlet only accepts a model, not docx bytes: the docx parse and
 * expansion stages ({@code OfficeDocModelParser} / {@code OfficeDocModelExpander})
 * live in nop-ooxml-docx / nop-report-docx, and a compile dependency from this
 * module to nop-report-docx is rejected by the Maven reactor as a cycle (the
 * WI7 edge nop-report-docx -&gt; nop-report-pdf is test scope). Callers chain
 * the two outlets: {@code WordDocTemplates.expandToModel(docxBytes, scope)}
 * feeds {@link #renderPdf(OfficeDocModel)}. Passing an un-expanded template
 * model renders the un-expanded template form - expand first.</p>
 */
public final class WordDocPdfTemplates {

    private WordDocPdfTemplates() {
    }

    /**
     * Renders the (already expanded) document model to PDF bytes.
     *
     * @param model the document model, e.g. the model stage result of
     *              {@code io.nop.report.docx.WordDocTemplates}
     * @return the PDF bytes with the render warnings
     */
    public static WordDocPdfOutput renderPdf(OfficeDocModel model) {
        if (model == null)
            throw new NopException(ERR_PDF_DOC_RENDER_MODEL_REQUIRED);

        // fresh renderer per call: renderer state (lastWarnings) is per instance
        OfficeDocPdfRenderer renderer = new OfficeDocPdfRenderer();
        byte[] pdf;
        try {
            pdf = renderer.render(model);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return new WordDocPdfOutput(pdf, renderer.getLastWarnings());
    }
}
