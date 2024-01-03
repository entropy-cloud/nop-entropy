package io.nop.tablesaw.dataset;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import static io.nop.tablesaw.utils.TablesawHelper.dataTypeToColumnType;

public class DataSetToTableTransformer implements Function<IDataSet, Table> {
    private final String name;

    public DataSetToTableTransformer(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Table apply(IDataSet ds) {
        IDataSetMeta meta = ds.getMeta();
        Table table = Table.create(name);
        int fldCount = meta.getFieldCount();
        Column<?>[] cols = new Column[fldCount];

        ObjIntConsumer<IDataRow>[] consumers = new ObjIntConsumer[fldCount];

        for (int i = 0; i < fldCount; i++) {
            ColumnType type = dataTypeToColumnType(meta.getFieldStdType(i));
            Column<?> col = type.create(meta.getFieldName(i));
            cols[i] = col;
            consumers[i] = ColumnCollectors.buildConsumer(col);
        }

        table.addColumns(cols);

        ds.forEach(row -> {
            for (int i = 0; i < fldCount; i++) {
                consumers[i].accept(row, i);
            }
        });

        return table;
    }
}
