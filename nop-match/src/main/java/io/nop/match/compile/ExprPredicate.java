package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.MatchState;

import java.util.function.Predicate;

public class ExprPredicate implements Predicate<MatchState> {
    private final IEvalAction expr;

    public ExprPredicate(IEvalAction expr) {
        this.expr = expr;
    }

    @Override
    public boolean test(MatchState matchState) {
        return ConvertHelper.toTruthy(expr.invoke(matchState.getScope()));
    }
}
