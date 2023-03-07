/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.reflection.IServiceActionArgBuilder;

import java.util.Map;

public class EvalServiceAction implements IServiceAction {
    private final IEvalAction action;
    private final Map<String, IServiceActionArgBuilder> argBuilders;

    public EvalServiceAction(IEvalAction action, Map<String, IServiceActionArgBuilder> argBuilders) {
        this.action = action;
        this.argBuilders = argBuilders;
    }

    @Override
    public Object invoke(Object request, FieldSelectionBean selection, IServiceContext context) {
        IEvalScope scope = context.getEvalScope().newChildScope();
        for (Map.Entry<String, IServiceActionArgBuilder> entry : argBuilders.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue().build(request, selection, context);
            scope.setLocalValue(null, name, value);
        }
        return action.invoke(scope);
    }
}
