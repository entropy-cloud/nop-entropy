/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.model.SqlDataTypeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.nop.dao.DaoErrors.ARG_ALLOWED_TYPES;
import static io.nop.dao.DaoErrors.ARG_DATA_TYPE;
import static io.nop.dao.DaoErrors.ARG_PRECISION;
import static io.nop.dao.DaoErrors.ERR_DIALECT_DATA_TYPE_NOT_SUPPORTED;
import static io.nop.dao.DaoErrors.ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED;

public class SqlDataTypeMapping {
    private Map<StdSqlType, List<SqlDataTypeModel>> stdToNative = new HashMap<>();
    private Map<String, SqlDataTypeModel> nativeTypes = new HashMap<>();

    public void init() {
        /**
         * 对标准数据类型相同的数据类型，按照precision从小到大排序
         */
        for (List<SqlDataTypeModel> list : stdToNative.values()) {
            Collections.sort(list, (t1, t2) -> {
                Integer p1 = t1.getPrecision();
                Integer p2 = t2.getPrecision();
                if (p1 == null)
                    p1 = Integer.MAX_VALUE;
                if (p2 == null)
                    p2 = Integer.MAX_VALUE;
                return Integer.compare(p1, p2);
            });
        }
    }

    public void register(SqlDataTypeModel dataType) {
        List<SqlDataTypeModel> list = stdToNative.computeIfAbsent(dataType.getStdSqlType(), k -> new ArrayList<>());
        // 标记为deprecated的类型不参与从StdSqlType到NativeSqlType的转换
        if (!dataType.isDeprecated())
            list.add(dataType);

        nativeTypes.put(dataType.getName().toUpperCase(), dataType);

        if (dataType.getAlias() != null) {
            for (String alias : dataType.getAlias()) {
                nativeTypes.put(alias.toUpperCase(), dataType);
            }
        }
    }

    public SqlDataTypeModel getNativeType(String sqlTypeName) {
        return getNativeType(sqlTypeName, false);
    }

    public SqlDataTypeModel getNativeType(String sqlTypeName, boolean ignoreUnknown) {
        SqlDataTypeModel type = nativeTypes.get(sqlTypeName.toUpperCase(Locale.ENGLISH));
        if (type == null) {
            if (ignoreUnknown)
                return null;
            
            throw new NopException(ERR_DIALECT_DATA_TYPE_NOT_SUPPORTED).param(ARG_DATA_TYPE, sqlTypeName)
                    .param(ARG_ALLOWED_TYPES, nativeTypes.keySet());
        }
        return type;
    }

    public SQLDataType stdToNativeSqlType(StdSqlType sqlType, int precision, int scale) {
        List<SqlDataTypeModel> list = stdToNative.get(sqlType);
        if (list == null) {
            if (sqlType == StdSqlType.TINYINT) {
                return stdToNativeSqlType(StdSqlType.SMALLINT, precision, scale);
            }
            if (sqlType == StdSqlType.SMALLINT)
                return stdToNativeSqlType(StdSqlType.INTEGER, precision, scale);

            throw new NopException(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED).param(ARG_DATA_TYPE, sqlType.getName())
                    .param(ARG_ALLOWED_TYPES, stdToNative.keySet());
        }
        for (SqlDataTypeModel dataTypeModel : list) {
            if (dataTypeModel.isDeprecated())
                continue;

            if (dataTypeModel.isAllowPrecision(precision)) {
                String code = dataTypeModel.getCode();
                if (code == null)
                    code = dataTypeModel.getName();
                return new SQLDataType(code,
                        sqlType.isAllowPrecision() && !Boolean.FALSE.equals(dataTypeModel.getAllowPrecision())
                                ? precision : -1,
                        sqlType.isAllowScale() ? scale : -1);
            }
        }
        throw new NopException(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED).param(ARG_DATA_TYPE, sqlType.getName())
                .param(ARG_ALLOWED_TYPES, stdToNative.keySet()).param(ARG_PRECISION, precision);
    }
}
