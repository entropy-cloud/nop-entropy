/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ISQLFunction的转换结果, 假定exprs为展平后的语法树，只要顺序输出即可。
 *
 * @author canonical_entropy@163.com
 */
public final class SqlExprList implements ISqlExpr {
    private static final long serialVersionUID = 2286519451656304634L;
    /**
     * 函数名
     */
    private final String name;
    private final StdSqlType sqlType;

    private final List<ISqlExpr> exprs;

    public SqlExprList(@Nonnull String name, StdSqlType sqlType, @Nonnull List<? extends ISqlExpr> exprs) {
        this.name = name;
        this.sqlType = sqlType;
        this.exprs = new ArrayList<ISqlExpr>(exprs);
    }

    public SqlExprList(String name, StdSqlType sqlType, ISqlExpr... exprs) {
        this(name, sqlType, Arrays.asList(exprs));
    }

    public SqlExprList(String name, StdSqlType sqlType) {
        this(name, sqlType, Collections.emptyList());
    }

    public SqlExprList(String name) {
        this(name, null);
    }

    public String getName() {
        return name;
    }

    public SqlExprList add(ISqlExpr expr) {
        this.exprs.add(expr);
        return this;
    }

    public SqlExprList add(String text) {
        this.exprs.add(StringSqlExpr.makeExpr(text));
        return this;
    }

    public List<ISqlExpr> getExprs() {
        return exprs;
    }

    @Override
    public StdSqlType getStdSqlType() {
        return sqlType;
    }

    @Override
    public void appendTo(SQL.SqlBuilder sb) {
        for (ISqlExpr expr : exprs) {
            expr.appendTo(sb);
        }
    }

    public String getSqlString() {
        SQL.SqlBuilder sb = SQL.begin();
        appendTo(sb);
        return sb.end().getText();
    }
}