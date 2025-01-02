package io.nop.ooxml.xlsx.imp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.type.StdDataType;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.core.model.table.tree.TreeCellChildPosition;
import io.nop.core.model.table.tree.TreeTableLayout;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.List;

public class TreeObjectLayout {
    public static final String STYLE_ID_ROW = "row";
    public static final String STYLE_ID_LIST = "list";
    public static final String STYLE_ID_SEQ = "seq";
    public static final String STYLE_ID_LABEL = "label";
    public static final String STYLE_ID_VALUE = "value";
    public static final String STYLE_ID_TITLE = "title";
    public static final String STYLE_ID_HEADER = "header";
    public static final String STYLE_ID_COL = "col";
    public static final String STYLE_ID_FIELD = "field";
    public static final String STYLE_ID_AUTO_SEQ = "auto-seq";

    private final List<TreeCell> cells = new ArrayList<>();

    public TreeCell init(ImportSheetModel sheetModel) {
        if (sheetModel.isList()) {
            TreeCell cell = buildListCell(sheetModel.getFields(), sheetModel.isNoSeqCol());
            cells.add(cell);
        } else {
            addFields(sheetModel.getFields());
        }

        return TreeTableLayout.instance().calcLayout(cells, true);
    }

    private void addFields(List<ImportFieldModel> fields) {
        for (ImportFieldModel field : fields) {
            addField(field);
        }
    }

    public List<TreeCell> getCells() {
        return cells;
    }

    private void addField(ImportFieldModel mainField) {
        if (mainField.isList()) {
            TreeCell cell = new TreeCell(mainField, TreeCellChildPosition.ver);
            cell.setStyleId(STYLE_ID_FIELD);

            TreeCell title = new TreeCell(mainField);
            title.setStyleId(STYLE_ID_TITLE);
            cell.addChild(title);

            cell.addChild(buildListCell(mainField.getFields(), mainField.isNoSeqCol()));
            cells.add(cell);
        } else if (mainField.hasFields()) {
            for (ImportFieldModel field : mainField.getFields()) {
                addField(field);
            }
        } else {
            addSimpleCell(mainField, isSingleRow(mainField));
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
        TreeCell labelCell = new TreeCell(value);
        labelCell.setStyleId(STYLE_ID_LABEL);

        TreeCell valueCell = new TreeCell(value);
        valueCell.setStyleId(STYLE_ID_VALUE);

        if (cells.isEmpty() || singleRow || (cells.get(cells.size()-1).getChildren().size()/2) % 2 == 1) {
            TreeCell row = new TreeCell(null, TreeCellChildPosition.hor);
            row.setStyleId(STYLE_ID_ROW);
            row.addChild(labelCell);
            row.addChild(valueCell);
            cells.add(row);
        } else {
            TreeCell last = cells.get(cells.size() - 1);
            last.addChild(labelCell);
            last.addChild(valueCell);
        }
    }

    public TreeCell buildListCell(List<ImportFieldModel> fields, boolean noSeqCol) {
        TreeCell cell = new TreeCell(null);
        cell.setStyleId(STYLE_ID_LIST);
        cell.setChildPos(TreeCellChildPosition.hor);

        boolean complex = hasSubFields(fields);
        if (complex) {
            TreeCell child = new TreeCell("1");
            child.setStyleId(STYLE_ID_SEQ);
            cell.addChild(child);

            TreeCell child2 = new TreeCell(null);
            child2.setChildPos(TreeCellChildPosition.ver);
            cell.addChild(child2);

            TreeObjectLayout layout = new TreeObjectLayout();
            layout.addFields(fields);
            child2.setChildren(layout.cells);
        } else {
            boolean addSeqCol = shouldAddSeqCol(noSeqCol, fields);
            if (addSeqCol) {
                TreeCell col = new TreeCell(null, TreeCellChildPosition.ver);
                col.setStyleId(STYLE_ID_COL);
                TreeCell labelCell = new TreeCell(null);
                labelCell.setStyleId(STYLE_ID_AUTO_SEQ);
                TreeCell valueCell = new TreeCell(null);
                valueCell.setStyleId(STYLE_ID_VALUE);
                col.addChild(labelCell);
                col.addChild(valueCell);
                cell.addChild(col);
            }

            for (ImportFieldModel field : fields) {
                TreeCell col = new TreeCell(field, TreeCellChildPosition.ver);
                col.setStyleId(STYLE_ID_COL);
                TreeCell labelCell = new TreeCell(field);
                labelCell.setStyleId(STYLE_ID_HEADER);
                TreeCell valueCell = new TreeCell(field);
                valueCell.setStyleId(STYLE_ID_VALUE);
                col.addChild(labelCell);
                col.addChild(valueCell);
                cell.addChild(col);
            }
        }
        return cell;
    }

    private boolean shouldAddSeqCol(boolean noSeqCol, List<ImportFieldModel> fields) {
        if (noSeqCol)
            return false;
        ImportFieldModel field = fields.get(0);
        if (!field.isMandatory())
            return true;
        ISchema schema = field.getSchema();
        if (schema == null)
            return true;
        return schema.getStdDataType() != StdDataType.INT;
    }
}