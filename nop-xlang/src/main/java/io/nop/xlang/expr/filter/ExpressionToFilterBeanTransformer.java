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
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.XLangASTHelper;
import io.nop.xlang.ast.XLangOperator;

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
                    throw new IllegalArgumentException("unsupported-binary-op:op=" + op + ",expr=" + expr);

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
                            throw new IllegalArgumentException("unsupported-binary-op:op=" + op + ",expr=" + expr);
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

    TreeBean transformOtherExpr(Expression expr) {
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
                throw new UnsupportedOperationException("operator:" + op);
        }
    }
}