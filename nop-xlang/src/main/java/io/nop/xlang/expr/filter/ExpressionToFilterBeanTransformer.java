/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr.filter;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.XLangASTHelper;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ERR_EXPR_UNSUPPORTED_OP;
import static io.nop.xlang.XLangErrors.ERR_FILTER_NOT_ALLOW_EXPR;
import static io.nop.xlang.XLangErrors.ERR_FILTER_OP_INVALID_ARG_COUNT;

/**
 * 将表达式AST转换为FilterBean结构，抽取其中的逻辑判断语句，构成Predicate Tree。
 * 例如 a == 1 and b > 3 转换为
 * <pre>{@code
 *   <and>
 *     <eq name="a" value="1" />
 *     <eq name="b" value="3" />
 *   </and>
 * }</pre>
 */
public class ExpressionToFilterBeanTransformer {
    private static final Logger log = LoggerFactory.getLogger(ExpressionToFilterBeanTransformer.class);
    private boolean allowExprOp;

    public ExpressionToFilterBeanTransformer(boolean allowExprOp) {
        this.allowExprOp = allowExprOp;
    }

    public ExpressionToFilterBeanTransformer() {
        this(true);
    }

    public TreeBean transform(Expression expr) {
        switch (expr.getASTKind()) {
            case Literal: {
                return transformLiteral((Literal) expr);
            }
            case UnaryExpression:
                return transformUnary((UnaryExpression) expr);
            case BinaryExpression: {
                return transformBinary((BinaryExpression) expr);
            }
            case AssertOpExpression:
                return transformAssertOp((AssertOpExpression) expr);
            case CompareOpExpression:
                return transformCompareOp((CompareOpExpression) expr);
            case LogicalExpression:
                return transformLogical((LogicalExpression) expr);
            case CallExpression:
                return transformCall((CallExpression) expr);
            default: {
                return transformOtherExpr(expr);
            }
        }
    }

    TreeBean transformLiteral(Literal literal) {
        boolean b = ConvertHelper.toTruthy(literal.getValue());
        return b ? FilterBeans.alwaysTrue() : FilterBeans.alwaysFalse();
    }

    TreeBean transformUnary(UnaryExpression expr) {
        if (expr.getOperator() == XLangOperator.NOT) {
            return FilterBeans.not(transform(expr.getArgument()));
        }
        return transformOtherExpr(expr);
    }

    TreeBean transformBinary(BinaryExpression expr) {
        XLangOperator op = expr.getOperator();
        switch (op) {
            case AND:
                return FilterBeans.and(transform(expr.getLeft()), transform(expr.getRight()));
            case OR:
                return FilterBeans.or(transform(expr.getLeft()), transform(expr.getRight()));
            default: {
                boolean leftIsName = XLangASTHelper.isQualifiedName(expr.getLeft());
                boolean rightIsName = XLangASTHelper.isQualifiedName(expr.getRight());
                String filterOp = expr.getOperator().toFilterOp();
                if (filterOp == null)
                    throw new NopException(ERR_EXPR_UNSUPPORTED_OP).source(expr)
                            .param(ARG_OP, expr.getOperator()).param(ARG_EXPR, expr);

                if (leftIsName || rightIsName) {
                    if (leftIsName && rightIsName) {
                        String leftName = XLangASTHelper.getQualifiedName(expr.getLeft());
                        String rightName = XLangASTHelper.getQualifiedName(expr.getRight());
                        return FilterBeans.propCompareOp(filterOp, leftName, rightName);
                    } else if (leftIsName) {
                        String name = XLangASTHelper.getQualifiedName(expr.getLeft());
                        Object value = XLangASTHelper.toJsonValue(expr.getRight());
                        return FilterBeans.compareOp(filterOp, name, value);
                    } else {
                        XLangOperator reverseOp = expr.getOperator().switchLeftRight();
                        if (reverseOp == null)
                            throw new NopException(ERR_EXPR_UNSUPPORTED_OP).source(expr)
                                    .param(ARG_OP, expr.getOperator()).param(ARG_EXPR, expr);
                        String rightName = XLangASTHelper.getQualifiedName(expr.getRight());
                        Object leftValue = XLangASTHelper.toJsonValue(expr.getLeft());
                        return FilterBeans.compareOp(filterOp, rightName, leftValue);
                    }
                }
                return transformOtherExpr(expr);
            }
        }
    }

    TreeBean transformAssertOp(AssertOpExpression expr) {
        if (XLangASTHelper.isQualifiedName(expr.getValue())) {
            String name = expr.getValue().toExprString();
            return FilterBeans.assertOp(expr.getOp(), name);
        } else {
            return transformOtherExpr(expr);
        }
    }

    TreeBean transformCompareOp(CompareOpExpression expr) {
        if (XLangASTHelper.isQualifiedName(expr.getLeft())) {
            if (XLangASTHelper.isJsonValue(expr.getRight())) {
                String name = expr.getLeft().toExprString();
                return FilterBeans.compareOp(expr.getOp(), name, XLangASTHelper.toJsonValue(expr.getRight()));
            }
        }
        return transformOtherExpr(expr);
    }

