/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;

import java.util.List;

public class SubRowView implements IRowView {
    private final ITableView table;
    private final IRowView row;
    private final CellRange range;

    public SubRowView(ITableView table, IRowView row, CellRange range) {
        this.table = table;
        this.row = row;
        this.range = range;
    }

    @Override
    public int getColCount() {
        return range.getColCount();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    int mapCol(int colIndex) {
        return range.getFirstColIndex() + colIndex;
    }

    @Override
    public ICellView getCell(int colIndex) {
        return row.getCell(mapCol(colIndex));
    }

    @Override
    public String getId() {
        return row.getId();
    }

    @Override
    public String getStyleId() {
        return row.getStyleId();
    }

    @Override
    public Double getHeight() {
        return row.getHeight();
    }

    @Override
    public boolean isHidden() {
        return row.isHidden();
    }

    @Override
    public ITableView getTable() {
        return table;
    }

    @Override
    public List<? extends ICellView> getCells() {
        return row.getCells().subList(range.getFirstColIndex(), range.getLastColIndex() + 1);
    }

    @Override
    public Object prop_get(String propName) {
        return row.prop_get(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        return row.prop_has(propName);
    }

    @Override
    public void prop_set(String propName, Object value) {
        row.prop_set(propName, value);
    }
}