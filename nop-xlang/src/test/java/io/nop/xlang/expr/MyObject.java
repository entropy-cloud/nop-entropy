/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.core.lang.eval.IEvalScope;

public class MyObject {
    @EvalMethod
    public int testEval(IEvalScope scope, int value) {
        return value + 1;
    }
}
