/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJECT_BINDING_NOT_MAP;

public class ObjectBindingAssignExecutable extends AbstractExecutable {
    private final PropBinding[] propBindings;
    private final AssignIdentifier restBinding;
    private final IExecutableExpression expr;
    private final Set<String> propKeys;

    public ObjectBindingAssignExecutable(SourceLocation loc, PropBinding[] propBindings, AssignIdentifier restBinding,
                                         IExecutableExpression expr) {
        super(loc);
        this.propBindings = propBindings;
        this.restBinding = restBinding;
        this.expr = expr;
        this.propKeys = getPropKeys();
    }

    private Set<String> getPropKeys() {
        Set<String> keys = new HashSet<>();
        for (PropBinding propBinding : propBindings) {
            keys.add(propBinding.getKey());
        }
        return keys;
    }

    // tell cpd to start ignoring code - CPD-OFF
    @Override
    public void display(StringBuilder sb) {
        sb.append("let ");
        sb.append('{');
        for (PropBinding propBinding : propBindings) {
            sb.append(propBinding.getKey());
            if (Objects.equals(propBinding.getKey(), propBinding.getVarName())) {
                sb.append(':').append(propBinding.getKey());
            }
            sb.append(',');
        }
        if (restBinding == null) {
            sb.deleteCharAt(sb.length() - 1);
        } else {
            sb.append("...").append(restBinding.getVarName());
        }
        sb.append(']');
        sb.append(" = ");
        expr.display(sb);
        sb.append(';');
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }
    // resume CPD analysis - CPD-ON

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(expr, rt);
        if (!(value instanceof Map)) {
            throw newError(ERR_EXEC_OBJECT_BINDING_NOT_MAP).param(ARG_VALUE, value);
        }

        Map<String, Object> map = (Map<String, Object>) value;
        for (int i = 0, n = propBindings.length; i < n; i++) {
            PropBinding binding = propBindings[i];
            Object propValue = map.get(binding.getKey());
            if (propValue == null)
                propValue = binding.getDefaultValue(executor, rt);
            binding.assign(propValue, rt);
        }
        if (restBinding != null) {
            Map<String, Object> tail = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (propKeys.contains(entry.getKey()))
                    continue;
                tail.put(entry.getKey(), value);
            }
            restBinding.assign(tail, rt);
        }
        return null;
    }
}
