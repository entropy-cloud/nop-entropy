package io.nop.ooxml.xlsx.imp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.type.StdDataType;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.core.model.table.tree.TreeCellChildPosition;
import io.nop.core.model.table.tree.TreeTableLayout;
import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.List;

public class ImportBeanLayout {
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
    public static final String STYLE_ID_SEQ_VALUE = "seq-value";
    public static final String STYLE_ID_SEPARATOR = "separator";

    private final List<TreeCell> cells = new ArrayList<>();

    public TreeCell init(ImportSheetModel sheetModel) {
        if (sheetModel.isList()) {
            TreeCell cell = buildListCell(sheetModel.getFields(), sheetModel.isNoSeqCol(), isSingleColLayout(sheetModel));
            cells.add(cell);
        } else {
            addFields(sheetModel.getFields(), isSingleColLayout(sheetModel));
        }

        return TreeTableLayout.instance().calcLayout(cells, true);
    }

    private void addFields(List<ImportFieldModel> fields, boolean singleColLayout) {
        for (ImportFieldModel field : fields) {
            addField(field, singleColLayout);
        }
    }

    public List<TreeCell> getCells() {
        return cells;
    }

    private void addField(ImportFieldModel mainField, boolean singleColLayout) {
        if (mainField.isList()) {
            if (!cells.isEmpty()) {
                TreeCell cell = new TreeCell(null);
                cell.setStyleId(STYLE_ID_SEPARATOR);
                cell.setFlex(1);
                cells.add(cell);
            }

            TreeCell cell = new TreeCell(mainField, TreeCellChildPosition.ver);
            cell.setStyleId(STYLE_ID_FIELD);

            TreeCell title = new TreeCell(mainField);
            title.setStyleId(STYLE_ID_TITLE);
            cell.addChild(title);

            cell.addChild(buildListCell(mainField.getFields(), mainField.isNoSeqCol(), isSingleColLayout(mainField)));
            cells.add(cell);
        } else if (mainField.hasFields()) {
            for (ImportFieldModel field : mainField.getFields()) {
                addField(field, singleColLayout || isSingleColLayout(field));
            }
        } else {
            addSimpleCell(mainField, singleColLayout || isSingleColLayout(mainField));
        }
    }


    private boolean isSingleColLayout(IExtensibleObject field) {
        return ConvertHelper.toPrimitiveBoolean(field.prop_get(XlsxConstants.EXT_PROP_XPT_SINGLE_COL_LAYOUT));
    }

    private boolean hasSubFields(List<ImportFieldModel> fields) {
        for (ImportFieldModel field : fields) {
            if (field.hasFields())
                return true;
        }
        return false;
    }

    public void addSimpleCell(Object value, boolean singleColLayout) {
        TreeCell labelCell = new TreeCell(value);
        labelCell.setStyleId(STYLE_ID_LABEL);
        labelCell.setColSpan(2);

        TreeCell valueCell = new TreeCell(value);
        valueCell.setStyleId(STYLE_ID_VALUE);
        valueCell.setColSpan(2);
        valueCell.setFlex(1);

        if (cells.isEmpty() || singleColLayout || (cells.get(cells.size() - 1).getChildren().size() / 2) % 2 == 0) {
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

    public TreeCell buildListCell(List<ImportFieldModel> fields, boolean noSeqCol, boolean singleColLayout) {
        TreeCell cell = new TreeCell(null);
        cell.setStyleId(STYLE_ID_LIST);
        cell.setChildPos(TreeCellChildPosition.hor);

        boolean complex = hasSubFields(fields);
        if (complex) {
            TreeCell child = new TreeCell("1");
            child.setStyleId(STYLE_ID_SEQ);
            cell.addChild(child);

            TreeCell child2 = new TreeCell(null);
            child2.setFlex(1);
            child2.setChildPos(TreeCellChildPosition.ver);
            cell.addChild(child2);

            ImportBeanLayout layout = new ImportBeanLayout();
            layout.addFields(fields, singleColLayout);
            child2.setChildren(layout.cells);
        } else {
            boolean addSeqCol = shouldAddSeqCol(noSeqCol, fields);
            boolean hasSeqValue = false;
            if (addSeqCol) {
                TreeCell col = new TreeCell(null, TreeCellChildPosition.ver);
                col.setStyleId(STYLE_ID_COL);
                TreeCell labelCell = new TreeCell(null);
                labelCell.setStyleId(STYLE_ID_AUTO_SEQ);
                TreeCell valueCell = new TreeCell("1");
                valueCell.setStyleId(STYLE_ID_SEQ_VALUE);
                col.addChild(labelCell);
                col.addChild(valueCell);
                cell.addChild(col);

                hasSeqValue = true;
            }

            for (ImportFieldModel field : fields) {
                TreeCell col = new TreeCell(field, TreeCellChildPosition.ver);
                col.setStyleId(STYLE_ID_COL);
                TreeCell labelCell = new TreeCell(field);
                labelCell.setStyleId(STYLE_ID_HEADER);
                TreeCell valueCell = new TreeCell(field);
                if (!hasSeqValue) {
                    valueCell.setStyleId(STYLE_ID_SEQ_VALUE);
                    hasSeqValue = true;
                } else {
                    valueCell.setStyleId(STYLE_ID_VALUE);
                }
                col.addChild(labelCell);
                col.addChild(valueCell);
                cell.addChild(col);
            }
            cell.getLastChild().setFlex(1);
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