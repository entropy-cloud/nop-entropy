/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import io.nop.commons.type.StdSqlType;

/**
 * 对SQL文本片段的简单封装，用于组装函数表达式
 *
 * @author canonical_entropy@163.com
 */
public class StringSqlExpr implements ISqlExpr {
    private static final long serialVersionUID = -7577677453387912152L;

    private final String text;

    protected StringSqlExpr(String text) {
        this.text = text;
    }

    public static ISqlExpr makeExpr(String text) {
        return new StringSqlExpr(text);
    }

    public static ISqlExpr makeExpr(StdSqlType dataType, String text) {
        return new TypedStringSqlExpr(dataType, text);
    }

    static class TypedStringSqlExpr extends StringSqlExpr {
        private static final long serialVersionUID = -4152093898953022091L;

        private final StdSqlType dataType;

        public TypedStringSqlExpr(StdSqlType dataType, String text) {
            super(text);
            this.dataType = dataType;
        }

        @Override
        public StdSqlType getStdSqlType() {
            return dataType;
        }
    }

    @Override
    public StdSqlType getStdSqlType() {
        return null;
    }

    public String getSqlString() {
        return text;
    }

    @Override
    public void appendTo(SQL.SqlBuilder sb) {
        sb.append(text);
    }
}