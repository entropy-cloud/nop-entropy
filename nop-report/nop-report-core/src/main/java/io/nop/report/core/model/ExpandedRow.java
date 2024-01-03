/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.model;

import io.nop.core.model.table.IRowView;
import io.nop.excel.model.XptRowModel;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class ExpandedRow implements IRowView {
    private XptRowModel model;

    /**
     * 展开完毕之后设置assignedRowIndex，避免频繁查找行下标
     */
    private int assignedRowIndex = -1;

    private String id;
    private String styleId;
    private ExpandedCell firstCell;

    private ExpandedTable table;

    private Double height;
    private boolean hidden;
    private boolean removed;

    /**
     * 单元格展开时新建的行
     */
    private boolean newlyCreated;

    public boolean isNewlyCreated() {
        return newlyCreated;
    }

    public void setNewlyCreated(boolean newlyCreated) {
        this.newlyCreated = newlyCreated;
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    @Override
    public Double getHeight() {
        return height;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public int getColCount() {
        return table.getColCount();
    }

    @Override
    public ExpandedCell getCell(int colIndex) {
        ExpandedCell cell = this.firstCell;
        for (int i = 0; i < colIndex; i++) {
            if (cell == null)
                return null;
            cell = cell.getRight();
        }
        return cell;
    }

    @Nonnull
    @Override
    public List<ExpandedCell> getCells() {
        List<ExpandedCell> ret = new ArrayList<>();
        ExpandedCell cell = this.firstCell;
        while (cell != null) {
            ret.add(cell);
            cell = cell.getRight();
        }
        return ret;
    }

    @Override
    public Iterator<ExpandedCell> iterator() {
        return new RowIterator(firstCell);
    }

    static class RowIterator implements Iterator<ExpandedCell> {
        private ExpandedCell next;

        public RowIterator(ExpandedCell next) {
            this.next = next;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public ExpandedCell next() {
            if (!hasNext())
                throw new NoSuchElementException();

            ExpandedCell next = this.next;
            this.next = next.getRight();
            return next;
        }
    }

    @Override
    public Object prop_get(String propName) {
        return model.prop_get(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        return model.prop_has(propName);
    }

    @Override
    public void prop_set(String propName, Object value) {
        model.prop_set(propName, value);
    }

    public void forEachRealCell(Consumer<ExpandedCell> action) {
        ExpandedCell cell = firstCell;
        do {
            if (!cell.isProxyCell()) {
                action.accept(cell);
            }
            cell = cell.getRight();
            if (cell == null)
                break;
        } while (true);
    }

    public void forEachCell(Consumer<ExpandedCell> action) {
        ExpandedCell cell = firstCell;
        do {
            action.accept(cell);
            cell = cell.getRight();
            if (cell == null)
                break;
        } while (true);
    }

    public static void visitTwoRow(ExpandedRow row1, ExpandedRow row2,
                                   ITwoCellProcessor processor) {
        ExpandedCell c1 = row1.getFirstCell();
        ExpandedCell c2 = row2.getFirstCell();
        int index = 0;
        while (c1 != null) {
            processor.process(c1, c2, index);
            index++;
            c1 = c1.getRight();
            c2 = c2.getRight();
        }
    }

    public static void visitThreeRow(ExpandedRow row1, ExpandedRow row2,
                                     ExpandedRow row3,
                                     IThreeCellProcessor processor) {
        ExpandedCell c1 = row1.getFirstCell();
        ExpandedCell c2 = row2.getFirstCell();
        ExpandedCell c3 = row3.getFirstCell();
        int index = 0;
        while (c1 != null) {
            processor.process(c1, c2, c3, index);
            index++;
            c1 = c1.getRight();
            c2 = c2.getRight();
            c3 = c3.getRight();
        }
    }

    public XptRowModel getModel() {
        return model;
    }

    public void setModel(XptRowModel model) {
        this.model = model;
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    public ExpandedCell getFirstCell() {
        return firstCell;
    }

    public void setFirstCell(ExpandedCell firstCell) {
        this.firstCell = firstCell;
    }

    public ExpandedTable getTable() {
        return table;
    }

    public void setTable(ExpandedTable table) {
        this.table = table;
    }

    public int getRowIndex() {
        if (assignedRowIndex >= 0)
            return assignedRowIndex;
        return table.getRows().indexOf(this);
    }

    public void setAssignedRowIndex(int assignedRowIndex) {
        this.assignedRowIndex = assignedRowIndex;
    }

    public void useNextRowStyle() {
        ExpandedCell cell = this.firstCell;
        while (cell != null) {
            ExpandedCell down = cell.getDown();
            if (down != null) {
                cell.setStyleId(down.getStyleId());
            }
            cell = cell.getRight();
        }
    }
}