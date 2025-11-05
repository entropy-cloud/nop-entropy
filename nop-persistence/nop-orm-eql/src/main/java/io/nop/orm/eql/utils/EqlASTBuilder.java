/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.utils;

import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlNullLiteral;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.enums.SqlDateTimeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class EqlASTBuilder {

    public static SqlColumnName colName(String owner, String colName) {
        SqlColumnName col = new SqlColumnName();
        col.setName(colName);
        col.setOwner(qualifier(owner));
        return col;
    }

    public static SqlQualifiedName qualifier(String name) {
        if (StringHelper.isEmpty(name))
            return null;
        SqlQualifiedName ret = new SqlQualifiedName();
        int pos = name.indexOf('.');
        if (pos > 0) {
            String rootName = name.substring(0, pos);
            String nextName = name.substring(pos + 1);
            ret.setName(rootName);
            ret.setNext(qualifier(nextName));
        } else {
            ret.setName(name);
        }
        return ret;
    }

    public static SqlExpr literal(Object value) {
        if (value == null)
            return new SqlNullLiteral();

        if (value instanceof Number) {
            SqlNumberLiteral literal = new SqlNumberLiteral();
            literal.setValue(value.toString());
            return literal;
        } else if (value instanceof String) {
            SqlStringLiteral literal = new SqlStringLiteral();
            literal.setValue(value.toString());
            return literal;
        } else if (value instanceof Boolean) {
            SqlBooleanLiteral literal = new SqlBooleanLiteral();
            literal.setValue((Boolean) value);
            return literal;
        } else if (value instanceof LocalDate) {
            SqlDateTimeLiteral literal = new SqlDateTimeLiteral();
            literal.setType(SqlDateTimeType.DATE);
            literal.setValue(value.toString());
            return literal;
        } else if (value instanceof LocalDateTime) {
            SqlDateTimeLiteral literal = new SqlDateTimeLiteral();
            literal.setType(SqlDateTimeType.TIMESTAMP);
            literal.setValue(value.toString());
            return literal;
        } else if (value instanceof LocalTime) {
            SqlDateTimeLiteral literal = new SqlDateTimeLiteral();
            literal.setType(SqlDateTimeType.TIME);
            literal.setValue(value.toString());
            return literal;
        } else {
            SqlStringLiteral literal = new SqlStringLiteral();
            literal.setValue(value.toString());
            return literal;
        }
    }
}
