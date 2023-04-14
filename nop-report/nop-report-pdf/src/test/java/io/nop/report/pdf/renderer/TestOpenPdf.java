package io.nop.report.pdf.renderer;

import com.lowagie.text.Cell;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Table;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import io.nop.commons.util.IoHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.OutputStream;

public class TestOpenPdf extends BaseTestCase {
    @Test
    public void testOpenPdf() throws Exception {
        // Create a new document
        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 50, 50, 50, 50);
        OutputStream fos = getTargetResource("table.pdf").getOutputStream();
        try {
            PdfWriter.getInstance(document, fos);
            document.open();

            // Create a new table
            Table table = new Table(3);
            table.setWidth(100);
            table.setBorderWidth(1);
            table.setBorderColor(Color.BLACK);
            table.setPadding(0);
            table.setSpacing(0);

            // Create a new cell and set its properties
            Font font = new Font(BaseFont.createFont(BaseFont.HELVETICA,BaseFont.CP1252,false), 16, Font.BOLD, Color.BLUE);
            Cell cell = new Cell(new Phrase("Cell 1",font));
            cell.setRowspan(2);
            cell.setColspan(2);
            cell.setBackgroundColor(Color.YELLOW);
            cell.setBorderWidth(2);
            cell.setBorderColor(Color.RED);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell,0,0);

            // Create another cell and set its properties
            font = new Font(Font.TIMES_ROMAN, 12, Font.ITALIC, Color.MAGENTA);
            cell = new Cell(new Phrase("Cell 2",font));
            cell.setBackgroundColor(Color.CYAN);
            cell.setBorderWidth(1);
            cell.setBorderColor(Color.BLACK);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell,0,2);

            // Create another cell and set its properties
            font = new Font(Font.COURIER, 14, Font.BOLDITALIC, Color.PINK);
            cell = new Cell(new Phrase("Cell 3",font));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setBorderWidth(1);
            cell.setBorderColor(Color.BLACK);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell,1,2);

            // Add the table to the document
            document.add(table);

            // Close the document
            document.close();
        } finally {
            IoHelper.safeCloseObject(fos);
        }
    }
}