    TreeBean transformCall(CallExpression expr) {
        if (expr.getCallee().getASTKind() != XLangASTKind.Identifier)
            throw new NopException(ERR_EXPR_UNSUPPORTED_OP).source(expr).param(ARG_EXPR, expr).param(ARG_OP, expr.getCallee().toExprString());

        String op = ((Identifier) expr.getCallee()).getName();
        FilterOp filterOp = FilterOp.fromName(op);
        if (filterOp == null)
            throw new NopException(ERR_EXPR_UNSUPPORTED_OP).source(expr).param(ARG_EXPR, expr).param(ARG_OP, op);

        if (filterOp.isAssertOp()) {
            if (expr.getArguments().size() != 1)
                throw new NopException(ERR_FILTER_OP_INVALID_ARG_COUNT)
                        .source(expr).param(ARG_OP, op).param(ARG_EXPR, expr)
                        .param(ARG_EXPECTED, 1)
                        .param(ARG_ARG_COUNT, expr.getArguments().size());

            Expression arg = expr.getArguments().get(0);
            if (!XLangASTHelper.isQualifiedName(arg))
                throw new NopException(ERR_FILTER_NOT_ALLOW_EXPR).source(expr).param(ARG_EXPR, expr);

            String name = arg.toExprString();
            return FilterBeans.assertOp(op, name);
        } else if (filterOp.isCompareOp()) {
            if (expr.getArguments().size() != 2)
                throw new NopException(ERR_FILTER_OP_INVALID_ARG_COUNT)
                        .source(expr).param(ARG_OP, op).param(ARG_EXPR, expr)
                        .param(ARG_EXPECTED, 2)
                        .param(ARG_ARG_COUNT, expr.getArguments().size());

            Expression left = expr.getArguments().get(0);
            Expression right = expr.getArguments().get(1);

            boolean leftIsName = XLangASTHelper.isQualifiedName(left);
            boolean rightIsName = XLangASTHelper.isQualifiedName(right);

            if (leftIsName && rightIsName) {
                String leftName = left.toExprString();
                String rightName = right.toExprString();
                return FilterBeans.propCompareOp(op, leftName, rightName);
            } else if (leftIsName) {
                String name = left.toExprString();
                Object value = XLangASTHelper.toJsonValue(right);
                return FilterBeans.compareOp(op, name, value);
            } else if (rightIsName) {
                Object value = XLangASTHelper.toJsonValue(left);
                String name = right.toExprString();
                return FilterBeans.compareOp(op, name, value);
            } else {
                throw new NopException(ERR_FILTER_NOT_ALLOW_EXPR).source(expr).param(ARG_EXPR, expr);
            }
        } else if (filterOp.isBetweenOp()) {
            if (expr.getArguments().size() < 3 || expr.getArguments().size() > 5)
                throw new NopException(ERR_FILTER_OP_INVALID_ARG_COUNT)
                        .source(expr).param(ARG_OP, op).param(ARG_EXPR, expr)
                        .param(ARG_EXPECTED, 3)
                        .param(ARG_ARG_COUNT, expr.getArguments().size());

            Expression nameExpr = expr.getArguments().get(0);
            Expression minExpr = expr.getArguments().get(1);
            Expression maxExpr = expr.getArguments().get(2);
            Expression excludeMinExpr = expr.getArgument(3);
            Expression excludeMaxExpr = expr.getArgument(4);
            if (excludeMinExpr != null && excludeMinExpr.getASTKind() != XLangASTKind.Literal)
                throw new NopException(ERR_FILTER_NOT_ALLOW_EXPR)
                        .source(expr).param(ARG_EXPR, excludeMinExpr);
            if (excludeMaxExpr != null && excludeMaxExpr.getASTKind() != XLangASTKind.Literal)
                throw new NopException(ERR_FILTER_NOT_ALLOW_EXPR)
                        .source(expr).param(ARG_EXPR, excludeMaxExpr);

            if (!XLangASTHelper.isQualifiedName(nameExpr)) {
                return transformOtherExpr(expr);
            }

            String name = nameExpr.toExprString();
            Object min = XLangASTHelper.toJsonValue(minExpr);
            Object max = XLangASTHelper.toJsonValue(maxExpr);
            boolean excludeMin = excludeMinExpr != null && ConvertHelper.toTruthy(((Literal) excludeMinExpr).getValue());
            boolean excludeMax = excludeMaxExpr != null && ConvertHelper.toTruthy(((Literal) excludeMaxExpr).getValue());
            return FilterBeans.betweenOp(op, name, min, max, excludeMin, excludeMax);
        } else {
            return transformOtherExpr(expr);
        }
    }

    TreeBean transformOtherExpr(Expression expr) {
        if (!allowExprOp)
            throw new NopException(ERR_FILTER_NOT_ALLOW_EXPR).source(expr).param(ARG_EXPR, expr);
        TreeBean ret = new TreeBean();
        ret.setLocation(expr.getLocation());
        ret.setTagName(FilterBeanConstants.FILTER_OP_EXPR);
        ret.setAttr(FilterBeanConstants.FILTER_ATTR_VALUE, expr);
        return ret;
    }

    TreeBean transformLogical(LogicalExpression expr) {
        XLangOperator op = expr.getOperator();
        switch (op) {
            case AND:
                return FilterBeans.and(transform(expr.getLeft()), transform(expr.getRight()));
            case OR:
                return FilterBeans.or(transform(expr.getLeft()), transform(expr.getRight()));
            default:
                throw new NopException(ERR_EXPR_UNSUPPORTED_OP).source(expr)
                        .param(ARG_EXPR, expr).param(ARG_OP, op);
        }
    }
}