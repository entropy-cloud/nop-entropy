/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.build;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;

/**
 * 调试功能，用于显示单元格之间的关系
 */
public class XptStructureToNode {
    public XNode buildNode(ExcelWorkbook workbook) {
        XNode node = XNode.make("workbook");
        for (ExcelSheet sheet : workbook.getSheets()) {
            node.appendChild(buildNodeForSheet(sheet));
        }
        return node;
    }

    public XNode buildNodeForSheet(ExcelSheet sheet) {
        XNode node = XNode.make("sheet");
        node.setAttr("name", sheet.getName());

        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            ExcelCell ec = (ExcelCell) cell;
            if (ec != null && isRootNode(ec)) {
                XNode cellNode = buildCellNode(ec);
                node.appendChild(cellNode);
            }
            return ProcessResult.CONTINUE;
        });
        return node;
    }

    private boolean isRootNode(ExcelCell cell) {
        XptCellModel cellModel = cell.getModel();
        if (cellModel == null) {
            return true;
        }

        return cellModel.isTopColCell() && cellModel.isTopRowCell();
    }

    private XNode buildCellNode(ExcelCell cell) {
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

        XptCellModel cellModel = cell.getModel();
        if (!StringHelper.isEmpty(cellModel.getField())) {
            cellNode.setAttr("field", cellModel.getField());
        }

        if (cellModel.getExpandType() != null) {
            cellNode.setAttr("expandType", cellModel.getExpandType());
            if (cellModel.getRowExpandOffset() != 0) {
                cellNode.setAttr("rowExpandOffset", cellModel.getRowExpandOffset());
            }

            if (cellModel.getRowExpandSpan() > 1) {
                cellNode.setAttr("rowExpandSpan", cellModel.getRowExpandSpan());
            }

            if (cellModel.getColExpandOffset() != 0) {
                cellNode.setAttr("colExpandOffset", cellModel.getColExpandOffset());
            }

            if (cellModel.getColExpandSpan() > 1) {
                cellNode.setAttr("colExpandSpan", cellModel.getColExpandSpan());
            }

            if (!cellModel.getColExtendCells().isEmpty()) {
                cellNode.setAttr("colExtendCells", cellModel.getColExtendCells().keySet());
            }

            if (!cellModel.getRowExtendCells().isEmpty()) {
                cellNode.setAttr("rowExtendCells", cellModel.getRowExtendCells().keySet());
            }

            if (!cellModel.getRowDuplicateCells().isEmpty()) {
                cellNode.setAttr("rowDuplicateCells", cellModel.getRowDuplicateCells().keySet());
            }

            if (!cellModel.getColDuplicateCells().isEmpty()) {
                cellNode.setAttr("colDuplicateCells", cellModel.getColDuplicateCells().keySet());
            }
        }

        if (cellModel.getRowParent() != null) {
            cellNode.setAttr("rowParent", cellModel.getRowParent());
        }

        if (cellModel.getColParent() != null) {
            cellNode.setAttr("colParent", cellModel.getColParent());
        }

        if (cellModel.getRowChildCells() != null && !cellModel.getRowChildCells().isEmpty()) {
            XNode children = XNode.make("rowChildren");
            for (ExcelCell child : cellModel.getRowChildCells().values()) {
                children.appendChild(buildCellNode(child));
            }
            cellNode.appendChild(children);
        }

        if (cellModel.getColChildCells() != null && !cellModel.getColChildCells().isEmpty()) {
            XNode children = XNode.make("colChildren");
            for (ExcelCell child : cellModel.getColChildCells().values()) {
                children.appendChild(buildCellNode(child));
            }
            cellNode.appendChild(children);
        }
        return cellNode;
    }
}
