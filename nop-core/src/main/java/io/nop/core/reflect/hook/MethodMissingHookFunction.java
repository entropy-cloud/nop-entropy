/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.hook;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

public class MethodMissingHookFunction implements IEvalFunction {
    private final String methodName;

    public MethodMissingHookFunction(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        IMethodMissingHook hook = (IMethodMissingHook) thisObj;
        return hook.method_invoke(methodName, args, scope);
    }
}
