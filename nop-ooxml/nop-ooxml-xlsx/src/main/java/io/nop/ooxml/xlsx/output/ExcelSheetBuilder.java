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
    private final ExcelCell titleCell;
    private final ExcelCell attributeLabelCell;
    private final ExcelCell attributeValueCell;

    public ExcelSheetBuilder(ExcelSheet template) {
        this.template = template;
        this.headerCell = (ExcelCell) template.getTable().getCell(0, 0);
        this.bodyCell = (ExcelCell) template.getTable().getCell(1, 0);
        this.titleCell = (ExcelCell) template.getTable().getCell(2, 0);
        this.attributeLabelCell = (ExcelCell) template.getTable().getCell(3, 0);
        this.attributeValueCell = (ExcelCell) template.getTable().getCell(3, 1);
    }

    public IExcelSheet buildSheet(ExcelSheetData sheetData) {
        ExcelSheet sheet = newSheet(sheetData.getName());
        int rowIndex = 0;

        if (sheetData.getTitle() != null) {
            writeTitle(sheet, sheetData.getTitle(), sheetData.getHeaders());
            rowIndex++;
        }

        if (sheetData.getAttributes() != null) {
            writeAttributes(sheet, sheetData.getAttributes(), sheetData.getHeaders(), rowIndex);
            rowIndex += sheetData.getAttributes().size();
            rowIndex++;
        }

        if (sheetData.getHeaderLabels() != null) {
            writeHeaders(sheet, sheetData.getHeaderLabels(), rowIndex);
        } else {
            writeHeaders(sheet, sheetData.getHeaders(), rowIndex);
        }

        writeSheetData(sheet, sheetData, rowIndex + 1);
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

    private void writeTitle(ExcelSheet sheet, String title, List<String> headers) {
        ExcelRow row = sheet.getTable().makeRow(0);
        if (titleCell != null) {
            row.setHeight(titleCell.getRow().getHeight());
        }
        ExcelCell cell = sheet.getTable().newCell();
        cell.setRow(row);
        cell.setMergeAcross(Math.max(1, headers.size() - 1));
        cell.setValue(title);
    }

    private void writeAttributes(ExcelSheet sheet, Map<String, Object> attributes, List<String> headers, int rowIndex) {
        int valueCellSpan = Math.max(1, headers.size() - 1);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            ExcelRow row = sheet.getTable().makeRow(rowIndex++);
            ExcelCell label = sheet.getTable().newCell();
            if (attributeLabelCell != null) {
                label.setStyleId(attributeLabelCell.getStyleId());
                row.setHeight(attributeLabelCell.getRow().getHeight());
            }

            label.setRow(row);
            label.setValue(entry.getKey());

            ExcelCell valueCell = sheet.getTable().newCell();
            valueCell.setRow(row);
            if (attributeValueCell != null)
                valueCell.setStyleId(attributeValueCell.getStyleId());
            valueCell.setValue(entry.getValue());
            valueCell.setMergeAcross(valueCellSpan - 1);
            row.internalAddCell(label);
            row.internalAddCell(valueCell);
        }
    }

    private void writeHeaders(ExcelSheet sheet, List<String> headers, int rowIndex) {
        ExcelRow row = sheet.getTable().makeRow(rowIndex);
        if (headerCell != null) {
            row.setHeight(headerCell.getRow().getHeight());
        }
        for (String header : headers) {
            ExcelCell cell = sheet.getTable().newCell();
            if (headerCell != null)
                cell.setStyleId(headerCell.getStyleId());
            cell.setValue(header);
            cell.setRow(row);
            row.internalAddCell(cell);
        }
    }

    private void writeSheetData(ExcelSheet sheet, ExcelSheetData sheetData, int rowIndex) {
        List<String> headers = sheetData.getHeaders();
        List<Map<String, Object>> data = sheetData.getData();
        int n = data.size();

        for (int i = 0; i < n; i++) {
            ExcelRow row = sheet.getTable().makeRow(i + rowIndex);
            if (bodyCell != null) {
                row.setHeight(bodyCell.getRow().getHeight());
            }

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
