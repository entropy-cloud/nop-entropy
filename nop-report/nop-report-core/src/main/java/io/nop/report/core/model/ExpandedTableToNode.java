package io.nop.report.core.model;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.CellPosition;
import io.nop.excel.model.XptCellModel;

public class ExpandedTableToNode {
    public static void dump(ExpandedSheet sheet) {
        new ExpandedTableToNode().buildNodeForSheet(sheet).dump();
    }

    public XNode buildNodeForSheet(ExpandedSheet sheet) {
        XNode node = XNode.make("sheet");
        node.setAttr("name", sheet.getName());

        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
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
        cellNode.setAttr("name", cell.getName());
        cellNode.setAttr("pos", CellPosition.toABString(cell.getRowIndex(), cell.getColIndex()));

        if (cell.getExpandIndex() >= 0) {
            cellNode.setAttr("expandedIndex", cell.getExpandIndex());
            cellNode.setAttr("expandedValue", cell.getExpandValue());
        }

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
        if (cellModel != null && !StringHelper.isEmpty(cellModel.getField())) {
            cellNode.setAttr("field", cellModel.getField());
        }

        if (cell.getName() != null)
            cellNode.setAttr("coordinate", cell.getLayerCoordinate());

        if (cell.getRowChildren() != null && !cell.getRowChildren().isEmpty()) {
            XNode children = XNode.make("rowChildren");
            for (ExpandedCell child : cell.getRowChildren()) {
                children.appendChild(buildCellNode(child));
            }
            cellNode.appendChild(children);
        }

        if (cell.getColChildren() != null && !cell.getColChildren().isEmpty()) {
            XNode children = XNode.make("colChildren");
            for (ExpandedCell child : cell.getColChildren()) {
                children.appendChild(buildCellNode(child));
            }
            cellNode.appendChild(children);
        }
        return cellNode;
    }
}
