/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.excel.model.constants.ExcelModelConstants;

import java.util.List;

public interface IExcelSheet {
    String getName();

    void setName(String name);

    XptSheetModel getModel();

    ExcelPageSetup getPageSetup();

    ExcelPageMargins getPageMargins();

    ExcelPageBreaks getPageBreaks();

    ExcelSheetProtection getSheetProtection();

    List<ExcelDataValidation> getDataValidations();

    ExcelDataValidation buildDataValidation(String id);

    Double getDefaultRowHeight();

    Double getDefaultColumnWidth();

    default double defaultRowHeight() {
        Double d = getDefaultRowHeight();
        return d == null ? ExcelModelConstants.DEFAULT_ROW_HEIGHT_IN_PT : d;
    }

    default double defaultColumnWidth() {
        Double d = getDefaultColumnWidth();
        return d == null ? ExcelModelConstants.DEFAULT_COL_WIDTH_IN_PT : d;
    }

    IExcelTable getTable();

    List<ExcelImage> getImages();

    default double getTotalWidth() {
        return getWidth(0, getTable().getColCount() - 1);
    }

    default double getTotalHeight() {
        return getHeight(0, getTable().getRowCount() - 1);
    }

    default double getWidth(int fromColIndex, int toColIndex) {
        return getTable().getRangeWidth(fromColIndex, toColIndex, defaultColumnWidth());
    }

    default double getHeight(int fromRowIndex, int toRowIndex) {
        return getTable().getRangeHeight(fromRowIndex, toRowIndex, defaultRowHeight());
    }

    default double getCellLeft(int colIndex) {
        return getWidth(0, colIndex - 1);
    }

    default double getCellTop(int rowIndex) {
        return getHeight(0, rowIndex - 1);
    }
}
