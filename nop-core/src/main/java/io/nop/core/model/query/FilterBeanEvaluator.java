/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;

import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN;
import static io.nop.api.core.convert.ConvertHelper.defaults;
import static io.nop.api.core.convert.ConvertHelper.toBoolean;

/**
 * 执行复杂过滤条件
 */
public class FilterBeanEvaluator extends FilterBeanVisitor<Boolean> {
    public static final FilterBeanEvaluator INSTANCE = new FilterBeanEvaluator();

    @Override
    protected Boolean visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        Object leftValue = getValue(scope, name);
        String valueName = getValueName(filter);
        Object rightValue;
        if (valueName != null) {
            rightValue = getValue(scope, valueName);
        } else {
            rightValue = getValue(filter);
        }
        return filterOp.getBiPredicate().test(leftValue, rightValue);
    }

    protected Object getValue(IVariableScope scope, String name){
        return scope.getValueByPropPath(name);
    }

    @Override
    protected Boolean visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        Object value = getValue(scope, name);
        return filterOp.getPredicate().test(value);
    }

    protected Boolean visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        Object value = getValue(scope, name);
        String minName = getMinName(filter);
        String maxName = getMaxName(filter);
        Object min;
        if (minName != null) {
            min = getValue(scope, minName);
        } else {
            min = getMin(filter);
        }

        Object max;
        if (maxName != null) {
            max = getValue(scope, maxName);
        } else {
            max = getMax(filter);
        }

        boolean excludeMin = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MIN)), false);
        boolean excludeMax = defaults(toBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MAX)), false);
        return filterOp.getBetweenOperator().test(value, min, max, excludeMin, excludeMax);
    }

    @Override
    public Boolean visitAnd(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return true;

        for (ITreeBean child : children) {
            if (!visit(child, scope))
                return false;
        }
        return true;
    }

    @Override
    public Boolean visitOr(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty())
            return true;

        for (ITreeBean child : children) {
            if (visit(child, scope))
                return true;
        }
        return false;
    }

    @Override
    public Boolean visitNot(ITreeBean filter, IVariableScope scope) {
        return !visitAnd(filter, scope);
    }
}