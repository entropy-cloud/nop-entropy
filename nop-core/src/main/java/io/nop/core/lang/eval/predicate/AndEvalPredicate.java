/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval.predicate;

import io.nop.commons.util.ArrayHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

public class AndEvalPredicate implements IEvalPredicate {
    private final IEvalPredicate[] predicates;

    public AndEvalPredicate(IEvalPredicate... predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        for (IEvalPredicate predicate : predicates) {
            if (!predicate.passConditions(ctx))
                return false;
        }
        return true;
    }

    @Override
    public IEvalPredicate and(IEvalPredicate test) {
        if (test == ALWAYS_TRUE)
            return this;

        return new AndEvalPredicate(ArrayHelper.append(predicates, test, IEvalPredicate.class));
    }
}