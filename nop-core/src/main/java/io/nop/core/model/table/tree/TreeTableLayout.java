/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.tree;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.ITable;
import io.nop.core.model.tree.TreeVisitors;

import java.util.List;

import static io.nop.core.CoreErrors.ARG_POS;
import static io.nop.core.CoreErrors.ERR_TREE_TABLE_UNSUPPORTED_CHILD_POS;

/**
 * TreeTable布局
 */
public class TreeTableLayout {
    static final TreeTableLayout _instance = new TreeTableLayout();

    public static TreeTableLayout instance() {
        return _instance;
    }

    /**
     * 根据子单元格大小和位置计算父单元格的大小和位置。父单元格和子单元格构成一个TreeCellBox，子TreeCellBox完全嵌套在父TreeCellBox内部
     *
     * @param cells 根单元格
     */
    public TreeCell calcLayout(List<TreeCell> cells, boolean bVer) {
        // 创建一个辅助单元格，用于简化计算
        TreeCell dummy = new TreeCell(null, bVer ? TreeCellChildPosition.ver : TreeCellChildPosition.hor);
        dummy.setTreeLevel(-1);
        dummy.setChildren(cells);

        // 1. 计算TreeCell的BoundingBox的大小
        calcBbox(dummy);
        // 2. 对其子单元格和父单元格的BoundingBox, 必要时自动伸展子单元格，确保没有空隙
        adjustBbox(dummy, dummy.getBboxWidth(), dummy.getBboxHeight());
        // 3. 计算每个单元格的rowIndex和colIndex
        updatePos(dummy, 0, 0);
        return dummy;
    }

    public void assignToTable(List<TreeCell> cells, ITable<IRow> table) {
        for (TreeCell cell : cells) {
            for (TreeCell c : TreeVisitors.depthFirstIterator(cell, true)) {
                if (c.isVirtual())
                    continue;
                table.setCell(c.getRowIndex(), c.getColIndex(), cell);
            }
        }
    }

    /**
     * 计算单元格以及所有子单元格构成的总区域的大小
     */
    void calcBbox(TreeCell cell) {
        List<TreeCell> children = cell.getChildren();
        if (children == null || children.isEmpty()) {
            if (cell.isVirtual()) {
                cell.setBboxHeight(0);
                cell.setBboxHeight(0);
            } else {
                cell.setBboxWidth(cell.getColSpan());
                cell.setBboxHeight(cell.getRowSpan());
            }
            return;
        }

        for (TreeCell child : children) {
            child.setTreeLevel(child.getTreeLevel() + 1);
            calcBbox(child);
        }

        TreeCellChildPosition pos = cell.getChildPos();
        if (pos == null)
            pos = TreeCellChildPosition.ver;

        switch (pos) {
            case right_hor:
            case left_hor: {
                int w = sumBboxWidth(children);
                int h = maxBboxHeight(children);
                cell.setMergeDown(Math.max(cell.getMergeDown(), h - 1));
                cell.setBboxWidth(cell.getColSpan() + w);
                cell.setBboxHeight(cell.getRowSpan());
                break;
            }
            case right_ver:
            case left_ver: {
                int w = maxBboxWidth(children);
                int h = sumBboxWidth(children);
                cell.setMergeDown(Math.max(cell.getMergeDown(), h - 1));
                cell.setBboxWidth(cell.getColSpan() + w);
                cell.setBboxHeight(cell.getRowSpan());
                break;
            }
            case bottom_hor:
            case top_hor: {
                int w = sumBboxWidth(children);
                int h = maxBboxHeight(children);
                cell.setMergeAcross(Math.max(cell.getMergeAcross(), w - 1));
                cell.setBboxWidth(cell.getColSpan());
                cell.setBboxHeight(cell.getRowSpan() + h);
                break;
            }
            case bottom_ver:
            case top_ver: {
                int w = maxBboxWidth(children);
                int h = sumBboxWidth(children);
                cell.setMergeAcross(Math.max(cell.getMergeAcross(), w - 1));
                cell.setBboxWidth(cell.getColSpan());
                cell.setBboxHeight(cell.getRowSpan() + h);
                break;
            }
            case hor: {
                int w = sumBboxWidth(children);
                int h = maxBboxHeight(children);
                cell.setMergeAcross(0);
                cell.setMergeDown(0);
                cell.setBboxWidth(w);
                cell.setBboxHeight(h);
                break;
            }
            case ver: {
                int w = maxBboxWidth(children);
                int h = sumBboxWidth(children);
                cell.setMergeAcross(0);
                cell.setMergeDown(0);
                cell.setBboxWidth(w);
                cell.setBboxHeight(h);
                break;
            }
            default:
                throw new NopException(ERR_TREE_TABLE_UNSUPPORTED_CHILD_POS).param(ARG_POS, cell.getChildPos());
        }
    }

    int sumBboxWidth(List<TreeCell> cells) {
        int ret = 0;
        for (TreeCell cell : cells) {
            ret += cell.getBboxWidth();
        }
        return ret;
    }

    int sumBboxHeight(List<TreeCell> cells) {
        int ret = 0;
        for (TreeCell cell : cells) {
            ret += cell.getBboxHeight();
        }
        return ret;
    }

    int maxBboxWidth(List<TreeCell> cells) {
        int ret = 0;
        for (TreeCell cell : cells) {
            if (cell.getBboxWidth() > ret)
                ret = cell.getBboxWidth();
        }
        return ret;
    }

