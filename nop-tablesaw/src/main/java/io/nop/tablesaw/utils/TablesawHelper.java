package io.nop.tablesaw.utils;

import io.nop.commons.type.StdDataType;
import io.nop.dataset.IDataSet;
import io.nop.tablesaw.dataset.DataSetToTableTransformer;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;

import java.util.function.Function;

import static tech.tablesaw.api.ColumnType.BOOLEAN;
import static tech.tablesaw.api.ColumnType.DOUBLE;
import static tech.tablesaw.api.ColumnType.FLOAT;
import static tech.tablesaw.api.ColumnType.INTEGER;
import static tech.tablesaw.api.ColumnType.LOCAL_DATE;
import static tech.tablesaw.api.ColumnType.LOCAL_DATE_TIME;
import static tech.tablesaw.api.ColumnType.LOCAL_TIME;
import static tech.tablesaw.api.ColumnType.LONG;
import static tech.tablesaw.api.ColumnType.SHORT;
import static tech.tablesaw.api.ColumnType.STRING;

public class TablesawHelper {
    public static ColumnType dataTypeToColumnType(StdDataType dataType) {
        if (dataType == null)
            return null;
        switch (dataType) {
            case INT:
                return INTEGER;
            case LONG:
                return LONG;
            case DATE:
                return ColumnType.LOCAL_DATE;
            case DATETIME:
                return ColumnType.LOCAL_DATE_TIME;
            case TIME:
                return ColumnType.LOCAL_TIME;
            case FLOAT:
                return FLOAT;
            case SHORT:
                return ColumnType.SHORT;
            case DECIMAL:
            case DOUBLE:
                return ColumnType.DOUBLE;
            case BOOLEAN:
                return BOOLEAN;
            case STRING:
            default:
                return STRING;
        }
    }

    public static StdDataType columnTypeToDataType(ColumnType colType) {
        if (colType == null)
            return null;
        if (colType == INTEGER)
            return StdDataType.INT;
        if (colType == LONG)
            return StdDataType.LONG;
        if (colType == LOCAL_DATE)
            return StdDataType.DATE;
        if (colType == LOCAL_DATE_TIME)
            return StdDataType.DATETIME;
        if (colType == LOCAL_TIME)
            return StdDataType.TIME;
        if (colType == FLOAT)
            return StdDataType.FLOAT;
        if (colType == SHORT)
            return StdDataType.SHORT;
        if (colType == DOUBLE)
            return StdDataType.DOUBLE;
        if (colType == BOOLEAN)
            return StdDataType.BOOLEAN;
        return StdDataType.STRING;
    }

    public static Function<IDataSet, Table> dataSetTransformer(String name) {
        return new DataSetToTableTransformer(name);
    }
}
