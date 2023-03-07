/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

@FunctionalInterface
public interface OneArgEvalFunction extends IEvalFunction {
    @Override
    default Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return call1(thisObj, args[0], scope);
    }

    @Override
    Object call1(Object thisObj, Object arg, IEvalScope scope);
}