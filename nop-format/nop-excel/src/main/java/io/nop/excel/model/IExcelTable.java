package io.nop.excel.model;

import io.nop.core.model.table.ITableView;

public interface IExcelTable extends ITableView {
    IExcelRow getRow(int index);
}
