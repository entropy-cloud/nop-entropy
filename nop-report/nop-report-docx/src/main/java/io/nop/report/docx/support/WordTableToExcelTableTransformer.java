package io.nop.report.docx.support;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.IColumnConfig;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelTable;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;

public final class WordTableToExcelTableTransformer {
    private WordTableToExcelTableTransformer() {
    }

    public static ExcelTable transform(WordTable source) {
        ExcelTable target = new ExcelTable();
        target.setCols(new java.util.ArrayList<>());
        target.setId(source.getId());
        target.setStyleId(source.getStyleId());
        target.setHeaderCount(source.getHeaderCount());
        target.setSideCount(source.getSideCount());
        target.setFooterCount(source.getFooterCount());
        target.setLocation(source.getLocation());
        source.copyExtPropsTo(target);

        if (source.getCols() != null) {
            for (IColumnConfig col : source.getCols()) {
                ExcelColumnConfig excelCol = new ExcelColumnConfig();
                if (col != null) {
                    excelCol.setWidth(col.getWidth());
                    excelCol.setStyleId(col.getStyleId());
                    excelCol.setHidden(col.isHidden());
                    if (col instanceof WordTableColumnConfig) {
                        WordTableColumnConfig wordCol = (WordTableColumnConfig) col;
                        excelCol.setLocation(wordCol.getLocation());
                        wordCol.copyExtPropsTo(excelCol);
                    }
                }
                target.getCols().add(excelCol);
            }
        }

        source.forEachRealCell((cell, rowIndex, colIndex) -> {
            WordTableCell wordCell = (WordTableCell) cell;
            ExcelCell excelCell = new ExcelCell();
            excelCell.setId(wordCell.getId());
            excelCell.setStyleId(wordCell.getStyleId());
            excelCell.setLocation(wordCell.getLocation());
            excelCell.setComment(wordCell.getComment());
            excelCell.setValue(wordCell.getValue());
            excelCell.setMergeAcross(wordCell.getMergeAcross());
            excelCell.setMergeDown(wordCell.getMergeDown());
            wordCell.copyExtPropsTo(excelCell);
            for (String name : wordCell.prop_names()) {
                excelCell.makeModel().prop_set(name, wordCell.prop_get(name));
            }
            target.setCell(rowIndex, colIndex, excelCell);
            return ProcessResult.CONTINUE;
        });

        for (int i = 0, n = source.getRowCount(); i < n; i++) {
            WordTableRow row = source.getRow(i);
            if (row == null) {
                continue;
            }
            ExcelRow excelRow = target.makeRow(i);
            excelRow.setLocation(row.getLocation());
            excelRow.setId(row.getId());
            excelRow.setStyleId(row.getStyleId());
            excelRow.setHeight(row.getHeight());
            excelRow.setHidden(row.isHidden());
            row.copyExtPropsTo(excelRow);
            for (String name : row.prop_names()) {
                excelRow.makeModel().prop_set(name, row.prop_get(name));
            }
        }

        return target;
    }
}
