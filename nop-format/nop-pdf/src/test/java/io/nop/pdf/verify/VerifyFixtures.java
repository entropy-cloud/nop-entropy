package io.nop.pdf.verify;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.File;
import java.io.IOException;
import java.awt.Color;

/**
 * Synthetic vector-only PDF fixtures for the verify toolchain tests. No text is drawn so
 * that rendering does not depend on environment font substitution (plan 2463).
 */
final class VerifyFixtures {

    static final PDRectangle A4 = PDRectangle.A4;

    private VerifyFixtures() {
    }

    /**
     * Two-page vector document:
     * page 1: red filled rect (100,600)-(300,700), 2pt black horizontal line at y=400
     * (x 100..400), 2pt black vertical line at x=500 (y 200..600);
     * page 2: blue filled rect (150,300)-(350,500).
     */
    static PDDocument newTwoPageDoc() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page1 = new PDPage(A4);
        doc.addPage(page1);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
            cs.setNonStrokingColor(Color.RED);
            cs.addRect(100, 600, 200, 100);
            cs.fill();

            cs.setStrokingColor(Color.BLACK);
            cs.setLineWidth(2f);
            cs.moveTo(100, 400);
            cs.lineTo(400, 400);
            cs.stroke();

            cs.moveTo(500, 200);
            cs.lineTo(500, 600);
            cs.stroke();
        }

        PDPage page2 = new PDPage(A4);
        doc.addPage(page2);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
            cs.setNonStrokingColor(Color.BLUE);
            cs.addRect(150, 300, 200, 200);
            cs.fill();
        }
        return doc;
    }

    static File save(PDDocument doc, File file) throws IOException {
        doc.save(file);
        return file;
    }
}
