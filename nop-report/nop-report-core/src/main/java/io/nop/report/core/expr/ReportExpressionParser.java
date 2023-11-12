/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprFeatures;

import java.util.List;

/**
 * 在XLang表达式的基础上增加CellCoordinate语法的支持。可以支持简单的Excel公式形式，例如 SUM(A3:A5) + C1
 */
public class ReportExpressionParser extends AbstractExcelFormulaParser {
    public ReportExpressionParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

    private Expression checkValueExpr(Expression expr) {
        if (expr instanceof CustomExpression) {
            CustomExpression custom = (CustomExpression) expr;
            if (custom.getExecutable() instanceof CellLayerCoordinateExecutable) {
                MemberExpression member = new MemberExpression();
                member.setObject(expr);
                member.setProperty(Identifier.valueOf(null, "value"));
                member.setOptional(true);
                return member;
            }
        }
        return expr;
    }

    @Override
    protected Expression newBinaryExpr(SourceLocation loc, XLangOperator op, Expression x, Expression y) {
        return super.newBinaryExpr(loc, op, checkValueExpr(x), checkValueExpr(y));
    }

    @Override
    protected Expression newLogicExpr(SourceLocation loc, XLangOperator op, List<Expression> exprs, int startIndex) {
        for (int i = 0, n = exprs.size(); i < n; i++) {
            exprs.set(i, checkValueExpr(exprs.get(i)));
        }
        return super.newLogicExpr(loc, op, exprs, startIndex);
    }

    @Override
    protected Expression newUnaryExpr(SourceLocation loc, XLangOperator op, Expression x) {
        return super.newUnaryExpr(loc, op, checkValueExpr(x));
    }
}