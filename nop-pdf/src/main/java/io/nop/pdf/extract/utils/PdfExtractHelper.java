package io.nop.pdf.extract.utils;

import io.nop.core.resource.IResource;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.export.ResourceDocumentExporters;
import io.nop.pdf.extract.parser.ResourceDocumentParser;
import io.nop.pdf.extract.struct.ResourceDocument;

public class PdfExtractHelper {
    public static void pdfToHtml(IResource srcFile, IResource destFile) {
        ResourceParseConfig config = new ResourceParseConfig();
        ResourceDocument doc = new ResourceDocumentParser(config).parseFromResource(srcFile);
        ResourceDocumentExporters.getExporter("html").exportToResource(doc, destFile, null);
    }
}
