/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.core.model.table.IColumnConfig;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.excel.ExcelConstants;

import java.util.List;

import static io.nop.excel.util.UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT;

public interface IExcelSheet {
    String getName();

    XptSheetModel getModel();

    ExcelPageSetup getPageSetup();

    ExcelPageMargins getPageMargins();

    ExcelPageBreaks getPageBreaks();

    Double getDefaultRowHeight();

    Double getDefaultColumnWidth();

    IExcelTable getTable();

    List<ExcelImage> getImages();

    default double getWidth(int fromColIndex, int toColIndex) {
        double sum = 0;
        for (int i = fromColIndex; i <= toColIndex; i++) {
            IColumnConfig col = getTable().getCol(i);
            if(col != null && col.isHidden())
                continue;

            Double d;
            if (col == null || col.getWidth() == null) {
                d = getDefaultColumnWidth();
            } else {
                d = col.getWidth();
            }
            if (d == null)
                d = ExcelConstants.DEFAULT_COL_WIDTH * DEFAULT_CHARACTER_WIDTH_IN_PT;
            sum += d;
        }
        return sum;
    }

    default double getHeight(int fromRowIndex, int toRowIndex) {
        double sum = 0;
        for (int i = fromRowIndex; i <= toRowIndex; i++) {
            IRowView row = getTable().getRow(i);
            if(row != null && row.isHidden())
                continue;

            Double d;
            if (row == null || row.getHeight() == null) {
                d = getDefaultRowHeight();
            } else {
                d = row.getHeight();
            }
            if (d == null)
                d = 14.25;
            sum += d;
        }
        return sum;
    }

    default double getCellLeft(int colIndex) {
        return getWidth(0, colIndex - 1);
    }

    default double getCellTop(int rowIndex) {
        return getHeight(0, rowIndex - 1);
    }
}
