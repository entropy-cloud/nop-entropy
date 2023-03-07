/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.ICell;
import io.nop.excel.model._gen._ExcelTable;

import java.util.ArrayList;

public class ExcelTable extends _ExcelTable implements INeedInit {
    public ExcelTable() {
        setRows(new ArrayList<>());
    }

    @Override
    public ExcelRow newRow() {
        return new ExcelRow();
    }

    public ExcelCell newCell() {
        return new ExcelCell();
    }


    public ExcelTable cloneInstance() {
        ExcelTable table = new ExcelTable();
        table.setLocation(getLocation());
        table.setCols(CollectionHelper.cloneList(getCols()));

        this.forEachRealCell((cell, rowIndex, colIndex) -> {
            table.setCell(rowIndex, colIndex, (ICell) cell.cloneInstance());
            return ProcessResult.CONTINUE;
        });
        return table;
    }

    @Override
    public void init() {
        this.normalizeMergeRanges();
    }
}
