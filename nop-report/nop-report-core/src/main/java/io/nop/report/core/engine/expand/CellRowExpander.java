/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.expand;

import io.nop.excel.model.XptCellModel;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedTable;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行单个单元格的行展开算法。行展开时会复制所有的子单元格，并延展所有与当前单元格处于同一行的父单元格
 */
public class CellRowExpander extends AbstractCellExpander {
    public static final CellRowExpander INSTANCE = new CellRowExpander();

    /**
     * 标记单元格被删除，它所有的子单元格也标记为被删除
     *
     * @param cell
     */
    @Override
    protected void removeCell(ExpandedCell cell) {
        cell.setRemoved(true);
        if (cell.getRowDescendants() != null) {
            for (List<ExpandedCell> list : cell.getRowDescendants().values()) {
                for (ExpandedCell child : list) {
                    child.setRemoved(true);
                }
            }
        }

        int startIndex = cell.getRowIndex() - cell.getModel().getRowExpandOffset();
        int endIndex = startIndex + cell.getModel().getRowExpandSpan();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i < endIndex; i++) {
            table.getRow(i).setRemoved(true);
        }
    }

    @Override
    protected void extendCells(ExpandedCell cell, ExpandCounter counter) {
        XptCellModel xptModel = cell.getModel();
        ExpandedRow row = cell.getRow();
        int rowIndex = row.getRowIndex();
        int expandSpan = xptModel == null ? 1 : xptModel.getRowExpandSpan();
        int expandOffset = xptModel == null ? 0 : xptModel.getRowExpandOffset();

        ExpandedTable table = row.getTable();

        int startIndex = rowIndex + expandOffset;

        Map<ExpandedCell, Boolean> needExtends = new IdentityHashMap<>();

        // 收集需要被扩展的单元格
        for (int i = 0; i < expandSpan; i++) {
            int index = startIndex + i;
            ExpandedRow r = table.getRow(index);
            r.forEachCell(c -> {
                ExpandedCell realCell = c.getRealCell();
                if (realCell.getName() != null) {
                    if (xptModel.getRowExtendCells().containsKey(realCell.getName())) {
                        needExtends.put(realCell, Boolean.TRUE);
                    }
                }
            });
        }

        if (counter.incSpan == counter.realIncSpan) {
            for (ExpandedCell needExtend : needExtends.keySet()) {
                needExtend.setMergeDown(needExtend.getMergeDown() + counter.incSpan);
                needExtend.markProxy();
            }
        } else {
            // 兄弟节点展开导致共享了部分新增列
            for (ExpandedCell needExtend : needExtends.keySet()) {
                int span = counter.incSpan - skipExtendSpan(table, needExtend, cell, counter);
                needExtend.setMergeDown(needExtend.getMergeDown() + span);
                needExtend.markProxy();
            }
        }
    }

    private int skipExtendSpan(ExpandedTable table, ExpandedCell needExtend, ExpandedCell cell, ExpandCounter counter) {
        int skipSpan = 0;
        for (int i = counter.minReuse; i <= counter.maxReuse; i++) {
            ExpandedRow row = table.getRow(i);
            if (row.getGeneratorCell() != null && row.getGeneratorCell() != cell
                    && row.getGeneratorCell().getModel().getRowExtendCells().containsKey(needExtend.getName()))
                skipSpan++;
        }
        return skipSpan;
    }


    @Override
    protected void duplicateCell(ExpandedCell cell, int expandIndex, Object expandValue,
                                 Collection<ExpandedCell> processing, ExpandCounter counter) {
        XptCellModel xptModel = cell.getModel();
        ExpandedRow row = cell.getRow();
        int rowIndex = row.getRowIndex();
        int expandSpan = xptModel == null ? 1 : xptModel.getRowExpandSpan();
        int expandOffset = xptModel == null ? 0 : xptModel.getRowExpandOffset();

        // 记录复制的单元格所对应的原始单元格
        Map<ExpandedCell, ExpandedCell> cellMap = new IdentityHashMap<>();

        ExpandedTable table = row.getTable();

        int startIndex = rowIndex + expandOffset;

        int incSpan = 0;

        for (int i = 0; i < expandSpan; i++) {
            int index = startIndex + i;
            ExpandedRow r = table.getRow(index);
            int newIndex = index + expandSpan * expandIndex;

            // 如果在模板中预留了一些空间（expandInplaceCount），则会复用这些空间，而不会插入行
            boolean needInsert = isNeedInsert(expandIndex,
                    xptModel == null || xptModel.getExpandInplaceCount() == null ? -1 : xptModel.getExpandInplaceCount());

            if (needInsert) {
                needInsert = !isAllowReuse(cell, table, newIndex);
                if (counter.minReuse == Integer.MAX_VALUE)
                    counter.minReuse = newIndex;
                if (counter.maxReuse < newIndex)
                    counter.maxReuse = newIndex;

                counter.incSpan++;
                if (needInsert)
                    counter.realIncSpan++;
            }

            ExpandedRow newRow = needInsert ? table.insertEmptyRow(newIndex) : table.makeRow(newIndex);
            if (needInsert) {
                newRow.setNewlyCreated(true);
                newRow.setGeneratorCell(cell);
                newRow.setHeight(r.getHeight());
                newRow.setModel(r.getModel());
                newRow.useNextRowStyle();
            }
            duplicateRow(r, cell, expandIndex, expandValue, newRow, cellMap, processing);
        }

        initRowParentAndColParent(cellMap);

        addNewCellToParentDescendants(table, cellMap);
    }

    private boolean isAllowReuse(ExpandedCell cell, ExpandedTable table, int index) {
        if (index >= table.getRowCount())
            return false;

        ExpandedRow row = table.getRow(index);
        if (row.isNewlyCreated()) {
            // 同一个父节点的兄弟节点可以复用展开列
            return cell.getRowParent() == row.getGeneratorCell().getRowParent();
        }

        return false;
    }


    // 如果是待复制单元格或者其子单元格，则复制，否则延展。
    private void duplicateRow(ExpandedRow row, ExpandedCell cell, int expandIndex,
                              Object expandValue, ExpandedRow newRow,
                              Map<ExpandedCell, ExpandedCell> cellMap,
                              Collection<ExpandedCell> processing) {
        newRow.setModel(row.getModel());

        ExpandedCell nextCell = row.getFirstCell();
        ExpandedCell newCell = newRow.getFirstCell();

        while (nextCell != null) {
            if (cell == nextCell) {
                copyCellValue(nextCell, newCell, true);
                newCell.setExpandIndex(expandIndex);
                newCell.setExpandValue(expandValue);
                cellMap.put(nextCell, newCell);
            } else if (cell.getRowDescendants() != null && cell.getRowDescendants().containsKey(nextCell.getName())) {
                List<ExpandedCell> cells = cell.getRowDescendants().get(nextCell.getName());
                if (cells.contains(nextCell)) {
                    copyCellValue(nextCell, newCell, true);
                    cellMap.put(nextCell, newCell);
                    if (newCell.isExpandable())
                        processing.add(newCell);
                } else {
                    newCell.setStyleId(nextCell.getStyleId());
                }
            } else if (!nextCell.isProxyCell()) {
                newCell.setStyleId(nextCell.getStyleId());
            }
            nextCell = nextCell.getRight();
            newCell = newCell.getRight();
        }
    }

    private boolean isNeedInsert(int expandIndex, int expandInplaceCount) {
        if (expandInplaceCount <= 0)
            return true;
        return expandIndex >= expandInplaceCount;
    }
}