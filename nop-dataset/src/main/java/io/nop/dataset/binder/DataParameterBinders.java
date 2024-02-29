/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.binder;

import io.nop.commons.bytes.ByteString;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataParameterBinders {

    public static IDataParameterBinder STRING = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.STRING;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.VARCHAR;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getString(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setString(index, (String) value);
        }
    };

    public static IDataParameterBinder STRING_EX = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.STRING;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.VARCHAR;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getString(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            String str = (String) value;
            if (StringHelper.isEmpty(str)) {
                params.setNull(index);
            } else {
                params.setString(index, str);
            }
        }
    };

    public static IDataParameterBinder BOOLEAN = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.BOOLEAN;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.BOOLEAN;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getBoolean(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setBoolean(index, (Boolean) value);
        }
    };

    public static IDataParameterBinder CHAR = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.STRING;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.CHAR;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getString(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setString(index, (String) value);
        }
    };

    public static IDataParameterBinder BYTE = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.BYTE;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.TINYINT;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getByte(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setByte(index, (Byte) value);
        }
    };

    public static IDataParameterBinder SHORT = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.SHORT;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.SMALLINT;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getShort(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setShort(index, (Short) value);
        }
    };

    public static IDataParameterBinder INT = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.INT;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.INTEGER;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getInt(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setInt(index, (Integer) value);
        }
    };

    public static IDataParameterBinder LONG = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.LONG;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.BIGINT;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getLong(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setLong(index, (Long) value);
        }
    };

    public static IDataParameterBinder FLOAT = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.FLOAT;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.FLOAT;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getFloat(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setFloat(index, (Float) value);
        }
    };

    public static IDataParameterBinder DOUBLE = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.DOUBLE;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.DOUBLE;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getDouble(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setDouble(index, (Double) value);
        }
    };

    public static IDataParameterBinder DECIMAL = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.DECIMAL;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.DECIMAL;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getBigDecimal(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setBigDecimal(index, (BigDecimal) value);
        }
    };

    public static IDataParameterBinder NUMERIC = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.DECIMAL;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.NUMERIC;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getBigDecimal(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setBigDecimal(index, (BigDecimal) value);
        }
    };


    public static IDataParameterBinder DATE = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.DATE;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.DATE;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getLocalDate(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setLocalDate(index, (LocalDate) value);
        }
    };

    public static IDataParameterBinder TIME = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.TIME;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.TIME;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getLocalTime(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setLocalTime(index, (LocalTime) value);
        }
    };

    public static IDataParameterBinder DATETIME = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.DATETIME;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.DATETIME;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getLocalDateTime(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setLocalDateTime(index, (LocalDateTime) value);
        }
    };

    public static IDataParameterBinder TIMESTAMP = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.TIMESTAMP;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.TIMESTAMP;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getTimestamp(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setTimestamp(index, (Timestamp) value);
        }
    };

    public static IDataParameterBinder BYTE_STRING = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.BYTES;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.VARBINARY;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getByteString(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setByteString(index, (ByteString) value);
        }
    };

    public static IDataParameterBinder ANY = new IDataParameterBinder() {
        @Override
        public StdDataType getStdDataType() {
            return StdDataType.ANY;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return StdSqlType.ANY;
        }

        @Override
        public Object getValue(IDataParameters params, int index) {
            return params.getObject(index);
        }

        @Override
        public void setValue(IDataParameters params, int index, Object value) {
            params.setObject(index, value);
        }
    };

    static final Map<String, IDataParameterBinder> defaultBinders = new ConcurrentHashMap<>();

    static {
        register(STRING);
        register(BOOLEAN);
        register(BYTE);
        register(SHORT);
        register(FLOAT);
        register(DOUBLE);
        register(LONG);
        register(DECIMAL);
        register(NUMERIC);
        register(INT);
        register(DATE);
        register(TIME);
        register(DATETIME);
        register(TIMESTAMP);
        register(BYTE_STRING);
        register(ANY);
        register(CHAR);
    }

    public static void register(IDataParameterBinder binder) {
        defaultBinders.put(binder.getStdSqlType().getName(), binder);
    }

    public static IDataParameterBinder getDefaultBinder(String sqlType) {
        return defaultBinders.get(sqlType);
    }

}