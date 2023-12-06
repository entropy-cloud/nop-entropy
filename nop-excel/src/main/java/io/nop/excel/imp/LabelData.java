package io.nop.excel.imp;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICellView;
import io.nop.excel.imp.model.ImportFieldModel;

public class LabelData {
    private final ICellView labelCell;
    private final ICellView groupCell;
    private final ImportFieldModel field;
    private final ImportFieldModel groupField;

    public LabelData(ICellView labelCell, ImportFieldModel field, ICellView groupCell, ImportFieldModel groupField) {
        this.labelCell = labelCell;
        this.groupCell = groupCell;
        this.field = field;
        this.groupField = groupField;
    }


    public LabelData(ICellView labelCell, ImportFieldModel field) {
        this(labelCell, field, null, null);
    }

    public String getFieldLabel() {
        return StringHelper.toString(labelCell.getText(), "").trim();
    }

    public String getGroupLabel() {
        if (groupCell == null)
            return "";
        return StringHelper.toString(groupCell.getText(), "").trim();
    }

    public ICellView getLabelCell() {
        return labelCell;
    }

    public ICellView getGroupCell() {
        return groupCell;
    }

    public ImportFieldModel getField() {
        return field;
    }

    public ImportFieldModel getGroupField() {
        return groupField;
    }
}