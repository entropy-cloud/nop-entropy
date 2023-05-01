package io.nop.core.lang.eval.predicate;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

public class NotEvalPredicate implements IEvalPredicate {
    private final IEvalPredicate predicate;

    public NotEvalPredicate(IEvalPredicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        return !predicate.passConditions(ctx);
    }

    @Override
    public IEvalPredicate not() {
        return predicate;
    }
}
