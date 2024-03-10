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

public class NoopEvalFunction implements IEvalFunction {
    public static final NoopEvalFunction INSTANCE = new NoopEvalFunction();

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return null;
    }
}
