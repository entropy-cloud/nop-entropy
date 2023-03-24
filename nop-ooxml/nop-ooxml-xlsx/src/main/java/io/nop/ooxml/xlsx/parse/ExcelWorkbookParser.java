/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ICellView;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.CommentsPart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;

import java.util.List;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_REL_ID;
import static io.nop.ooxml.xlsx.XlsxErrors.ARG_TYPE;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_NULL_REL_PART;

public class ExcelWorkbookParser extends AbstractXlsxParser {

    @Override
    protected ExcelSheet parseSheet(ExcelWorkbook workbook, XSSFSheetRef sheetRef, WorkbookPart workbookFile) {
        IOfficePackagePart sheetPart = pkg.getRelPart(workbookFile, sheetRef.getRelId());
        if (sheetPart == null)
            throw new NopException(ERR_XLSX_NULL_REL_PART).param(ARG_TYPE, "sheet").param(ARG_REL_ID, sheetRef.getRelId());
        CommentsPart comments = getCommentsTable(sheetPart);

        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, sheetRef.getName());

        SheetNodeHandler handler = new SheetNodeHandler(sharedStringsTable, contentsHandler);
        sheetPart.processXml(handler, null);

        ExcelSheet sheet = contentsHandler.getSheet();
        sheet.setLocation(pkg.getLocation());
        sheet.setDefaultColumnWidth(ExcelConstants.DEFAULT_COL_WIDTH * UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT);

        if (comments != null) {
            comments.forEachComment((cellPos, comment) -> {
                ICellView cell = sheet.getTable().getCell(cellPos.getRowIndex(), cellPos.getColIndex());
                if (cell != null) {
                    ExcelCell ec = (ExcelCell) cell.getRealCell();
                    ec.setComment(comment.getComment());
                } else {
                    ExcelCell ec = new ExcelCell();
                    ec.setComment(comment.getComment());
                    sheet.getTable().setCell(cellPos.getRowIndex(), cellPos.getColIndex(), ec);
                }
            });
        }

        return sheet;
    }

    static class SimpleSheetContentsHandler implements SheetContentsHandler {
        private final ExcelWorkbook workbook;

        private final ExcelSheet sheet = new ExcelSheet();
        private ExcelTable table = sheet.getTable();

        public SimpleSheetContentsHandler(ExcelWorkbook workbook, String sheetName) {
            this.workbook = workbook;
            this.sheet.setName(sheetName);
        }

        public ExcelSheet getSheet() {
            return sheet;
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
        public void startRow(int rowNum, Double height) {
            table.makeRow(rowNum).setHeight(height);
        }

        @Override
        public void endRow(int rowNum) {
        }

        @Override
        public void cell(CellPosition cellRef, Object value, int styleId) {
            ExcelCell cell = table.newCell();
            cell.setLocation(new SourceLocation(workbook.resourcePath(), 0, 0, 0, 0,
                    sheet.getName(), cellRef.toABString(), null));
            if (styleId >= 0) {
                cell.setStyleId(String.valueOf(styleId));
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

        ExcelStyle getStyle(ExcelWorkbook wk, ExcelCell ec) {
            String styleId = ec.getStyleId();
            if (styleId == null)
                return null;
            return wk.getStyle(styleId);
        }
    }

}