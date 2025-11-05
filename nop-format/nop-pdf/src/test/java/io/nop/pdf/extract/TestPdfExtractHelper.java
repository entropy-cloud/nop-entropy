package io.nop.pdf.extract;

import io.nop.commons.util.FileHelper;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.pdf.core.PdfDoc;
import io.nop.pdf.extract.utils.PdfExtractHelper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestPdfExtractHelper extends BaseTestCase {

    @Test
    public void testToHtml() {
        IResource srcFile = attachmentResource("test.pdf");
        IResource destFile = getTargetResource("test.html");
        PdfExtractHelper.pdfToHtml(srcFile, destFile);

        File txtFile = getTargetFile("test.txt");
        FileHelper.writeText(txtFile, PdfDoc.loadFromFile(srcFile.toFile()).getAllText(), null);
    }
}
