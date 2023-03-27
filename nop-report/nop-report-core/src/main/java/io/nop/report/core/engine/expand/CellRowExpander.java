/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
        for (List<ExpandedCell> list : cell.getRowDescendants().values()) {
            for (ExpandedCell child : list) {
                child.setRemoved(true);
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
    protected void extendCells(ExpandedCell cell, int expandCount) {
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

        int incSpan = expandSpan * (expandCount - 1);

        for (ExpandedCell needExtend : needExtends.keySet()) {
            needExtend.setMergeDown(needExtend.getMergeDown() + incSpan);
            needExtend.markProxy();
        }
    }

    @Override
    protected void duplicateCell(ExpandedCell cell, int expandIndex, Object expandValue, Collection<ExpandedCell> processing) {
        XptCellModel xptModel = cell.getModel();
        ExpandedRow row = cell.getRow();
        int rowIndex = row.getRowIndex();
        int expandSpan = xptModel == null ? 1 : xptModel.getRowExpandSpan();
        int expandOffset = xptModel == null ? 0 : xptModel.getRowExpandOffset();

        // 记录复制的单元格所对应的原始单元格
        Map<ExpandedCell, ExpandedCell> cellMap = new IdentityHashMap<>();

        ExpandedTable table = row.getTable();

        int startIndex = rowIndex + expandOffset;

        for (int i = 0; i < expandSpan; i++) {
            int index = startIndex + i;
            ExpandedRow r = table.getRow(index);
            int newIndex = index + expandSpan * expandIndex;

            boolean needInsert = isNeedInsert(expandIndex,
                    xptModel == null || xptModel.getExpandInplaceCount() == null ? -1 : xptModel.getExpandInplaceCount());

            ExpandedRow newRow = needInsert ? table.insertEmptyRow(newIndex) : table.makeRow(newIndex);
            if(needInsert)
                newRow.setHeight(r.getHeight());
            duplicateRow(r, cell, expandIndex, expandValue, newRow, cellMap, processing);
        }

        initRowChildren(cellMap);

        for (ExpandedCell newCell : cellMap.values()) {
            table.addNamedCell(newCell);

            if (newCell.getMergeAcross() > 0 || newCell.getMergeDown() > 0)
                newCell.markProxy();

            if (newCell.getRowParent() != null) {
                newCell.getRowParent().addRowChild(newCell);
            }


            if (newCell.getColParent() != null) {
                newCell.getColParent().addColChild(newCell);
            }
        }
    }

    // 如果是待复制单元格或者其子单元格，则复制，否则延展。
    private void duplicateRow(ExpandedRow row, ExpandedCell cell, int expandIndex,
                              Object expandValue, ExpandedRow newRow,
                              Map<ExpandedCell, ExpandedCell> cellMap,
                              Collection<ExpandedCell> processing) {
        newRow.setModel(row.getModel());

        ExpandedCell nextCell = row.getFirstCell();
        ExpandedCell newCell = newRow.getFirstCell();

        XptCellModel xptModel = cell.getModel();

        while (nextCell != null) {
            if (cell == nextCell) {
                copyCellValue(nextCell, newCell);
                newCell.setExpandIndex(expandIndex);
                newCell.setExpandValue(expandValue);
                cellMap.put(nextCell, newCell);
            } else if (xptModel != null && xptModel.getRowDuplicateCells().containsKey(nextCell.getName())) {
                copyCellValue(nextCell, newCell);
                cellMap.put(nextCell, newCell);
                if (newCell.isExpandable())
                    processing.add(newCell);
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

    private void initRowChildren(Map<ExpandedCell, ExpandedCell> cellMap) {
        for (Map.Entry<ExpandedCell, ExpandedCell> entry : cellMap.entrySet()) {
            ExpandedCell cell = entry.getKey();
            ExpandedCell newCell = entry.getValue();

            ExpandedCell parent = cell.getRowParent();
            if (parent != null) {
                ExpandedCell newParent = cellMap.get(parent);
                if (newParent == null)
                    newParent = parent;
                newCell.setRowParent(newParent);
            }
//
//            if (cell.hasRowDescendant()) {
//                Map<String, List<ExpandedCell>> children = getNewListMap(cell.getRowDescendants(), cellMap);
//                newCell.setRowDescendants(children);
//            }
        }
    }
}