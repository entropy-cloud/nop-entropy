/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * 通过expander展开每层的单元格，构建树形结构
 *
 * @param <T>
 */
public class TreeCellBuilder<T> {
    public List<TreeCell> buildRootCells(List<? extends T> cells, TreeCellChildPosition childPos) {
        List<TreeCell> ret = new ArrayList<>(cells.size());
        for (T cell : cells) {
            TreeCell box = new TreeCell(cell, childPos);
            box.setTreeLevel(0);
            ret.add(box);
        }
        return ret;
    }

    public List<TreeCell> buildTreeCells(ITreeCellExpander<T> expander, List<? extends T> cells,
                                         TreeCellChildPosition childPos) {
        List<TreeCell> list = buildRootCells(cells, childPos);
        expandTreeCells(expander, list);
        return list;
    }

    public void expandTreeCells(ITreeCellExpander<T> expander, List<TreeCell> cells) {
        for (TreeCell cell : cells) {
            List<TreeCell> children = expander.buildChildren(cell);
            if (children != null) {
                int childLevel = cell.getTreeLevel() + 1;
                cell.setChildren(children);
                for (TreeCell child : children) {
                    child.setParent(cell);
                    child.setTreeLevel(childLevel);
                }

                expandTreeCells(expander, children);
            }
        }
    }
}