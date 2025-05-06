package io.nop.ooxml.xlsx.output;

import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.IExcelSheet;
import io.nop.ooxml.xlsx.util.ExcelSheetData;

import java.util.List;
import java.util.Map;

public class ExcelSheetBuilder {
    private final ExcelSheet template;
    private final ExcelCell headerCell;
    private final ExcelCell bodyCell;

    public ExcelSheetBuilder(ExcelSheet template) {
        this.template = template;
        this.headerCell = (ExcelCell) template.getTable().getCell(0, 0);
        this.bodyCell = (ExcelCell) template.getTable().getCell(1, 0);
    }

    public IExcelSheet buildSheet(ExcelSheetData sheetData) {
        ExcelSheet sheet = newSheet(sheetData.getName());
        if (sheetData.getHeaderLabels() != null) {
            writeHeaders(sheet, sheetData.getHeaderLabels());
        } else {
            writeHeaders(sheet, sheetData.getHeaders());
        }
        writeSheetData(sheet, sheetData);
        return sheet;
    }

    protected ExcelSheet newSheet(String name) {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName(name);
        sheet.setDefaultColumnWidth(template.getDefaultColumnWidth());
        sheet.setDefaultRowHeight(template.getDefaultRowHeight());
        sheet.setSheetOptions(template.getSheetOptions());
        sheet.setDataValidations(template.getDataValidations());
        sheet.setPageMargins(template.getPageMargins());
        sheet.setPageSetup(template.getPageSetup());
        return sheet;
    }

    private void writeHeaders(ExcelSheet sheet, List<String> headers) {
        ExcelRow row = sheet.getTable().makeRow(0);
        for (String header : headers) {
            ExcelCell cell = sheet.getTable().newCell();
            if (headerCell != null)
                cell.setStyleId(headerCell.getStyleId());
            cell.setValue(header);
            cell.setRow(row);
            row.internalAddCell(cell);
        }
    }

    private void writeSheetData(ExcelSheet sheet, ExcelSheetData sheetData) {
        List<String> headers = sheetData.getHeaders();
        List<Map<String, Object>> data = sheetData.getData();
        int n = data.size();

        for (int i = 0; i < n; i++) {
            ExcelRow row = sheet.getTable().makeRow(i + 1);
            Map<String, Object> rowData = data.get(i);

            for (String header : headers) {
                ExcelCell cell = new ExcelCell();
                if (bodyCell != null) {
                    cell.setStyleId(bodyCell.getStyleId());
                }
                cell.setRow(row);
                Object value = rowData.get(header);
                cell.setValue(value);
                row.internalAddCell(cell);
            }
        }
    }
}
