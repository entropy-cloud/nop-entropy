/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertySetter;

public class FunctionPropertySetter implements IPropertySetter {
    private final IEvalFunction method;

    public FunctionPropertySetter(IEvalFunction method) {
        this.method = method;
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        method.call2(obj, propName, value, scope);
    }
}
