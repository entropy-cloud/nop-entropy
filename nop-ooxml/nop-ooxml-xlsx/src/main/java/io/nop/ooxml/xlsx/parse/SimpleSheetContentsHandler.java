package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICell;
import io.nop.excel.ExcelConstants;
import io.nop.excel.format.ExcelDateHelper;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;

import java.util.List;

public class SimpleSheetContentsHandler implements SheetContentsHandler {
    private final ExcelWorkbook workbook;

    private final ExcelSheet sheet = new ExcelSheet();
    private ExcelTable table = sheet.getTable();

    private String drawingId;

    public SimpleSheetContentsHandler(ExcelWorkbook workbook, String sheetName) {
        this.workbook = workbook;
        this.sheet.setName(sheetName);
    }

    public ExcelSheet getSheet() {
        return sheet;
    }

    public String getDrawingId() {
        return drawingId;
    }

    public void drawing(String id) {
        this.drawingId = StringHelper.emptyAsNull(id);
    }

    @Override
    public void startSheet(String sheetName) {
        this.sheet.setName(sheetName);
    }

    @Override
    public void cols(List<ExcelColumnConfig> cols) {
        table.setCols(cols);
    }

    @Override
    public void pageMargins(ExcelPageMargins pageMargins) {
        sheet.setPageMargins(pageMargins);
    }

    @Override
    public void sheetFormat(Double defaultRowHeight) {
        sheet.setDefaultRowHeight(defaultRowHeight);
    }

    @Override
    public void startRow(int rowNum, Double height, boolean hidden) {
        ExcelRow row = table.makeRow(rowNum);
        row.setHeight(height);
        row.setHidden(hidden);
    }

    @Override
    public void endRow(int rowNum) {
    }

    @Override
    public void cell(CellPosition cellRef, Object value, String formulaStr, int styleId) {
        ExcelCell cell = table.newCell();
        cell.setFormula(formulaStr);
        cell.setLocation(new SourceLocation(workbook.resourcePath(), 0, 0, 0, 0,
                sheet.getName(), cellRef.toABString(), null));
        if (styleId >= 0) {
            cell.setStyleId(String.valueOf(styleId));
            if (value instanceof Number) {
                ExcelStyle style = workbook.getStyle(cell.getStyleId());
                if (style != null && style.isDateFormat()) {
                    value = ExcelDateHelper.excelDateToLocalDateTime(((Number) value).doubleValue());
                }
            }
        }
        cell.setValue(value);
        table.setCell(cellRef.getRowIndex(), cellRef.getColIndex(), cell);
    }

    @Override
    public void mergeCell(CellRange range) {
        ExcelCell ec = (ExcelCell) table.getCell(range.getFirstRowIndex(), range.getFirstColIndex());
        ICell lastCell = table.getCell(range.getLastRowIndex(), range.getLastColIndex());
        if (lastCell != null) {
            ExcelCell ec2 = (ExcelCell) lastCell.getRealCell();
            ExcelStyle style = getStyle(workbook, ec);
            ExcelStyle style2 = getStyle(workbook, ec2);
            if (style != null && style2 != null) {
                style.setRightBorder(style2.getRightBorder());
                style.setBottomBorder(style2.getBottomBorder());
            }
        }
        table.mergeCell(range);
    }

    @Override
    public void link(String ref, String location, String rId) {
        if(ref.indexOf(':') > 0)
            return;

        CellPosition pos = CellPosition.fromABString(ref);
        ExcelCell cell = (ExcelCell) table.makeCell(pos.getRowIndex(), pos.getColIndex());
        if (location != null) {
            cell.setLinkUrl(ExcelConstants.REF_LINK_PREFIX + location);
        }
    }

    ExcelStyle getStyle(ExcelWorkbook wk, ExcelCell ec) {
        String styleId = ec.getStyleId();
        if (styleId == null)
            return null;
        return wk.getStyle(styleId);
    }
}
