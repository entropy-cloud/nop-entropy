/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.core.model.table.ICell;
import io.nop.excel.model._gen._ExcelRow;

import java.util.ArrayList;

public class ExcelRow extends _ExcelRow {
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
}
