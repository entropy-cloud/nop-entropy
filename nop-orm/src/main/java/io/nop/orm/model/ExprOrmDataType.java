/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;
import io.nop.core.lang.sql.StdSqlType;

import java.util.EnumMap;

public class ExprOrmDataType implements IOrmDataType {
    private final StdSqlType sqlType;

    private ExprOrmDataType(StdSqlType sqlType) {
        this.sqlType = sqlType;
    }

    @Override
    public StdDataType getStdDataType() {
        return sqlType.getStdDataType();
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.EXPR;
    }

    static final EnumMap<StdSqlType, ExprOrmDataType> s_map = new EnumMap<>(StdSqlType.class);

    static {
        for (StdSqlType sqlType : StdSqlType.values()) {
            s_map.put(sqlType, new ExprOrmDataType(sqlType));
        }
    }

    public static ExprOrmDataType fromSqlType(StdSqlType sqlType) {
        return s_map.get(sqlType);
    }
}
