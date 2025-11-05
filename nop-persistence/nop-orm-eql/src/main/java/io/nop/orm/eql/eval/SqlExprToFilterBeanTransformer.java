package io.nop.orm.eql.eval;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlLiteral;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlUnaryExpr;
import io.nop.xlang.ast.XLangOperator;

import java.util.List;
import java.util.stream.Collectors;

import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPR;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_EVAL_EXPR;

/**
 * 将SQL表达式转换为XLang表达式
 */
public class SqlExprToFilterBeanTransformer {

    public TreeBean transform(SqlExpr expr) {
        if (expr == null)
            return null;

        switch (expr.getASTKind()) {
            case SqlAndExpr:
                return transformAnd((SqlAndExpr) expr);
            case SqlOrExpr:
                return transformOr((SqlOrExpr) expr);
            case SqlNotExpr:
                return transformNot((SqlNotExpr) expr);
            case SqlBinaryExpr:
                return transformBinary((SqlBinaryExpr) expr);
            case SqlLikeExpr:
                return transformLike((SqlLikeExpr) expr);
            case SqlUnaryExpr:
                return transformUnary((SqlUnaryExpr) expr);
            case SqlIsNullExpr:
                return transformIsNull((SqlIsNullExpr) expr);
            case SqlInValuesExpr:
                return transformInValues((SqlInValuesExpr) expr);
            case SqlBetweenExpr:
                return transformBetween((SqlBetweenExpr) expr);
        }
        throw new NopException(ERR_EQL_UNSUPPORTED_EVAL_EXPR)
                .param(ARG_EXPR, expr);
    }

    private Object getValue(SqlExpr expr) {
        if (expr instanceof SqlLiteral)
            return ((SqlLiteral) expr).getLiteralValue();
        throw new IllegalArgumentException("expr-not-literal:" + expr);
    }

    private TreeBean transformAnd(SqlAndExpr expr) {
        TreeBean and = FilterBeans.and(
                transform(expr.getLeft()), transform(expr.getRight()));
        and.setLocation(expr.getLocation());
        return and;
    }

    private TreeBean transformOr(SqlOrExpr expr) {
        TreeBean or = FilterBeans.or(
                transform(expr.getLeft()), transform(expr.getRight()));
        or.setLocation(expr.getLocation());
        return or;
    }

    private TreeBean transformNot(SqlNotExpr expr) {
        TreeBean not = FilterBeans.or(
                transform(expr.getExpr()));
        not.setLocation(expr.getLocation());
        return not;
    }

    private TreeBean transformBinary(SqlBinaryExpr expr) {
        SqlExpr left = expr.getLeft();
        SqlExpr right = expr.getRight();
        boolean leftIsName = left.getASTKind() == EqlASTKind.SqlColumnName;
        boolean rightIsName = right.getASTKind() == EqlASTKind.SqlColumnName;

        XLangOperator op = SqlExprTransformHelper.toXLangOperator(expr.getOperator());
        String tagName = op.toFilterOp();
        if (tagName == null)
            throw new IllegalArgumentException("expr-not-supported-binary-expr:" + expr + ",op=" + op);

        if (leftIsName || rightIsName) {
            if (leftIsName && rightIsName) {
                String leftName = getName(left);
                String rightNam = getName(right);
                return FilterBeans.propCompareOp(tagName, leftName, rightNam);
            } else if (leftIsName) {
                String leftName = getName(left);
                Object value = getValue(right);
                return FilterBeans.compareOp(tagName, leftName, value);
            } else {
                Object value = getValue(left);
                String rightName = getName(right);
                XLangOperator reverseOp = op.switchLeftRight();
                if (reverseOp == null)
                    throw new IllegalArgumentException("unsupported-binary-op:" + expr + ",op=" + op);
                return FilterBeans.compareOp(reverseOp.toFilterOp(), rightName, value);
            }
        } else {
            throw new IllegalArgumentException("unsupported-binary-expr:" + expr);
        }
    }

    private TreeBean transformLike(SqlLikeExpr expr) {
        String name = getName(expr.getExpr());
        Object value = getValue(expr.getValue());
        if (StringHelper.isEmptyObject(value))
            return FilterBeans.alwaysFalse();

        TreeBean ret = FilterBeans.like(name, value.toString());
        ret.setLocation(expr.getLocation());
        return ret;
    }

    private TreeBean transformUnary(SqlUnaryExpr expr) {
        XLangOperator op = SqlExprTransformHelper.toXLangOperator(expr.getOperator());
        if (op == XLangOperator.NOT) {
            return FilterBeans.not(transform(expr.getExpr()));
        }
        throw new IllegalArgumentException("not-supported-unary-expr:" + expr);
    }

    private TreeBean transformIsNull(SqlIsNullExpr expr) {
        String name = getName(expr.getExpr());
        TreeBean isNull = FilterBeans.isNull(name);
        isNull.setLocation(expr.getLocation());

        if (expr.getNot())
            return FilterBeans.not(isNull);
        return isNull;
    }

    private TreeBean transformBetween(SqlBetweenExpr expr) {
        String name = getName(expr.getTest());
        Object min = getValue(expr.getBegin());
        Object max = getValue(expr.getEnd());

        TreeBean ret = FilterBeans.between(name, min, max, false, false);

        if (expr.getNot()) {
            ret = FilterBeans.not(ret);
        }

        return ret;
    }

    private TreeBean transformInValues(SqlInValuesExpr expr) {
        String name = getName(expr.getExpr());

        List<Object> values = expr.getValues().stream().map(this::getValue).collect(Collectors.toList());
        TreeBean in = FilterBeans.in(name, values);
        in.setLocation(expr.getLocation());
        return in;
    }

    private String getName(SqlExpr expr) {
        if (expr.getASTKind() != EqlASTKind.SqlColumnName)
            throw new IllegalArgumentException("expr-not-sql-column-name:" + expr);
        SqlColumnName colName = (SqlColumnName) expr;
        return colName.getFullName();
    }
}