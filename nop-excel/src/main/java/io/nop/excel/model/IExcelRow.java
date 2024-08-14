package io.nop.excel.model;

import io.nop.core.model.table.IRowView;

public interface IExcelRow extends IRowView {
    XptRowModel getModel();
}
