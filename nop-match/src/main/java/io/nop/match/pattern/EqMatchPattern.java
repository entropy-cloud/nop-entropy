package io.nop.match.pattern;

import io.nop.commons.functional.IEqualsChecker;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import static io.nop.match.MatchErrors.ARG_EXPECTED;
import static io.nop.match.MatchErrors.ERR_MATCH_FIELD_VALUE_NOT_EXPECTED;

public class EqMatchPattern implements IMatchPattern {
    private final IEqualsChecker<Object> equalsChecker;
    private final Object expected;

    public EqMatchPattern(IEqualsChecker<Object> equalsChecker, Object expected) {
        this.equalsChecker = equalsChecker;
        this.expected = expected;
    }

    @Override
    public Object toJson() {
        return expected;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        Object value = state.getValue();
        if (!equalsChecker.isEquals(value, expected)) {
            if (collectError) {
                state.buildError(ERR_MATCH_FIELD_VALUE_NOT_EXPECTED)
                        .param(ARG_EXPECTED, expected)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}