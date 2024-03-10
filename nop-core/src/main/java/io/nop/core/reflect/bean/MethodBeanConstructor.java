/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IFunctionModel;

public class MethodBeanConstructor implements IBeanConstructor, IBeanConstructorEx {
    private final IFunctionModel function;

    public MethodBeanConstructor(IFunctionModel function) {
        this.function = function;
    }

    @Override
    public Object newInstance() {
        return function.call0(null, DisabledEvalScope.INSTANCE);
    }

    @Override
    public Object newInstance(Object[] args) {
        return function.invoke(null, args, DisabledEvalScope.INSTANCE);
    }
}