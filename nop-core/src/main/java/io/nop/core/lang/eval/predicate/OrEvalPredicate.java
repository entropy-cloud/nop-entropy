/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.predicate;

import io.nop.commons.util.ArrayHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

public class OrEvalPredicate implements IEvalPredicate {
    private final IEvalPredicate[] predicates;

    public OrEvalPredicate(IEvalPredicate... predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        for (IEvalPredicate predicate : predicates) {
            if (predicate.passConditions(ctx))
                return true;
        }
        return false;
    }

    @Override
    public IEvalPredicate or(IEvalPredicate test) {
        if (test == ALWAYS_FALSE)
            return this;

        return new OrEvalPredicate(ArrayHelper.append(predicates, test, IEvalPredicate.class));
    }
}
