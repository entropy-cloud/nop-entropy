/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.type;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public enum StdSqlType implements ISqlDataType {
    BOOLEAN(false, false, Types.BOOLEAN, StdDataType.BOOLEAN), TINYINT(false, false, Types.TINYINT, StdDataType.BYTE),
    SMALLINT(false, false, Types.SMALLINT, StdDataType.SHORT), INTEGER(false, false, Types.INTEGER, StdDataType.INT),
    BIGINT(false, false, Types.BIGINT, StdDataType.LONG), DECIMAL(true, true, Types.DECIMAL, StdDataType.DECIMAL),
    FLOAT(false, false, Types.FLOAT, StdDataType.DOUBLE), REAL(false, false, Types.REAL, StdDataType.FLOAT),
    DOUBLE(false, false, Types.DOUBLE, StdDataType.DOUBLE),

    NUMERIC(false, false, Types.NUMERIC, StdDataType.DECIMAL),

    DATE(false, false, Types.DATE, StdDataType.DATE),

    TIME(false, false, Types.TIME, StdDataType.TIME),

    DATETIME(false, false, Types.TIMESTAMP, StdDataType.DATETIME),

    // TIME(PrecScale.NO_NO | PrecScale.YES_NO, false, Types.TIME,
    // SqlTypeFamily.TIME),
    // TIME_WITH_LOCAL_TIME_ZONE(PrecScale.NO_NO | PrecScale.YES_NO, false, Types.OTHER,
    // SqlTypeFamily.TIME),
    TIMESTAMP(false, false, Types.TIMESTAMP, StdDataType.TIMESTAMP),

    // TIMESTAMP_WITH_LOCAL_TIME_ZONE(PrecScale.NO_NO | PrecScale.YES_NO, false, Types.OTHER,
    // SqlTypeFamily.TIMESTAMP),
    // INTERVAL_YEAR(PrecScale.NO_NO, false, Types.OTHER,
    // SqlTypeFamily.INTERVAL_YEAR_MONTH),
    // INTERVAL_YEAR_MONTH(PrecScale.NO_NO, false, Types.OTHER,
    // SqlTypeFamily.INTERVAL_YEAR_MONTH),
    // INTERVAL_MONTH(PrecScale.NO_NO, false, Types.OTHER,
    // SqlTypeFamily.INTERVAL_YEAR_MONTH),
    // INTERVAL_DAY(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_DAY_HOUR(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_DAY_MINUTE(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_DAY_SECOND(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_HOUR(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_HOUR_MINUTE(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_HOUR_SECOND(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_MINUTE(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_MINUTE_SECOND(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    // INTERVAL_SECOND(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
    // false, Types.OTHER, SqlTypeFamily.INTERVAL_DAY_TIME),
    CHAR(true, false, Types.CHAR, StdDataType.STRING),

    VARCHAR(true, false, Types.VARCHAR, StdDataType.STRING),

    BINARY(true, false, Types.BINARY, StdDataType.BYTES),
    // SqlTypeFamily.BINARY),
    VARBINARY(true, false, Types.VARBINARY, StdDataType.BYTES),

    // NULL(PrecScale.NO_NO, true, Types.NULL, SqlTypeFamily.NULL),
    // ANY(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES, true,
    // Types.JAVA_OBJECT, SqlTypeFamily.ANY),
    // SYMBOL(PrecScale.NO_NO, true, Types.OTHER, null),
    // MULTISET(PrecScale.NO_NO, false, Types.ARRAY, SqlTypeFamily.MULTISET),
    ARRAY(false, false, Types.ARRAY, StdDataType.LIST),

    // MAP(PrecScale.NO_NO, false, Types.OTHER, SqlTypeFamily.MAP),
    // DISTINCT(PrecScale.NO_NO, false, Types.DISTINCT, null),
    // STRUCTURED(PrecScale.NO_NO, false, Types.STRUCT, null),
    // ROW(PrecScale.NO_NO, false, Types.STRUCT, null),
    OTHER(false, false, Types.OTHER, StdDataType.ANY),

    JSON(false, false, Types.VARCHAR, StdDataType.STRING),

    CLOB(false, false, Types.CLOB, StdDataType.STRING),

    BLOB(false, false, Types.BLOB, StdDataType.BYTES),

    ANY(false, false, Types.JAVA_OBJECT, StdDataType.ANY),
    // CURSOR(PrecScale.NO_NO, false, ExtraSqlTypes.REF_CURSOR,
    // SqlTypeFamily.CURSOR),
    // COLUMN_LIST(PrecScale.NO_NO, false, Types.OTHER + 2,
    // SqlTypeFamily.COLUMN_LIST),
    // DYNAMIC_STAR(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES, true,
    // Types.JAVA_OBJECT, SqlTypeFamily.ANY),

    /**
     * Spatial type. Though not standard, it is common to several DBs, so we do not flag it 'special' (internal).
     */
    GEOMETRY(false, false, 2015, StdDataType.GEOMETRY);

    private final boolean allowPrecision;
    private final boolean allowScale;
    private final int jdbcType;
    private final StdDataType stdDataType;

    StdSqlType(boolean allowPrecision, boolean allowScale, int jdbcType, StdDataType stdDataType) {
        this.allowPrecision = allowPrecision;
        this.allowScale = allowScale;
        this.jdbcType = jdbcType;
        this.stdDataType = stdDataType;
    }

    public String getName() {
        return name();
    }

    public boolean isAllowPrecision() {
        return allowPrecision;
    }

    public boolean isAllowScale() {
        return allowScale;
    }

    public int getJdbcType() {
        return jdbcType;
    }

    public StdDataType getStdDataType() {
        return stdDataType;
    }

    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        return stdDataType.convert(value, errorFactory);
    }

    private static final Map<String, StdSqlType> stdNameMap = new HashMap<>();
    private static final Map<Integer, StdSqlType> jdbcTypeMap = new HashMap<>();

    private static final Map<StdDataType, StdSqlType> stdTypeMap = new HashMap<>();

    static {
        stdNameMap.put("INT", INTEGER);

        for (StdSqlType type : StdSqlType.values()) {
            stdNameMap.put(type.getName(), type);

            if (!jdbcTypeMap.containsKey(type.getJdbcType()))
                jdbcTypeMap.put(type.getJdbcType(), type);

            stdTypeMap.put(type.getStdDataType(), type);
        }
        stdTypeMap.put(StdDataType.STRING, StdSqlType.VARCHAR);
        stdTypeMap.put(StdDataType.BYTES, StdSqlType.VARBINARY);
    }

    public boolean isCompatibleWith(StdSqlType type) {
        if (this == type)
            return true;

        switch (this) {
            case BIGINT:
                return type == INTEGER || type == SMALLINT;
            case DOUBLE:
                return type == FLOAT;
        }

        return false;
    }

    public static Set<String> getNames() {
        return Collections.unmodifiableSet(stdNameMap.keySet());
    }

    public static StdSqlType fromJdbcType(int jdbcType) {
        return jdbcTypeMap.get(jdbcType);
    }

    @StaticFactoryMethod
    public static StdSqlType fromStdName(String stdName) {
        return stdNameMap.get(stdName);
    }

    public static StdSqlType fromStdDataTYpe(StdDataType dataType) {
        return stdTypeMap.get(dataType);
    }

    public static StdSqlType fromJavaClass(Class<?> clazz) {
        StdDataType dataType = StdDataType.fromJavaClass(clazz);
        if (dataType == null)
            return null;
        return StdSqlType.fromStdDataTYpe(dataType);
    }
}