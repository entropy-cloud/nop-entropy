/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect.function;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.lang.sql.StdSqlType;
import io.nop.dao.dialect.IDialect;

import java.util.List;

public interface ISQLFunction {
    /**
     * 函数名称
     *
     * @return
     */
    String getName();

    /**
     * 最少参数个数
     *
     * @return
     */
    int getMinArgCount();

    /**
     * 最多允许的参数个数
     *
     * @return
     */
    int getMaxArgCount();

    /**
     * 调用时如果没有参数，是否必须要有括号
     *
     * @return
     */
    boolean hasParentheses();

    List<StdSqlType> getArgTypes();

    /**
     * 获得函数的返回值类型
     *
     * @param argExprs 第一个参数的数据类型
     * @param dialect
     * @return
     */
    StdSqlType getReturnType(List<? extends ISqlExpr> argExprs, IDialect dialect);

    StdSqlType getArgType(List<? extends ISqlExpr> argExprs, int argIndex, IDialect dialect);

    /**
     * 构造函数文本
     */
    SqlExprList buildFunctionExpr(SourceLocation loc, List<? extends ISqlExpr> argExprs, IDialect dialect);
}