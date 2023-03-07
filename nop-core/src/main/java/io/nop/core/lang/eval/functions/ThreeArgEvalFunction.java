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
public interface ThreeArgEvalFunction extends IEvalFunction {
    @Override
    default Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return call3(thisObj, args[0], args[1], args[2], scope);
    }

    @Override
    Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope);
}