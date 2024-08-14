/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICell;
import io.nop.excel.model._gen._ExcelTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ExcelTable extends _ExcelTable implements INeedInit, IExcelTable {
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

    @Override
    public ExcelCell newProxyCell(ICell cell, int rowOffset, int colOffset) {
        ExcelCell proxy = new ExcelCell();
        proxy.setRealCell((ExcelCell) cell);
        proxy.setRowOffset(rowOffset);
        proxy.setColOffset(colOffset);
        return proxy;
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

    public Set<StdDataType> getColTypes(int colIndex) {
        Set<StdDataType> types = new HashSet<>();
        for (int i = 0, n = getRowCount(); i < n; i++) {
            ExcelRow row = getRow(i);
            ICell cell = row.getCell(colIndex);
            if (cell == null || cell.isProxyCell())
                continue;

            ExcelCell ec = (ExcelCell) cell;
            StdDataType dataType = ec.getType();
            if (dataType == null && StringHelper.isEmptyObject(ec.getValue())) {
                dataType = StdDataType.guessFromValue(ec.getValue());
            }
            if (dataType != null) {
                types.add(dataType);
            }
        }
        return types;
    }
}
