package io.nop.match.pattern;

import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static io.nop.match.MatchConstants.KEY_PREFIX;
import static io.nop.match.MatchConstants.NAME_IF;

public class IfMatchPattern implements IMatchPattern {
    private final String testExpr;
    private final Predicate<MatchState> test;
    private final IMatchPattern truePattern;
    private final IMatchPattern falsePattern;

    public IfMatchPattern(String testExpr,
                          Predicate<MatchState> test,
                          IMatchPattern truePattern,
                          IMatchPattern falsePattern) {
        this.testExpr = testExpr;
        this.test = test;
        this.truePattern = truePattern;
        this.falsePattern = falsePattern;
    }

    @Override
    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(KEY_PREFIX, NAME_IF);
        ret.put("testExpr", testExpr);
        ret.put("true", truePattern.toJson());
        if (falsePattern != null)
            ret.put("false", falsePattern.toJson());
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (test.test(state)) {
            return truePattern.matchValue(state, collectError);
        } else {
            if (falsePattern == null)
                return false;
            return falsePattern.matchValue(state, collectError);
        }
    }
}
