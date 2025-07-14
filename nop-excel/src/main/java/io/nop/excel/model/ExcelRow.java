/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.core.model.table.ICell;
import io.nop.excel.model._gen._ExcelRow;

import java.util.ArrayList;

public class ExcelRow extends _ExcelRow implements IExcelRow {
    public ExcelRow() {
        setCells(new ArrayList<>());
    }

    @Override
    public ICell makeCell(int colIndex) {
        ICell cell = getCell(colIndex);
        if (cell == null) {
            ExcelCell ec = new ExcelCell();
            internalSetCell(colIndex, ec);
            cell = ec;
        }
        return cell;
    }

    public XptRowModel makeModel() {
        XptRowModel model = getModel();
        if (model == null) {
            model = new XptRowModel();
            setModel(model);
        }
        return model;
    }
}
