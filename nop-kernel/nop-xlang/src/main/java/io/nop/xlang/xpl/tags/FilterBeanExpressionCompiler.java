/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.Arrays;
import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.convert.ConvertHelper.defaults;
import static io.nop.api.core.convert.ConvertHelper.toBoolean;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INVALID_VAR_NAME;

public class FilterBeanExpressionCompiler extends FilterBeanVisitor<Expression> {
    static final List<String> ASSERT_OP_ARGS = Arrays.asList(FILTER_ATTR_NAME);
    static final List<String> COMPARE_OP_ARGS = Arrays.asList(FILTER_ATTR_VALUE, FILTER_ATTR_NAME, FILTER_ATTR_VALUE_NAME);
    static final List<String> BETWEEN_OP_ARGS = Arrays.asList(FILTER_ATTR_NAME, FILTER_ATTR_MIN, FILTER_ATTR_MAX,
            FILTER_ATTR_EXCLUDE_MIN, FILTER_ATTR_EXCLUDE_MAX, FILTER_ATTR_MIN_NAME, FILTER_ATTR_MAX_NAME);


    private final IXplCompiler compiler;
    private final IXLangCompileScope compileScope;

    public FilterBeanExpressionCompiler(IXplCompiler compiler, IXLangCompileScope compileScope) {
        this.compiler = compiler;
        this.compileScope = compileScope;
    }

    public FilterBeanExpressionCompiler(XLangCompileTool tool) {
        this(tool.getCompiler(), tool.getScope());
    }

    public Expression compilePredicate(XNode check) {
        return visitAnd(check, compileScope);
    }

    @Override
    protected Expression visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkArgNames((XNode) filter, COMPARE_OP_ARGS);

        String name = getName(filter);
        if (!StringHelper.isValidPropPath(name))
            throw new NopEvalException(ERR_XLANG_INVALID_VAR_NAME)
                    .source(filter).param(ARG_VAR_NAME, name);

        String valueName = getValueName(filter);
        Object rightValue = getValue(filter);

        SourceLocation loc = filter.getLocation();

        Expression left = compileNameExpr(loc, name);
        Expression right;
        if (valueName != null) {
            right = compileNameExpr(loc, valueName);
        } else {
            right = compileValueExpr(loc, rightValue);
        }

        return CompareOpExpression.valueOf(loc, left, filterOp, right);
    }

    private Expression compileNameExpr(SourceLocation loc, String name) {
        return XLangASTBuilder.buildPropExpr(loc, name);
    }

    @Override
    protected String getNameAttr(ITreeBean filter, String attrName) {
        XNode node = (XNode) filter;
        String name = node.attrText(attrName);
        if (name != null && !StringHelper.isValidPropPath(name))
            throw new NopException(ERR_XLANG_INVALID_VAR_NAME)
                    .loc(node.attrLoc(attrName)).param(ARG_VAR_NAME, name);
        return name;
    }

    @Override
    public Expression visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkNoArgNames((XNode) filter);

        return Literal.booleanValue(filter.getLocation(), true);
    }

    @Override
    public Expression visitAlwaysFalse(ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkNoArgNames((XNode) filter);

        return Literal.booleanValue(filter.getLocation(), false);
    }

    protected Expression compileValueExpr(SourceLocation loc, Object value) {
        if (value instanceof Expression) {
            return (Expression) value;
        }

        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith("@:")) {
                Object jsonValue = JsonTool.parseNonStrict(loc, str.substring(2).trim());
                return Literal.valueOf(loc, jsonValue);
            }
            if (str.contains("${"))
                return compiler.parseTemplateExpr(loc, str, false, ExprPhase.eval, compileScope);
            return Literal.valueOf(loc, value);
        } else {
            return Literal.valueOf(loc, value);
        }
    }

    @Override
    protected Expression visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkArgNames((XNode) filter, ASSERT_OP_ARGS);

        String name = getName(filter);
        SourceLocation loc = filter.getLocation();

        Expression value = compileNameExpr(loc, name);

        return AssertOpExpression.valueOf(loc, filterOp, value);
    }

    protected Expression visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkArgNames((XNode) filter, BETWEEN_OP_ARGS);

        String name = getName(filter);
        String minName = getMinName(filter);
        String maxName = getMaxName(filter);

        SourceLocation loc = filter.getLocation();

        Expression valueExpr = compileNameExpr(loc, name);
        Expression minExpr;
        if (minName != null) {
            minExpr = compileNameExpr(loc, minName);
        } else {
            minExpr = compileValueExpr(loc, getMin(filter));
        }

        Expression maxExpr;
        if (maxName != null) {
            maxExpr = compileNameExpr(loc, maxName);
        } else {
            maxExpr = compileValueExpr(loc, getMax(filter));
        }

        boolean excludeMin = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MIN)), false);
        boolean excludeMax = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MAX)), false);
        return BetweenOpExpression.valueOf(loc, valueExpr, filterOp.name(), minExpr, maxExpr, excludeMin, excludeMax);
    }

    @Override
    public Expression visitAnd(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return Literal.booleanValue(filter.getLocation(), true);

        if (children.size() == 1) {
            return visit(children.get(0), scope);
        }

        return getNext(children, 0, scope, XLangOperator.AND);
    }

    private Expression getNext(List<? extends ITreeBean> children, int index, IVariableScope scope, XLangOperator op) {
        Expression left = visit(children.get(index), scope);
        if (index == children.size() - 1) {
            return left;
        } else {
            Expression right = getNext(children, index + 1, scope, op);
            if (left == null)
                return right;
            if (right == null)
                return left;
            return LogicalExpression.valueOf(left.getLocation(), op, left, right);
        }
    }

    @Override
    public Expression visitOr(ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkNoArgNames((XNode) filter);

        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return Literal.booleanValue(filter.getLocation(), true);

        if (children.size() == 1)
            return visit(children.get(0), scope);

        return getNext(children, 0, scope, XLangOperator.OR);
    }

    @Override
    public Expression visitNot(ITreeBean filter, IVariableScope scope) {
        XplParseHelper.checkNoArgNames((XNode) filter);
        return UnaryExpression.valueOf(filter.getLocation(), XLangOperator.NOT, visitAnd(filter, scope));
    }

    @Override
    public Expression visitUnknown(String op, ITreeBean filter, IVariableScope scope) {
        XNode node = (XNode) filter;
        return compiler.parseTag(node, compileScope);
    }
}