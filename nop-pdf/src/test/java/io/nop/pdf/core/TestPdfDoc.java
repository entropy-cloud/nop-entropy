package io.nop.pdf.core;

import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

@Disabled
public class TestPdfDoc {
    @Test
    public void testDoc() {
        File dir = new File("c:/test/split");
        File file = new File("c:/test/data.pdf");

        PdfDoc doc = PdfDoc.loadFromFile(file);

        String html = doc.getAllTablesHtml();

        html = "<style>.xui-table .xui-cell{border:1px solid black;}</style>" + html;

        FileHelper.writeText(new File(file.getParent(), "all.html"), html, null);

        FileHelper.writeText(new File(file.getParent(), "all.txt"), doc.getAllText("====Page{pageNo}===", null, 200), null);
    }
}
