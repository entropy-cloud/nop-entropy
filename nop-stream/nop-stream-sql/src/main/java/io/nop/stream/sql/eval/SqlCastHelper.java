/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

/**
 * WI29: runtime CAST helper for the stream SQL scalar subset. The EQL parser
 * validates the CAST target name against StdSqlType (WI9a aliases included), so the
 * helper receives StdSqlType-style names and maps them onto the nine managed types
 * (sql-compiler-contract §1.3 mapping). Names outside the managed closed set —
 * DECIMAL/NUMBER/NUMERIC/DATE/TIMESTAMP 等 — fail fast: the §4a#5 column-binding
 * exclusion is NOT widened, only this literal-target conversion surface opens.
 *
 * <p>Null propagates as null (three-valued semantics). Called from generated xpl
 * bodies via import + static call (SqlRowAggregateOps precedent).
 */
public final class SqlCastHelper {

    private SqlCastHelper() {
    }

    public static Object cast(Object value, String typeName) {
        if (value == null)
            return null;
        String type = typeName == null ? null : typeName.toUpperCase();
        if (type == null)
            return null;
        switch (type) {
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
                return value.toString();
            case "INT":
            case "INTEGER":
            case "TINYINT":
            case "SMALLINT":
                return toInt(value);
            case "BIGINT":
                return toLong(value);
            case "FLOAT":
            case "REAL":
                return toFloat(value);
            case "DOUBLE":
                return toDouble(value);
            case "BOOLEAN":
            case "BOOL":
                return toBoolean(value);
            case "VARBINARY":
            case "BLOB":
            case "BINARY":
                return toBytes(value);
            default:
                // DECIMAL/NUMBER/NUMERIC/DATE/TIMESTAMP 等——§4a#5 排除不放宽
                throw (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                        .param(ARG_ARG_NAME, "cast")
                        .param(ARG_DETAIL, "CAST target type '" + typeName
                                + "' is outside the stream SQL managed-type closed set "
                                + "(string/int/bigint/smallint/tinyint/float/double/boolean/bytes)");
        }
    }

    private static Integer toInt(Object v) {
        return ConvertHelper.toInt(v, err -> castError(v, "int"));
    }

    private static Long toLong(Object v) {
        return ConvertHelper.toLong(v, err -> castError(v, "bigint"));
    }

    private static Float toFloat(Object v) {
        return ConvertHelper.toFloat(v, err -> castError(v, "float"));
    }

    private static Double toDouble(Object v) {
        return ConvertHelper.toDouble(v, err -> castError(v, "double"));
    }

    private static Boolean toBoolean(Object v) {
        if (v instanceof Boolean)
            return (Boolean) v;
        if (v instanceof Number)
            return ((Number) v).intValue() != 0;
        String s = v.toString();
        if ("true".equalsIgnoreCase(s) || "1".equals(s))
            return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s) || "0".equals(s))
            return Boolean.FALSE;
        throw castError(v, "boolean");
    }

    private static byte[] toBytes(Object v) {
        if (v instanceof byte[])
            return (byte[]) v;
        return v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static StreamException castError(Object v, String typeName) {
        return (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "cast")
                .param(ARG_DETAIL, "cannot cast value to " + typeName + ": " + v);
    }
}

