/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.excel.format.ExcelFormatHelper;
import io.nop.excel.model._gen._ExcelWorkbook;

import java.text.Format;

import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_EXCEL_UNKNOWN_SHEET;

public class ExcelWorkbook extends _ExcelWorkbook implements INeedInit {
    public ExcelWorkbook() {

    }

    public boolean isEnableDump() {
        XptWorkbookModel model = getModel();
        return model == null ? false : Boolean.TRUE.equals(model.getDump());
    }

    public boolean shouldRemoveHiddenCell() {
        XptWorkbookModel model = getModel();
        return model == null ? false : model.isRemoveHiddenCell();
    }

    @Override
    public void init() {
        for (ExcelSheet sheet : getSheets()) {
            sheet.init();
        }
    }

    public Object getExcelFormatValue(ExcelCell cell) {
        if (cell.getStyleId() == null)
            return cell.getValue();

        ExcelStyle style = getStyle(cell.getStyleId());
        if (style != null && style.getNumberFormat() != null) {
            Format format = ExcelFormatHelper.getFormat(style.getNumberFormat());
            if (format != null) {
                return format.format(cell.getValue());
            }
        }

        return cell.getValue();
    }

    public ExcelSheet requireSheet(String sheetName) {
        ExcelSheet sheet = getSheet(sheetName);
        if (sheet == null) {
            throw new NopException(ERR_EXCEL_UNKNOWN_SHEET)
                    .source(this)
                    .param(ARG_SHEET_NAME, sheetName);
        }
        return sheet;
    }

    public ExcelSheet removeSheet(String sheetName) {
        ExcelSheet sheet = getSheet(sheetName);
        if (sheet != null) {
            getSheets().remove(sheet);
        }
        return sheet;
    }
}