    int maxBboxHeight(List<TreeCell> cells) {
        int ret = 0;
        for (TreeCell cell : cells) {
            if (cell.getBboxHeight() > ret)
                ret = cell.getBboxHeight();
        }
        return ret;
    }

    /**
     * 根据boundingBox的计算结果，自动拉伸部分单元格，使其占满所有剩余空间
     */
    void adjustBbox(TreeCell cell, int w, int h) {
        List<TreeCell> children = cell.getChildren();
        if (children == null || children.isEmpty()) {
            cell.setMergeDown(h - 1);
            cell.setMergeAcross(w - 1);
            cell.setBboxHeight(h);
            cell.setBboxWidth(w);
            return;
        }

        int bboxWidth = cell.getBboxWidth();
        int bboxHeight = cell.getBboxHeight();

        cell.setBboxWidth(w);
        cell.setBboxHeight(h);

        switch (cell.getChildPos()) {
            case right_hor:
            case left_hor: {
                // 如果边框宽度增加了，则延展最后一个child
                lastChild(children).incWidth(w - bboxWidth);
                adjustChildrenHor(children, h);
                cell.setMergeDown(h - 1);
                break;
            }
            case right_ver:
            case left_ver: {
                lastChild(children).incHeight(h - bboxHeight);
                adjustChildrenVer(children, w - cell.getColSpan());
                cell.setMergeDown(h - 1);
                break;
            }
            case bottom_hor:
            case top_hor: {
                lastChild(children).incWidth(w - bboxWidth);
                adjustChildrenHor(children, h - cell.getRowSpan());
                cell.setMergeAcross(w - 1);
                break;
            }
            case bottom_ver:
            case top_ver: {
                cell.setMergeAcross(w - 1);
                lastChild(children).incHeight(h - bboxHeight);
                adjustChildrenVer(children, w);
                break;
            }
            case hor:
                lastChild(children).incWidth(w - bboxWidth);
                adjustChildrenHor(children, h);
                break;
            case ver:
                lastChild(children).incHeight(h - bboxHeight);
                adjustChildrenVer(children, w);
                break;
            default:
                throw new NopException(ERR_TREE_TABLE_UNSUPPORTED_CHILD_POS).param(ARG_POS, cell.getChildPos());
        }
    }

    TreeCell lastChild(List<TreeCell> children) {
        return children.get(children.size() - 1);
    }

    void adjustChildrenHor(List<TreeCell> children, int h) {
        for (TreeCell child : children) {
            adjustBbox(child, child.getBboxWidth(), h);
        }
    }

    void adjustChildrenVer(List<TreeCell> children, int w) {
        for (TreeCell child : children) {
            adjustBbox(child, w, child.getBboxHeight());
        }
    }

    void updatePos(TreeCell cell, int rowIndex, int colIndex) {
        cell.setRowIndex(rowIndex);
        cell.setColIndex(colIndex);

        List<TreeCell> children = cell.getChildren();
        if (children == null || children.isEmpty())
            return;

        int bboxWidth = cell.getBboxWidth();
        int bboxHeight = cell.getBboxHeight();

        switch (cell.getChildPos()) {
            case right_hor: {
                setHorChildrenTo(children, rowIndex, colIndex + cell.getColSpan());
                break;
            }
            case left_hor: {
                cell.setColIndex(colIndex + bboxWidth - cell.getColSpan());
                setHorChildrenTo(children, rowIndex, colIndex);
                break;
            }
            case right_ver: {
                setVerChildrenTo(children, rowIndex, colIndex + cell.getColSpan());
                break;
            }
            case left_ver: {
                cell.setColIndex(colIndex + bboxWidth - cell.getColSpan());
                setVerChildrenTo(children, rowIndex, colIndex);
                break;
            }
            case bottom_hor: {
                setHorChildrenTo(children, rowIndex + cell.getRowSpan(), colIndex);
                break;
            }
            case top_hor: {
                cell.setRowIndex(rowIndex + bboxHeight - cell.getRowSpan());
                setHorChildrenTo(children, rowIndex, colIndex);
                break;
            }
            case bottom_ver: {
                setVerChildrenTo(children, rowIndex + cell.getRowSpan(), colIndex);
                break;
            }
            case top_ver: {
                cell.setRowIndex(rowIndex + bboxHeight - cell.getRowSpan());
                setVerChildrenTo(children, rowIndex, colIndex);
                break;
            }
            case hor: {
                setHorChildrenTo(children, rowIndex, colIndex);
                break;
            }
            case ver: {
                setVerChildrenTo(children, rowIndex, colIndex);
                break;
            }
            default:
                throw new NopException(ERR_TREE_TABLE_UNSUPPORTED_CHILD_POS).param(ARG_POS, cell.getChildPos());
        }
    }

    void setHorChildrenTo(List<TreeCell> children, int rowIndex, int colIndex) {
        for (TreeCell child : children) {
            updatePos(child, rowIndex, colIndex);
            colIndex += child.getBboxWidth();
        }
    }

    void setVerChildrenTo(List<TreeCell> children, int rowIndex, int colIndex) {
        for (TreeCell child : children) {
            updatePos(child, rowIndex, colIndex);
            rowIndex += child.getBboxHeight();
        }
    }
}