/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.tree;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.core.model.table.impl.BaseTable;

import static io.nop.core.CoreErrors.ARG_CELL_POS;
import static io.nop.core.CoreErrors.ARG_ROW_INDEX;
import static io.nop.core.CoreErrors.ERR_TABLE_NOT_TREE_CELL;
import static io.nop.core.CoreErrors.ERR_TABLE_NULL_ROW;

public class TreeTableHelper {

    /**
     * 解析表格中的一个区域为TreeTable结构。
     */
    public static BaseTable buildTreeTable(ITableView table,
                                           int beginRowIndex, int beginColIndex,
                                           int endRowIndex, int endColIndex,
                                           boolean vertical) {
        BaseTable ret = new BaseTable();
        for (int i = beginRowIndex; i < endRowIndex; i++) {
            IRowView row = table.getRow(i);
            if (row == null)
                throw new NopException(ERR_TABLE_NULL_ROW)
                        .param(ARG_ROW_INDEX, i);

            row.forEachCell(i, (cell, r, c) -> {
                if (c < beginColIndex)
                    return ProcessResult.CONTINUE;
                if (c >= endColIndex)
                    return ProcessResult.STOP;

                if (cell != null && cell.isProxyCell()) {
                    return ProcessResult.CONTINUE;
                }

                TreeCell retCell = new TreeCell(cell, vertical ? TreeCellChildPosition.bottom_hor : TreeCellChildPosition.right_ver);
                retCell.setRowIndex(r - beginRowIndex);
                retCell.setColIndex(c - beginColIndex);
                retCell.setId(CellPosition.toABString(r, c));
                retCell.setValue(cell);

                if (cell != null) {
                    retCell.setMergeAcross(cell.getMergeAcross());
                    retCell.setMergeDown(cell.getMergeDown());
                    retCell.setComment(cell.getComment());
                }
                ret.setCell(retCell.getRowIndex(), retCell.getColIndex(), retCell);
                return ProcessResult.CONTINUE;
            });
        }

        buildParentChildren(ret, vertical);
        return ret;
    }

    private static void buildParentChildren(ITableView table, boolean vertical) {
        if (vertical) {
            int rowCount = table.getRowCount();
            IRowView row = table.getRow(rowCount - 1);
            int leafIndex = 0;
            for (int i = 0, n = table.getColCount(); i < n; i++) {
                TreeCell cell = (TreeCell) row.getCell(i).getRealCell();
                buildParentChildren(table, cell, true);
                i += cell.getMergeAcross();
                cell.setLeafIndex(leafIndex++);
            }
        } else {
            int colCount = table.getColCount();
            int leafIndex = 0;
            for (int i = 0, n = table.getRowCount(); i < n; i++) {
                TreeCell cell = (TreeCell) table.getCell(i, colCount - 1).getRealCell();
                buildParentChildren(table, cell, false);
                i += cell.getMergeDown();
                cell.setLeafIndex(leafIndex++);
            }
        }
    }

    private static void buildParentChildren(ITableView table, TreeCell cell, boolean vertical) {
        if (cell.getParent() != null)
            return;

        if (vertical) {
            if (cell.getRowIndex() == 0)
                return;

            TreeCell prevCell = (TreeCell) table.getCell(cell.getRowIndex() - 1, cell.getColIndex()).getRealCell();
            if (prevCell.getColIndex() > cell.getColIndex()
                    || prevCell.getEndColIndex() < cell.getEndColIndex())
                throw new NopException(ERR_TABLE_NOT_TREE_CELL)
                        .param(ARG_CELL_POS, cell.getId());

            prevCell.addChild(cell);
            buildParentChildren(table, prevCell, true);

            cell.setTreeLevel(prevCell.getTreeLevel() + 1);
        } else {
            if (cell.getColIndex() == 0)
                return;

            TreeCell prevCell = (TreeCell) table.getCell(cell.getRowIndex(), cell.getColIndex() - 1).getRealCell();
            if (prevCell.getRowIndex() > cell.getRowIndex()
                    || prevCell.getEndRowIndex() < cell.getEndRowIndex())
                throw new NopException(ERR_TABLE_NOT_TREE_CELL)
                        .param(ARG_CELL_POS, cell.getId());

            prevCell.addChild(cell);

            buildParentChildren(table, prevCell, false);

            cell.setTreeLevel(prevCell.getTreeLevel() + 1);
        }
    }
}
