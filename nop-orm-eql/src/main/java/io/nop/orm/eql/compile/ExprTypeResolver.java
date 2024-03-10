/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdSqlType;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.SingleColumnExprMeta;
import io.nop.orm.model.ExprOrmDataType;

public class ExprTypeResolver {
    private final IDialect dialect;

    public ExprTypeResolver(IDialect dialect) {
        this.dialect = dialect;
    }

    public ISqlExprMeta resolveExprMeta(SqlExpr expr) {
        ISqlExprMeta exprMeta = expr.getResolvedExprMeta();
        if (exprMeta == null) {
            StdSqlType sqlType = resolveType(expr);
            IDataParameterBinder binder = dialect.getDataParameterBinder(sqlType.getStdDataType(), sqlType);
            if (binder == null)
                binder = DataParameterBinders.ANY;
            exprMeta = new SingleColumnExprMeta(null, binder, ExprOrmDataType.fromSqlType(sqlType));
            expr.setResolvedExprMeta(exprMeta);
        }
        return exprMeta;
    }

    private StdSqlType resolveType(SqlExpr expr) {
        ISqlExprMeta exprMeta = expr.getResolvedExprMeta();
        if (exprMeta != null)
            return exprMeta.getStdSqlType();

        StdSqlType type = StdSqlType.OTHER;
        switch (expr.getASTKind()) {
            case SqlBinaryExpr: {
                type = resolveBinaryExprType((SqlBinaryExpr) expr);
                break;
            }
            case SqlDateTimeLiteral: {
                SqlDateTimeLiteral literal = (SqlDateTimeLiteral) expr;
                switch (literal.getType()) {
                    case DATE:
                        type = StdSqlType.DATE;
                        break;
                    case TIME:
                        type = StdSqlType.TIME;
                        break;
                    default:
                        type = StdSqlType.TIMESTAMP;
                }
                break;
            }
            case SqlStringLiteral: {
                type = StdSqlType.VARCHAR;
                break;
            }
            case SqlRegularFunction: {
                type = resolveFuncType((SqlRegularFunction) expr);
                break;
            }
            case SqlAggregateFunction: {
                type = resolveAggFuncType((SqlAggregateFunction) expr);
                break;
            }
        }
        return type;
    }

    StdSqlType resolveBinaryExprType(SqlBinaryExpr expr) {
        return expr.getOperator().getResultSqlType();
    }

    StdSqlType resolveFuncType(SqlRegularFunction fn) {
        ISQLFunction func = fn.getResolvedFunction();
        for (SqlExpr arg : fn.getArgs()) {
            resolveExprMeta(arg);
        }
        return func.getReturnType(fn.getArgs(), dialect);
    }

    StdSqlType resolveAggFuncType(SqlAggregateFunction fn) {
        String name = fn.getName();
        if (name.endsWith(OrmEqlConstants.FUNC_COUNT)) {
            return StdSqlType.BIGINT;
        }
        SqlExpr arg = fn.getArgs().get(0);
        resolveExprMeta(arg);
        return resolveType(arg);
    }
}
