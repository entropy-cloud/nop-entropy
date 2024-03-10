/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.api.core.util.Guard;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IColumnConfig;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubTableView implements ITableView {
    private final ITableView table;
    private final CellRange range;
    private int headerCount;
    private int footerCount;
    private int sideCount;
    private List<IRowView> rows;
    private int rowCount;

    public SubTableView(ITableView table, CellRange range) {
        Guard.checkArgument(range.getLastRowIndex() < table.getRowCount(), "invalid table range");
        this.table = table;
        this.range = range;
        this.rowCount = Math.min(table.getRowCount() - 1, range.getLastRowIndex()) - range.getFirstRowIndex() + 1;
    }

    public CellRange getRange() {
        return range;
    }

    public String getId() {
        return table.getId();
    }

    @Override
    public String getStyleId() {
        return table.getStyleId();
    }

    @Override
    public int getRowCount() {
        return rowCount;
    }

    @Override
    public int getColCount() {
        return range.getColCount();
    }

    int mapRow(int rowIndex) {
        return range.getFirstColIndex() + rowIndex;
    }

    int mapCol(int colIndex) {
        return range.getFirstColIndex() + colIndex;
    }

    @Override
    public IRowView getRow(int rowIndex) {
        int cnt = this.getRowCount();
        if (rowIndex >= cnt)
            return null;

        return getRows().get(rowIndex);
    }

    @Override
    public ICellView getCell(int rowIndex, int colIndex) {
        return table.getCell(mapRow(rowIndex), mapCol(colIndex));
    }

    @Override
    public ITableView getSubTable(CellRange range) {
        return new SubTableView(table, range.offset(range.getFirstRowIndex(), range.getFirstColIndex()));
    }

    @Override
    public int getHeaderCount() {
        return headerCount;
    }

    @Override
    public int getSideCount() {
        return sideCount;
    }

    @Override
    public int getFooterCount() {
        return footerCount;
    }

    @Override
    public List<? extends IColumnConfig> getCols() {
        List<? extends IColumnConfig> colTypes = table.getCols();
        if (colTypes == null)
            return null;

        int begin = range.getFirstColIndex();
        if (begin >= colTypes.size())
            return Collections.emptyList();

        int end = range.getLastColIndex() + 1;
        if (end > colTypes.size())
            end = colTypes.size();
        return colTypes.subList(begin, end);
    }

    public IColumnConfig getCol(int colIndex) {
        return table.getCol(colIndex + range.getFirstColIndex());
    }

    @Override
    public List<? extends IRowView> getRows() {
        if (rows == null) {
            rows = new ArrayList<>(getRowCount());
            for (int i = 0, n = getRowCount(); i < n; i++) {
                IRowView baseRow = table.getRow(mapRow(i));
                SubRowView row = baseRow == null ? null : new SubRowView(this, baseRow, range);
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Object prop_get(String propName) {
        return table.prop_get(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        return table.prop_has(propName);
    }

    @Override
    public void prop_set(String propName, Object value) {
        table.prop_set(propName, value);
    }
}