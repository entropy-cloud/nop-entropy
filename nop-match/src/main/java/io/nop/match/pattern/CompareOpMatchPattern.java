package io.nop.match.pattern;

import io.nop.core.lang.json.JsonTool;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.function.BiPredicate;

import static io.nop.match.MatchConstants.PATTERN_PREFIX;
import static io.nop.match.MatchErrors.ARG_FILTER_OP;
import static io.nop.match.MatchErrors.ARG_PATTERN;
import static io.nop.match.MatchErrors.ERR_MATCH_COMPARE_OP_MATCH_FAIL;

public class CompareOpMatchPattern implements IMatchPattern {
    private final String filterOp;
    private final BiPredicate<Object, Object> predicate;
    private final Object pattern;

    public CompareOpMatchPattern(String filterOp,
                                 BiPredicate<Object, Object> predicate, Object pattern) {
        this.filterOp = filterOp;
        this.predicate = predicate;
        this.pattern = pattern;
    }

    @Override
    public Object toJson() {
        return PATTERN_PREFIX + filterOp + ':' + JsonTool.stringify(pattern);
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (!predicate.test(state.getValue(), pattern)) {
            if (collectError) {
                state.buildError(ERR_MATCH_COMPARE_OP_MATCH_FAIL)
                        .param(ARG_FILTER_OP, filterOp)
                        .param(ARG_PATTERN, pattern)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}