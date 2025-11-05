package io.nop.excel.util;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.IColumnConfig;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.IExcelTable;

import java.util.ArrayList;
import java.util.List;

public class ExcelModelHelper {
    public static ExcelSheet copySheet(IExcelSheet sheet) {
        ExcelSheet ret = new ExcelSheet();
        ret.setModel(sheet.getModel());
        ret.setName(sheet.getName());
        ret.setPageSetup(sheet.getPageSetup());
        ret.setPageMargins(sheet.getPageMargins());
        ret.setPageBreaks(sheet.getPageBreaks());
        ret.setDefaultColumnWidth(sheet.getDefaultColumnWidth());
        ret.setDefaultRowHeight(sheet.getDefaultRowHeight());
        if (sheet.getImages() != null)
            ret.setImages(new ArrayList<>(sheet.getImages()));

        copyTable(sheet.getTable(), ret.getTable());
        return ret;
    }

    public static void copyTable(IExcelTable table, ExcelTable ret) {
        List<ExcelColumnConfig> cols = new ArrayList<>(table.getColCount());
        for (IColumnConfig colConfig : table.getCols()) {
            ExcelColumnConfig retCol = new ExcelColumnConfig();
            retCol.setHidden(colConfig.isHidden());
            retCol.setStyleId(colConfig.getStyleId());
            retCol.setWidth(colConfig.getWidth());
            cols.add(retCol);
        }
        ret.setCols(cols);

        table.forEachRealCell((cell, rowIndex, colIndex) -> {
            ExcelCell retCell = new ExcelCell();
            retCell.setMergeAcross(cell.getMergeAcross());
            retCell.setMergeDown(cell.getMergeDown());
            retCell.setStyleId(cell.getStyleId());
            retCell.setValue(cell.getValue());
            retCell.setLinkUrl(cell.getLinkUrl());
            retCell.setId(cell.getId());
            retCell.setFormula(cell.getFormula());
            ret.setCell(rowIndex, colIndex, retCell);
            return ProcessResult.CONTINUE;
        });
    }
}
