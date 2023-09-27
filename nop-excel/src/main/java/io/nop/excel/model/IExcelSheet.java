/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    ExcelPageSetup getPageSetup();

    ExcelPageMargins getPageMargins();

    ExcelPageBreaks getPageBreaks();

    Double getDefaultRowHeight();

    Double getDefaultColumnWidth();

    ITableView getTable();

    List<ExcelImage> getImages();

    default double getCellLeft(int colIndex) {
        double sum = 0;
        for (int i = 0; i < colIndex; i++) {
            IColumnConfig col = getTable().getCol(i);
            Double d;
            if (col == null || col.getWidth() == null) {
                d = getDefaultColumnWidth();
            }else{
                d = col.getWidth();
            }
            if (d == null)
                d = ExcelConstants.DEFAULT_COL_WIDTH * DEFAULT_CHARACTER_WIDTH_IN_PT;
            sum += d;
        }
        return sum;
    }

    default double getCellTop(int rowIndex) {
        double sum = 0;
        for (int i = 0; i < rowIndex; i++) {
            IRowView row = getTable().getRow(i);
            Double d;
            if (row == null || row.getHeight() == null) {
                d = getDefaultRowHeight();
            }else{
                d = row.getHeight();
            }
            if (d == null)
                d = 14.25;
            sum += d;
        }
        return sum;
    }
}
