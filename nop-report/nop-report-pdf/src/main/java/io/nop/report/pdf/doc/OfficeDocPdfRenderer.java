package io.nop.report.pdf.doc;

import io.nop.office.doc.model.OfficeDocModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Entry point of the OfficeDocModel -> PDF renderer: lays out the model over the
 * shared rendering session, then paints each page. Named styles go through
 * {@code OfficeStyleResolver} with inline overrides taking precedence.
 */
public class OfficeDocPdfRenderer {

    private List<String> lastWarnings = List.of();

    /** Render to a byte array. */
    public byte[] render(OfficeDocModel doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        render(doc, bos);
        return bos.toByteArray();
    }

    /** Render to a stream; the stream is not closed by the renderer. */
    public void render(OfficeDocModel doc, OutputStream out) throws IOException {
        try (DocRenderSession session = new DocRenderSession(doc)) {
            DocLayoutEngine engine = new DocLayoutEngine(session);
            List<DocPage> pages = engine.layout();
            DocPagePainter painter = new DocPagePainter(session);
            painter.paintPages(pages);
            session.getDocument().save(out);
            lastWarnings = List.copyOf(session.getWarnings());
        }
    }

    /** Named degradations recorded during the last render (empty list initially). */
    public List<String> getLastWarnings() {
        return lastWarnings;
    }
}
