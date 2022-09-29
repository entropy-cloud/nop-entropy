package io.nop.match;

public interface IMatchPattern {
    Object toJson();

    boolean matchValue(MatchState state, boolean collectError);
}
