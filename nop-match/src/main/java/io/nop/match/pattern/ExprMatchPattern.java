package io.nop.match.pattern;

import io.nop.commons.functional.IEqualsChecker;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import static io.nop.match.MatchConstants.NAME_EXPR;
import static io.nop.match.MatchConstants.PATTERN_PREFIX;
import static io.nop.match.MatchErrors.ARG_EXPECTED;
import static io.nop.match.MatchErrors.ERR_MATCH_EXPR_MATCH_FAIL;

public class ExprMatchPattern implements IMatchPattern {
    private final IEqualsChecker equalsChecker;
    private final IEvalAction expr;
    private final String exprString;

    public ExprMatchPattern(IEqualsChecker equalsChecker, String exprString, IEvalAction expr) {
        this.equalsChecker = equalsChecker;
        this.exprString = exprString;
        this.expr = expr;
    }

    @Override
    public Object toJson() {
        return PATTERN_PREFIX + NAME_EXPR + ':' + exprString;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        Object value = expr.invoke(state.getScope());
        if (!equalsChecker.isEquals(value, state.getValue())) {
            if (collectError) {
                state.buildError(ERR_MATCH_EXPR_MATCH_FAIL)
                        .param(ARG_EXPECTED, value)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}
