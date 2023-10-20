/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.model;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.XptCellModel;

import java.util.List;

public class ExpandedTableToNode {

    public static void dump(ExpandedTable table) {
        new ExpandedTableToNode().buildNodeForTable(table).dump();
    }

    public XNode buildNodeForTable(ExpandedTable table) {
        XNode node = XNode.make("table");
        table.forEachRealCell((cell, rowIndex, colIndex) -> {
            ExpandedCell ec = (ExpandedCell) cell;
            if (ec != null && isRootNode(ec)) {
                XNode cellNode = buildCellNode(ec);
                node.appendChild(cellNode);
            }
            return ProcessResult.CONTINUE;
        });
        return node;
    }

    private boolean isRootNode(ExpandedCell cell) {
        return cell.getRowParent() == null && cell.getColParent() == null;
    }

    private XNode buildCellNode(ExpandedCell cell) {
        XNode cellNode = XNode.make("cell");
        cellNode.setAttr("name", cell.getModel().getName());
        if (cell.getMergeAcross() > 0) {
            cellNode.setAttr("mergeAcross", cell.getMergeAcross());
        }

        if (cell.getMergeDown() > 0) {
            cellNode.setAttr("mergeDown", cell.getMergeDown());
        }

        if (!StringHelper.isEmpty(cell.getText())) {
            cellNode.setAttr("text", cell.getText());
        }

        if (cell.getExpandType() != null) {
            cellNode.setAttr("expandType", cell.getExpandType());
        }

        if (cell.getExpandIndex() >= 0) {
            cellNode.setAttr("expandIndex", cell.getExpandIndex());
        }

        if (cell.getExpandValue() != null) {
            cellNode.setAttr("expandValue", cell.getExpandValue());
        }

        XptCellModel cellModel = cell.getModel();
        if (!StringHelper.isEmpty(cellModel.getField())) {
            cellNode.setAttr("field", cellModel.getField());
        }

        if (cell.getRowParent() != null) {
            cellNode.setAttr("rowParent", cell.getRowParent().getName());
            if (cell.getRowParent().getExpandIndex() >= 0)
                cellNode.setAttr("rowParentExpandIndex", cell.getRowParent().getExpandIndex());
        }

        List<ExpandedCell> rowChildren = cell.getRowChildren();
        if (rowChildren != null && !rowChildren.isEmpty()) {
            XNode children = cellNode.makeChild("rowChildren");
            for (ExpandedCell child : rowChildren) {
                children.appendChild(buildCellNode(child));
            }
        }

        if (cell.getColParent() != null) {
            cellNode.setAttr("colParent", cell.getColParent().getName());
            if (cell.getColParent().getExpandIndex() >= 0)
                cellNode.setAttr("colParentExpandIndex", cell.getColParent().getExpandIndex());
        }

        List<ExpandedCell> colChildren = cell.getColChildren();
        if (colChildren != null && !colChildren.isEmpty()) {
            XNode children = cellNode.makeChild("colChildren");
            for (ExpandedCell child : colChildren) {
                children.appendChild(buildCellNode(child));
            }
        }
        return cellNode;
    }
}
