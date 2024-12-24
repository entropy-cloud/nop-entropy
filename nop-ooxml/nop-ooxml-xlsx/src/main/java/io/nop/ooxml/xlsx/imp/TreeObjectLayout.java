package io.nop.ooxml.xlsx.imp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.core.model.table.tree.TreeCellChildPosition;
import io.nop.core.model.table.tree.TreeTableLayout;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.ooxml.xlsx.XlsxConstants;

import java.util.ArrayList;
import java.util.List;

public class TreeObjectLayout {
    static final String STYLE_ID_ROW = "row";
    static final String STYLE_ID_LIST = "list";
    static final String STYLE_ID_SEQ = "seq";

    private final List<TreeCell> cells = new ArrayList<>();

    public TreeCell init(ImportSheetModel sheetModel) {
        if (sheetModel.isList()) {
            TreeObjectLayout layout = addListCell(sheetModel, hasSubFields(sheetModel.getFields()));
            for (ImportFieldModel field : sheetModel.getFields()) {
                layout.addField(field);
            }
        } else {
            for (ImportFieldModel field : sheetModel.getFields()) {
                addField(field);
            }
        }

        return TreeTableLayout.instance().calcLayout(cells, true);
    }

    public List<TreeCell> getCells() {
        return cells;
    }

    private void addField(ImportFieldModel mainField) {
        if (mainField.isList()) {
            TreeObjectLayout layout = addListCell(mainField, hasSubFields(mainField.getFields()));
            for (ImportFieldModel field : mainField.getFields()) {
                layout.addField(field);
            }
        } else if (mainField.hasFields()) {
            for (ImportFieldModel field : mainField.getFields()) {
                addField(field);
            }
        } else {
            addSimpleCell(mainField.getName(), isSingleRow(mainField));
        }
    }

    private boolean isSingleRow(ImportFieldModel field) {
        return ConvertHelper.toPrimitiveBoolean(field.prop_get(XlsxConstants.EXT_PROP_XPT_SINGLE_ROW));
    }

    private boolean hasSubFields(List<ImportFieldModel> fields) {
        for (ImportFieldModel field : fields) {
            if (field.hasFields())
                return true;
        }
        return false;
    }

    public void addSimpleCell(Object value, boolean singleRow) {
        TreeCell cell = new TreeCell(value);
        if (cells.isEmpty() || singleRow) {
            cells.add(cell);
        } else {
            TreeCell last = cells.get(cells.size() - 1);
            if (!last.hasChild()) {
                last.setChildPos(TreeCellChildPosition.right_hor);
                last.addChild(cell);
            } else {
                cells.add(cell);
            }
        }
    }

    public TreeObjectLayout addListCell(Object value, boolean complex) {
        TreeCell cell = new TreeCell(value, TreeCellChildPosition.bottom_hor);
        cell.setStyleId(STYLE_ID_LIST);
        cells.add(cell);

        TreeObjectLayout layout = new TreeObjectLayout();

        if (complex) {
            TreeCell child = new TreeCell("1");
            child.setStyleId(STYLE_ID_SEQ);
            child.setChildPos(TreeCellChildPosition.right_hor);
            cell.addChild(child);

            TreeCell child2 = new TreeCell(null);
            child2.setChildPos(TreeCellChildPosition.ver);
            child.addChild(child2);

            child2.setChildren(layout.cells);
        } else {
            cell.setChildren(layout.cells);
        }
        return layout;
    }
}