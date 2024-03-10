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
public interface NoArgEvalFunction extends IEvalFunction {
    @Override
    default Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return call0(thisObj, scope);
    }

    @Override
    Object call0(Object thisObj, IEvalScope scope);
}