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
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedTable;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CellColExpander extends AbstractCellExpander {
    public static final CellColExpander INSTANCE = new CellColExpander();

    /**
     * 标记单元格被删除，它所有的子单元格也标记为被删除
     *
     * @param cell
     */
    @Override
    protected void removeCell(ExpandedCell cell) {
        cell.setRemoved(true);
        if (cell.getColDescendants() != null) {
            for (List<ExpandedCell> list : cell.getColDescendants().values()) {
                for (ExpandedCell child : list) {
                    child.setRemoved(true);
                }
            }
        }

        int startIndex = cell.getColIndex() - cell.getModel().getColExpandOffset();
        int endIndex = startIndex + cell.getModel().getColExpandSpan();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i < endIndex; i++) {
            table.getCol(i).setRemoved(true);
        }
    }

    @Override
    protected void extendCells(ExpandedCell cell, ExpandCounter counter) {
        XptCellModel xptModel = cell.getModel();
        ExpandedCol col = cell.getCol();
        int colIndex = col.getColIndex();
        int expandSpan = xptModel == null ? 1 : xptModel.getColExpandSpan();
        int expandOffset = xptModel == null ? 0 : xptModel.getColExpandOffset();

        ExpandedTable table = col.getTable();

        int startIndex = colIndex + expandOffset;

        Map<ExpandedCell, Boolean> needExtends = new IdentityHashMap<>();

        // 收集需要被扩展的单元格
        for (int i = 0; i < expandSpan; i++) {
            int index = startIndex + i;
            ExpandedCol r = table.getCol(index);
            r.forEachCell(c -> {
                ExpandedCell realCell = c.getRealCell();
                if (realCell.getName() != null) {
                    if (xptModel.getColExtendCells().containsKey(realCell.getName())) {
                        needExtends.put(realCell, Boolean.TRUE);
                    }
                }
            });
        }

        if (counter.incSpan == counter.realIncSpan) {
            for (ExpandedCell needExtend : needExtends.keySet()) {
                needExtend.setMergeAcross(needExtend.getMergeAcross() + counter.incSpan);
                needExtend.markProxy();
            }
        } else {
            // 兄弟节点展开导致共享了部分新增列
            for (ExpandedCell needExtend : needExtends.keySet()) {
                int span = counter.incSpan - skipExtendSpan(table, needExtend, cell, counter);
                needExtend.setMergeAcross(needExtend.getMergeAcross() + span);
                needExtend.markProxy();
            }
        }
    }

    private int skipExtendSpan(ExpandedTable table, ExpandedCell needExtend, ExpandedCell cell, ExpandCounter counter) {
        int skipSpan = 0;
        for (int i = counter.minReuse; i <= counter.maxReuse; i++) {
            ExpandedCol col = table.getCol(i);
            if (col.getGeneratorCell() != null && col.getGeneratorCell() != cell
                    && col.getGeneratorCell().getModel().getColExtendCells().containsKey(needExtend.getName()))
                skipSpan++;
        }
        return skipSpan;
    }

    @Override
    protected void duplicateCell(ExpandedCell cell, int expandIndex, Object expandValue,
                                 Collection<ExpandedCell> processing, ExpandCounter counter) {
        XptCellModel xptModel = cell.getModel();
        ExpandedCol col = cell.getCol();
        int colIndex = col.getColIndex();
        int expandSpan = xptModel == null ? 1 : xptModel.getColExpandSpan();
        int expandOffset = xptModel == null ? 0 : xptModel.getColExpandOffset();

        // 记录复制的单元格所对应的原始单元格
        Map<ExpandedCell, ExpandedCell> cellMap = new IdentityHashMap<>();

        ExpandedTable table = col.getTable();

        int startIndex = colIndex + expandOffset;

        for (int i = 0; i < expandSpan; i++) {
            int index = startIndex + i;
            ExpandedCol r = table.getCol(index);
            int newIndex = index + expandSpan * expandIndex;

            boolean needInsert = isNeedInsert(expandIndex,
                    xptModel == null || xptModel.getExpandInplaceCount() == null ? -1 : xptModel.getExpandInplaceCount());
            if (needInsert) {
                needInsert = !isAllowReuse(cell, table, newIndex);
                if (!needInsert) {
                    if (counter.minReuse == Integer.MAX_VALUE)
                        counter.minReuse = newIndex;
                    if (counter.maxReuse < newIndex)
                        counter.maxReuse = newIndex;
                }
                counter.incSpan++;
                if (needInsert)
                    counter.realIncSpan++;
            }

            ExpandedCol newCol = needInsert ? table.insertEmptyCol(newIndex) : table.makeCol(newIndex);
            if (needInsert) {
                newCol.setNewlyCreated(true);
                newCol.setGeneratorCell(cell);
                newCol.setColModel(col.getColModel());
                newCol.useNextColStyle();
            }
            duplicateCol(r, cell, expandIndex, expandValue, newCol, cellMap, processing);
        }

        initRowParentAndColParent(cellMap);

        addNewCellToParentDescendants(table, cellMap);
    }

    private boolean isAllowReuse(ExpandedCell cell, ExpandedTable table, int index) {
        if (index >= table.getColCount())
            return false;

        ExpandedCol col = table.getCol(index);
        if (col.isNewlyCreated()) {
            // 同一个父节点的兄弟节点可以复用展开列
            return cell.getExpandableColParent() == col.getGeneratorCell().getExpandableColParent();
        }

        return false;
    }

    // 如果是待复制单元格或者其子单元格，则复制，否则延展。
    private void duplicateCol(ExpandedCol col, ExpandedCell cell, int expandIndex,
                              Object expandValue, ExpandedCol newCol,
                              Map<ExpandedCell, ExpandedCell> cellMap,
                              Collection<ExpandedCell> processing) {
        newCol.setColModel(col.getColModel());

        ExpandedCell nextCell = col.getFirstCell();
        ExpandedCell newCell = newCol.getFirstCell();

        while (nextCell != null) {
            if (cell == nextCell) {
                copyCellValue(nextCell, newCell, false);
                newCell.setExpandIndex(expandIndex);
                newCell.setExpandValue(expandValue);
                cellMap.put(nextCell, newCell);
            } else if (cell.getColDescendants() != null && cell.getColDescendants().containsKey(nextCell.getName())) {
                List<ExpandedCell> cells = cell.getColDescendants().get(nextCell.getName());
                if (cells.contains(nextCell)) {
                    copyCellValue(nextCell, newCell, false);
                    cellMap.put(nextCell, newCell);
                    if (newCell.isExpandable())
                        processing.add(newCell);
                } else {
                    newCell.setStyleId(nextCell.getStyleId());
                }
            }
            nextCell = nextCell.getDown();
            newCell = newCell.getDown();
        }
    }

    private boolean isNeedInsert(int expandIndex, int expandInplaceCount) {
        if (expandInplaceCount <= 0)
            return true;
        return expandIndex >= expandInplaceCount;
    }

}