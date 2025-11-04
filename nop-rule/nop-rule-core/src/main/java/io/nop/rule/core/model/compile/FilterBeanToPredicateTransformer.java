/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model.compile;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.predicate.AndEvalPredicate;
import io.nop.core.lang.eval.predicate.OrEvalPredicate;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.LiteralExecutable;

import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN;
import static io.nop.api.core.convert.ConvertHelper.defaults;
import static io.nop.api.core.convert.ConvertHelper.toBoolean;

public class FilterBeanToPredicateTransformer extends FilterBeanVisitor<IEvalPredicate> {
    private final XLangCompileTool compileTool;

    public FilterBeanToPredicateTransformer(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    @Override
    protected IEvalPredicate visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        String valueName = getValueName(filter);
        Object rightValue = getValue(filter);

        SourceLocation loc = filter.getLocation();

        IExecutableExpression left = compileNameExpr(loc, name);
        IExecutableExpression right;
        if (valueName != null) {
            right = compileNameExpr(loc, valueName);
        } else {
            right = compileValueExpr(loc, rightValue);
        }

        return new CompareOpExecutable(loc, filterOp, left, right);
    }

    @Override
    public IEvalPredicate visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        return IEvalPredicate.ALWAYS_TRUE;
    }

    @Override
    public IEvalPredicate visitAlwaysFalse(ITreeBean filter, IVariableScope scope) {
        return IEvalPredicate.ALWAYS_FALSE;
    }

    protected IExecutableExpression compileNameExpr(SourceLocation loc, String name) {
        Expression expr = XLangASTBuilder.buildPropExpr(loc, name);
        return compileTool.buildExecutable(expr);
    }

    protected IExecutableExpression compileValueExpr(SourceLocation loc, Object value) {
        if (value instanceof IExecutableExpression)
            return ((IExecutableExpression) value);

        if (value instanceof Expression) {
            return compileTool.buildExecutable((Expression) value);
        }

        return LiteralExecutable.build(loc, value);
    }

    @Override
    protected IEvalPredicate visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        SourceLocation loc = filter.getLocation();

        IExecutableExpression value = compileNameExpr(loc, name);

        return new AssertOpExecutable(loc, filterOp, value);
    }

    protected IEvalPredicate visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        String minName = getMinName(filter);
        String maxName = getMaxName(filter);

        SourceLocation loc = filter.getLocation();

        IExecutableExpression valueExpr = compileNameExpr(loc, name);
        IExecutableExpression minExpr;
        if (minName != null) {
            minExpr = compileNameExpr(loc, minName);
        } else {
            minExpr = compileValueExpr(loc, getMin(filter));
        }

        IExecutableExpression maxExpr;
        if (maxName != null) {
            maxExpr = compileNameExpr(loc, maxName);
        } else {
            maxExpr = compileValueExpr(loc, getMax(filter));
        }

        boolean excludeMin = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MIN)), false);
        boolean excludeMax = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MAX)), false);
        return new BetweenOpExecutable(loc, filterOp, valueExpr, minExpr, maxExpr, excludeMin, excludeMax);
    }

    @Override
    public IEvalPredicate visitAnd(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return IEvalPredicate.ALWAYS_TRUE;

        if (children.size() == 1) {
            return visit(children.get(0), scope);
        }

        IEvalPredicate[] predicates = new IEvalPredicate[children.size()];
        for (int i = 0, n = children.size(); i < n; i++) {
            predicates[i] = visit(children.get(i), scope);
        }
        return new AndEvalPredicate(predicates);
    }

    @Override
    public IEvalPredicate visitOr(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return IEvalPredicate.ALWAYS_TRUE;

        if (children.size() == 1)
            return visit(children.get(0), scope);

        IEvalPredicate[] predicates = new IEvalPredicate[children.size()];
        for (int i = 0, n = children.size(); i < n; i++) {
            predicates[i] = visit(children.get(i), scope);
        }
        return new OrEvalPredicate(predicates);
    }

    @Override
    public IEvalPredicate visitNot(ITreeBean filter, IVariableScope scope) {
        return visitAnd(filter, scope).not();
    }
}