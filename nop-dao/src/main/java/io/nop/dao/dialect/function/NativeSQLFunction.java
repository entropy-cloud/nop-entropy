/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect.function;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.StringSqlExpr;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;

import java.util.ArrayList;
import java.util.List;

import static io.nop.dao.DaoErrors.ARG_FUNC_NAME;
import static io.nop.dao.DaoErrors.ARG_MAX_ARG_COUNT;
import static io.nop.dao.DaoErrors.ARG_MIN_ARG_COUNT;
import static io.nop.dao.DaoErrors.ERR_DAO_FUNC_TOO_FEW_ARGS;
import static io.nop.dao.DaoErrors.ERR_DAO_FUNC_TOO_MANY_ARGS;

public class NativeSQLFunction implements ISQLFunction {
    private final SqlNativeFunctionModel funcModel;
    private final List<StdSqlType> argTypes;
    private final int minArgCount;
    private final int maxArgCount;

    public NativeSQLFunction(SqlNativeFunctionModel funcModel) {
        this.funcModel = funcModel;
        this.argTypes = CollectionHelper.toNotNull(funcModel.getArgTypes());
        this.minArgCount = funcModel.getMinArgCount() == null ? argTypes.size() : funcModel.getMinArgCount();
        this.maxArgCount = funcModel.getMaxArgCount() == null ? argTypes.size() : funcModel.getMaxArgCount();
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + "]";
    }

    @Override
    public String getName() {
        return funcModel.getName();
    }

    @Override
    public int getMinArgCount() {
        return minArgCount;
    }

    @Override
    public int getMaxArgCount() {
        return maxArgCount;
    }

    @Override
    public boolean hasParentheses() {
        return !Boolean.FALSE.equals(funcModel.getHasParenthesis());
    }

    @Override
    public List<StdSqlType> getArgTypes() {
        return argTypes;
    }

    @Override
    public StdSqlType getReturnType(List<? extends ISqlExpr> argExprs, IDialect dialect) {
        return funcModel.getReturnType();
    }

    @Override
    public StdSqlType getArgType(List<? extends ISqlExpr> argExprs, int argIndex, IDialect dialect) {
        if (argIndex >= argTypes.size())
            return StdSqlType.ANY;
        return argTypes.get(argIndex);
    }

    public String getRealFuncName() {
        String name = funcModel.getRealName();
        if (name == null)
            name = funcModel.getName();
        return name;
    }

    @Override
    public SqlExprList buildFunctionExpr(SourceLocation loc, List<? extends ISqlExpr> argExprs, IDialect dialect) {
        if (argExprs.size() > getMaxArgCount()) {
            throw new NopException(ERR_DAO_FUNC_TOO_MANY_ARGS).loc(loc).param(ARG_FUNC_NAME, getName())
                    .param(ARG_MAX_ARG_COUNT, getMaxArgCount());
        }
        if (argExprs.size() < getMinArgCount()) {
            throw new NopException(ERR_DAO_FUNC_TOO_FEW_ARGS).loc(loc).param(ARG_FUNC_NAME, getName())
                    .param(ARG_MIN_ARG_COUNT, getMinArgCount());
        }

        if (argExprs.isEmpty() && !this.hasParentheses())
            return new SqlExprList(getName(), getReturnType(argExprs, dialect),
                    StringSqlExpr.makeExpr(getRealFuncName()));

        List<ISqlExpr> exprs = new ArrayList<>(argExprs.size() * 2 + 1);

        exprs.add(StringSqlExpr.makeExpr(getRealFuncName() + "("));
        for (int i = 0, n = argExprs.size(); i < n; i++) {
            ISqlExpr argExpr = argExprs.get(i);
            exprs.add(argExpr);
            if (i != n - 1)
                exprs.add(StringSqlExpr.makeExpr(","));
        }
        exprs.add(StringSqlExpr.makeExpr(")"));
        return new SqlExprList(getName(), getReturnType(argExprs, dialect), exprs);
    }
}
