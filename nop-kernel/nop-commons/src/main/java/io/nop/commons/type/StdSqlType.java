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

    /**
     * WI9b: 本地时区时间（无偏移存储、按会话时区渲染；Standard SQL:1999 类型）。
     * JDBC 无 LOCAL 时区标准码，按注释体原始选择落在 {@link Types#OTHER}
     * （JDBC 4.2 的 TIME_WITH_TIMEZONE=2013 在真实驱动上表示保偏移的
     * TIME WITH TIME ZONE，见裁定文档 §2.1 一手探测，不挂给本成员）。
     * DDL 方向由方言 {@code <sqlDataType>} 决定（当前全部方言无原生宿主，
     * 无映射方言经 stdToNativeSqlType 响亮报错）。取值裁定见
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md §三。
     */
    TIME_WITH_LOCAL_TIME_ZONE(false, false, Types.OTHER, StdDataType.TIME),

    TIMESTAMP(false, false, Types.TIMESTAMP, StdDataType.TIMESTAMP),

    /**
     * WI9b: 本地时区时间戳（Oracle TIMESTAMP WITH LOCAL TIME ZONE 同名语义：
     * 存储、读取分别按数据库/会话时区归一化）。jdbcType/stdDataType 取值与
     * {@link #TIME_WITH_LOCAL_TIME_ZONE} 同理，见裁定文档 §三。
     */
    TIMESTAMP_WITH_LOCAL_TIME_ZONE(false, false, Types.OTHER, StdDataType.TIMESTAMP),
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
    GEOMETRY(false, false, 2015, StdDataType.GEOMETRY),

    /**
     * WI9a: UUID（通用唯一标识符）。JDBC 无标准 UUID 类型，按 {@link #JSON} 同例落在
     * VARCHAR/STRING 家族（StdDataType 无独立 UUID 成员）。DDL 方向由方言
     * {@code <sqlDataType>} 决定（h2/postgresql/duckdb 原生 UUID、mssql UNIQUEIDENTIFIER，
     * 无映射方言经 stdToNativeSqlType 响亮报错）。取值裁定见
     * ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md §四。
     */
    UUID(false, false, Types.VARCHAR, StdDataType.STRING);

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

        // WI9a: CAST 类型别名 —— 仅扩展解析接受集（stdNameMap 键集），不修改任何既有映射条目
        // （G-3 裁决 TEXT→CLOB / BOOL→BOOLEAN；NUMBER 目标由 NUMERIC 修订为 DECIMAL，
        // 见 ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md §三）。
        // UUID 为枚举常量，经下方 values() 循环自动入表，无需显式 put。
        stdNameMap.put("TEXT", CLOB);
        stdNameMap.put("NUMBER", DECIMAL);
        stdNameMap.put("BOOL", BOOLEAN);

        for (StdSqlType type : StdSqlType.values()) {
            stdNameMap.put(type.getName(), type);

            if (!jdbcTypeMap.containsKey(type.getJdbcType()))
                jdbcTypeMap.put(type.getJdbcType(), type);

            stdTypeMap.put(type.getStdDataType(), type);
        }
        stdTypeMap.put(StdDataType.STRING, StdSqlType.VARCHAR);
        stdTypeMap.put(StdDataType.BYTES, StdSqlType.VARBINARY);

        // WI9b: 时区两成员的声明序先于 OTHER（jdbcTypeMap 先到先得键）、晚于 TIME/TIMESTAMP
        // （stdTypeMap last-wins 键）——按 STRING/BYTES 重钉先例显式重钉，保持既有反查零翻转
        // （裁定文档 §三：fromJdbcType(OTHER)=OTHER、fromStdDataTYpe(TIME/TIMESTAMP) 不变；
        // fromJdbcType(Types.TIME_WITH_TIMEZONE/TIMESTAMP_WITH_TIMEZONE) 保持 null，不改读回归类）
        jdbcTypeMap.put(Types.OTHER, StdSqlType.OTHER);
        stdTypeMap.put(StdDataType.TIME, StdSqlType.TIME);
        stdTypeMap.put(StdDataType.TIMESTAMP, StdSqlType.TIMESTAMP);
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