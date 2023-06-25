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
        for (List<ExpandedCell> list : cell.getColDescendants().values()) {
            for (ExpandedCell child : list) {
                child.setRemoved(true);
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
    protected void extendCells(ExpandedCell cell, int expandCount) {
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

        int incSpan = expandSpan * (expandCount - 1);
        if (xptModel != null && xptModel.getExpandInplaceCount() != null && xptModel.getExpandInplaceCount() > 0) {
            incSpan -= xptModel.getExpandInplaceCount() - 1;
        }

        for (ExpandedCell needExtend : needExtends.keySet()) {
            needExtend.setMergeAcross(needExtend.getMergeAcross() + incSpan);
            needExtend.markProxy();
        }
    }

    @Override
    protected void duplicateCell(ExpandedCell cell, int expandIndex, Object expandValue, Collection<ExpandedCell> processing) {
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

            ExpandedCol newCol = needInsert ? table.insertEmptyCol(newIndex) : table.makeCol(newIndex);
            if(needInsert)
                newCol.setColModel(col.getColModel());
            duplicateCol(r, cell, expandIndex, expandValue, newCol, cellMap, processing);

        }

        initColChildren(cellMap);

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
    private void duplicateCol(ExpandedCol col, ExpandedCell cell, int expandIndex,
                              Object expandValue, ExpandedCol newCol,
                              Map<ExpandedCell, ExpandedCell> cellMap,
                              Collection<ExpandedCell> processing) {
        newCol.setColModel(col.getColModel());

        ExpandedCell nextCell = col.getFirstCell();
        ExpandedCell newCell = newCol.getFirstCell();

        XptCellModel xptModel = cell.getModel();

        while (nextCell != null) {
            if (cell == nextCell) {
                copyCellValue(nextCell, newCell);
                newCell.setExpandIndex(expandIndex);
                newCell.setExpandValue(expandValue);
                cellMap.put(nextCell, newCell);
            } else if (xptModel != null && xptModel.getColDuplicateCells().containsKey(nextCell.getName())) {
                copyCellValue(nextCell, newCell);
                cellMap.put(nextCell, newCell);
                if (newCell.isExpandable())
                    processing.add(newCell);
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

    private void initColChildren(Map<ExpandedCell, ExpandedCell> cellMap) {
        for (Map.Entry<ExpandedCell, ExpandedCell> entry : cellMap.entrySet()) {
            ExpandedCell cell = entry.getKey();
            ExpandedCell newCell = entry.getValue();

            ExpandedCell parent = cell.getColParent();
            if (parent != null) {
                ExpandedCell newParent = cellMap.get(parent);
                if (newParent == null)
                    newParent = parent;
                newCell.setColParent(newParent);
            }

//            if (cell.hasColDescendant()) {
//                Map<String, List<ExpandedCell>> children = getNewListMap(cell.getColDescendants(), cellMap);
//                newCell.setColDescendants(children);
//            }
        }
    }
}