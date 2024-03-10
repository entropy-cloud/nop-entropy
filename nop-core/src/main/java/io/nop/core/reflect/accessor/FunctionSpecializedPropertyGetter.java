/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

public class FunctionSpecializedPropertyGetter implements ISpecializedPropertyGetter {
    private final IEvalFunction func;

    public FunctionSpecializedPropertyGetter(IEvalFunction func) {
        this.func = func;
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        return func.call0(obj, scope);
    }
}