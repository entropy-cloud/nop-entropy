/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

public class EnumValueOfMethodInvoker implements IEvalFunction {
    private final Class<? extends Enum> enumClass;

    public EnumValueOfMethodInvoker(Class<? extends Enum> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return Enum.valueOf(enumClass, (String) arg);
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return call1(thisObj, args[0], scope);
    }
}
