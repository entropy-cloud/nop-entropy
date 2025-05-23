package io.nop.report.pdf.renderer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.Closeable;
import java.io.IOException;

public class PdfPageRenderer implements Closeable {
    private final PDDocument doc;
    private final PDPage page;
    private final PDPageContentStream contentStream;

    public PdfPageRenderer(PDDocument doc, PDPage page) throws IOException {
        this.doc = doc;
        this.page = page;
        this.contentStream = new PDPageContentStream(doc, page);
    }

    public PDDocument getDocument() {
        return doc;
    }

    public PDPage getPage() {
        return page;
    }

    public PDPageContentStream getContentStream() {
        return contentStream;
    }

    @Override
    public void close() throws IOException {
        contentStream.close();
    }

    public void saveGraphicsState() throws IOException {
        contentStream.saveGraphicsState();
    }

    public void restoreGraphicsState() throws IOException {
        contentStream.restoreGraphicsState();
    }
}
