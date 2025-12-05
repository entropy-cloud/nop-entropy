/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tablesaw.dataset;

import io.nop.dataset.IDataRow;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.columns.Column;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.ObjIntConsumer;

public class ColumnCollectors {

    @SuppressWarnings("unchecked")
    public static ObjIntConsumer<IDataRow> buildConsumer(Column<?> col) {
        ColumnType type = col.type();
        if (type == ColumnType.STRING)
            return consumeString((Column<String>) col);
        if (type == ColumnType.BOOLEAN)
            return consumeBoolean((Column<Boolean>) col);
        if (type == ColumnType.SHORT)
            return consumeShort((Column<Short>) col);
        if (type == ColumnType.INTEGER)
            return consumeInteger((Column<Integer>) col);
        if (type == ColumnType.LONG)
            return consumeLong((Column<Long>) col);
        if (type == ColumnType.FLOAT)
            return consumeFloat((Column<Float>) col);
        if (type == ColumnType.DOUBLE)
            return consumeDouble((Column<Double>) col);
        if (type == ColumnType.LOCAL_DATE)
            return consumeLocateDate((Column<LocalDate>) col);
        if (type == ColumnType.LOCAL_TIME)
            return consumeLocateTime((Column<LocalTime>) col);
        if (type == ColumnType.LOCAL_DATE_TIME)
            return consumeLocateDateTime((Column<LocalDateTime>) col);
        if (type == ColumnType.INSTANT)
            return consumeInstant((Column<Instant>) col);
        throw new IllegalArgumentException("nop.err.unsupported-column-type:" + type);
    }

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

    private static ObjIntConsumer<IDataRow> consumeInstant(Column<Instant> col) {
        return (row, colIndex) -> {
            Instant value = row.getInstant(colIndex);
            if (value == null) {
                col.append(value);
            } else {
                col.appendMissing();
            }
        };
    }
}
