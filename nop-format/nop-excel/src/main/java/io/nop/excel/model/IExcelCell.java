package io.nop.excel.model;

import io.nop.core.model.table.ICellView;

public interface IExcelCell extends ICellView {
    XptCellModel getModel();

    void setComment(String comment);

    void setValue(Object value);
}
