/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.ITable;
import io.nop.core.resource.component.AbstractComponentModel;

import java.util.Collection;
import java.util.List;

public abstract class AbstractRow extends AbstractComponentModel implements IRow {

    private static final long serialVersionUID = 6917319826479925078L;

    private ITable<? extends IRow> table;

    private String id;

    @Override
    public String getStyleId() {
        return null;
    }

    @Override
    public Double getHeight() {
        return null;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        checkAllowChange();
        this.id = id;
    }

    public void freeze(boolean cascade) {
        super.freeze(cascade);
        if (cascade) {
            for (ICell cell : getCells()) {
                cell.freeze(true);
            }
        }
    }

    /*
     * 因为行中需要记录proxy, 所以单行无法实现copy语义 protected void copyTo(BaseRow row) { row.rowId = rowId; row.customProps =
     * MapHelper.clone(customProps); row.model = model; }
     */

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Row[");
        if (getId() != null) {
            sb.append("id=").append(getId()).append(',');
        }
        sb.append("colCount=").append(getCells().size());
        appendInfo(sb);
        sb.append("]");
        return sb.toString();
    }

    protected void appendInfo(StringBuilder sb) {
    }

    @JsonIgnore
    public boolean isEmpty() {
        return getCells().isEmpty();
    }

    @JsonIgnore
    @Override
    public int getColCount() {
        return getCells().size();
    }

    @Override
    public ICell getCell(int i) {
        List<? extends ICell> cells = getCells();
        if (i < 0 || i >= cells.size())
            return null;
        return cells.get(i);
    }

    @Override
    public void internalSetCell(int colIndex, ICell cell) {
        checkAllowChange();
        if (cell != null)
            cell.setRow(this);

        List cells = getCells();
        for (int j = cells.size(); j <= colIndex; j++) {
            cells.add(null);
        }
        cells.set(colIndex, cell);
    }

    @Override
    public void internalRemoveCell(int colIndex) {
        checkAllowChange();
        List cells = getCells();
        if (colIndex >= cells.size())
            return;
        cells.remove(colIndex);
    }

    @Override
    public void internalInsertCell(int colIndex, ICell cell) {
        checkAllowChange();
        if (cell != null)
            cell.setRow(this);

        List cells = getCells();
        if (cells.size() < colIndex) {
            for (int j = cells.size(); j < colIndex; j++) {
                cells.add(null);
            }
            cells.add(cell);
        } else {
            cells.add(colIndex, cell);
        }
    }

    @Override
    public void internalAddCells(Collection<? extends ICell> cells) {
        checkAllowChange();
        if (cells != null) {
            for (ICell cell : cells) {
                internalAddCell(cell);
            }
        }
    }

    @Override
    public void internalAddCell(ICell cell) {
        checkAllowChange();
        if (cell != null)
            cell.setRow(this);

        List cells = getCells();
        cells.add(cell);
    }


    @JsonIgnore
    @Override
    public ITable<? extends IRow> getTable() {
        return table;
    }

    public void setTable(ITable<? extends IRow> table) {
        checkAllowChange();
        this.table = table;
    }

}