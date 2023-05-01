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
