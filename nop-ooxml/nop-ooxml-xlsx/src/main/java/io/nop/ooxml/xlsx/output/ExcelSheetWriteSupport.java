package io.nop.ooxml.xlsx.output;

import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.handler.CollectXmlHandler;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.IColumnConfig;
import io.nop.core.model.table.IRowView;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class ExcelSheetWriteSupport {
    private final Writer out;
    private final IXNodeHandler handler;
    private final ExcelWriteSupport writeSupport;

    public ExcelSheetWriteSupport(ExcelWorkbook workbook, int sheetIndex, IResource sheetFile) {
        this.out = new BufferedWriter(sheetFile.getWriter(null));
        this.handler = new CollectXmlHandler(out);
        this.writeSupport = new ExcelWriteSupport(sheetIndex == 0, sheetIndex, workbook);
    }

    public void beginSheet(CellRange cellRange, Double defaultRowHeight, Double defaultColWidth,
                           List<? extends IColumnConfig> cols) {
        writeSupport.genSheetBegin(handler, cellRange);

        writeSupport.genSheetViews(handler, defaultRowHeight);

        writeSupport.genCols(handler, cols, defaultColWidth);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

    public void beginRows() {
        writeSupport.beginRows(handler);
    }

    public void endRows() {
        writeSupport.endRows(handler);
    }

    public void writeRow(int index, IRowView row) {
        writeSupport.genRow(handler, index, row.getColCount(), row);
    }

    public void endSheet(ExcelPageMargins pageMargins, ExcelPageSetup pageSetup, List<ExcelImage> images) {
        writeSupport.genPageMargins(handler, pageMargins);
        writeSupport.genPageSetup(handler, pageSetup);

        if (images != null && !images.isEmpty()) {
            writeSupport.genDrawing(handler);
        }

        writeSupport.genSheetEnd(handler);
    }
}
