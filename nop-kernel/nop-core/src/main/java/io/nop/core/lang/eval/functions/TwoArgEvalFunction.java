/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

@FunctionalInterface
public interface TwoArgEvalFunction extends IEvalFunction {
    @Override
    default Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return call2(thisObj, args[0], args[1], scope);
    }

    @Override
    Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope);
}