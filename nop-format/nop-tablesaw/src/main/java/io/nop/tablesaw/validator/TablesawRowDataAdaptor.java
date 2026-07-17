package io.nop.tablesaw.validator;

import io.nop.table.validator.IRowDataAdaptor;
import tech.tablesaw.api.Row;

public class TablesawRowDataAdaptor implements IRowDataAdaptor<Row> {

    @Override
    public Object getValue(Row row, int columnIndex) {
        return row.getObject(columnIndex);
    }
}
