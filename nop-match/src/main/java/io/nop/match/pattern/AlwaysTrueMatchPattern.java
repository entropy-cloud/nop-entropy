package io.nop.match.pattern;

import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

public class AlwaysTrueMatchPattern implements IMatchPattern {
    public static final AlwaysTrueMatchPattern INSTANCE = new AlwaysTrueMatchPattern();

    @Override
    public Object toJson() {
        return "*";
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        return true;
    }
}