/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.IVariableScope;

import java.util.List;

@DataBean
public class SwitchValue implements IConfigValue<Object> {
    private TreeBean when;
    private Object value;

    private List<SwitchValue> cases;

    public Object eval(FilterBeanVisitor<Boolean> evaluator, IVariableScope scope) {
        if (evalWhen(evaluator, scope)) {
            return getValue(evaluator, scope);
        }
        return null;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public Object get() {
        return eval(FilterBeanEvaluator.INSTANCE, ContextVariableScope.INSTANCE);
    }

    private boolean evalWhen(FilterBeanVisitor<Boolean> evaluator, IVariableScope scope) {
        if (when == null)
            return true;
        return Boolean.TRUE.equals(evaluator.visit(when, scope));
    }

    private Object getValue(FilterBeanVisitor<Boolean> evaluator, IVariableScope scope) {
        if (cases == null || cases.isEmpty())
            return value;

        for (SwitchValue child : cases) {
            if (child.evalWhen(evaluator, scope)) {
                return child.getValue(evaluator, scope);
            }
        }
        return null;
    }

    /**
     * 强制转型到指定类型
     */
    public void normalizeValue(Class<?> valueType) {
        if (value != null) {
            value = ConvertHelper.convertTo(valueType, value, NopEvalException::new);
        }
        if (cases != null) {
            for (SwitchValue child : cases) {
                child.normalizeValue(valueType);
            }
        }
    }

    public TreeBean getWhen() {
        return when;
    }

    public void setWhen(TreeBean when) {
        this.when = when;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public List<SwitchValue> getCases() {
        return cases;
    }

    public void setCases(List<SwitchValue> cases) {
        this.cases = cases;
    }
}