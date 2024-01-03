package io.nop.tablesaw.dataset;

import io.nop.dataset.IDataRow;
import tech.tablesaw.columns.Column;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.ObjIntConsumer;

public class ColumnCollectors {

    private static ObjIntConsumer<IDataRow> consumeString(Column<String> col) {
        return (row, colIndex) -> {
            String value = row.getString(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeInteger(Column<Integer> col) {
        return (row, colIndex) -> {
            Integer value = row.getInt(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeShort(Column<Short> col) {
        return (row, colIndex) -> {
            Short value = row.getShort(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }


    private static ObjIntConsumer<IDataRow> consumeLong(Column<Long> col) {
        return (row, colIndex) -> {
            Long value = row.getLong(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeDouble(Column<Double> col) {
        return (row, colIndex) -> {
            Double value = row.getDouble(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeFloat(Column<Float> col) {
        return (row, colIndex) -> {
            Float value = row.getFloat(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeBoolean(Column<Boolean> col) {
        return (row, colIndex) -> {
            Boolean value = row.getBoolean(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeLocateDateTime(Column<LocalDateTime> col) {
        return (row, colIndex) -> {
            LocalDateTime value = row.getLocalDateTime(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeLocateDate(Column<LocalDate> col) {
        return (row, colIndex) -> {
            LocalDate value = row.getLocalDate(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }

    private static ObjIntConsumer<IDataRow> consumeLocateTime(Column<LocalTime> col) {
        return (row, colIndex) -> {
            LocalTime value = row.getLocalTime(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }
}
