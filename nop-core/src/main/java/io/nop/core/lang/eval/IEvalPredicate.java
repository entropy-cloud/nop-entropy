/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.predicate.AndEvalPredicate;
import io.nop.core.lang.eval.predicate.NotEvalPredicate;
import io.nop.core.lang.eval.predicate.OrEvalPredicate;

@FunctionalInterface
public interface IEvalPredicate {
    IEvalPredicate ALWAYS_TRUE = ctx -> true;
    IEvalPredicate ALWAYS_FALSE = ctx -> false;

    boolean passConditions(IEvalContext ctx);

    default IEvalPredicate not() {
        if (ALWAYS_TRUE == this)
            return ALWAYS_FALSE;
        if (ALWAYS_FALSE == this)
            return ALWAYS_TRUE;
        return new NotEvalPredicate(this);
    }

    default IEvalPredicate and(IEvalPredicate test) {
        if (this == ALWAYS_TRUE)
            return test;

        if (this == ALWAYS_FALSE || test == ALWAYS_TRUE)
            return this;

        return new AndEvalPredicate(this, test);
    }

    default IEvalPredicate or(IEvalPredicate test) {
        if (this == ALWAYS_TRUE || test == ALWAYS_FALSE)
            return this;

        if (this == ALWAYS_FALSE)
            return test;

        return new OrEvalPredicate(this, test);
    }
}