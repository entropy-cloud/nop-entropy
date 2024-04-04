package io.nop.xlang.expr.flags;

import io.nop.api.core.util.Guard;

import java.util.Set;
import java.util.function.Predicate;

public class MatchFlagPredicate implements Predicate<Set<String>> {
    private final String flag;

    public MatchFlagPredicate(String flag) {
        this.flag = Guard.notEmpty(flag, "flag");
    }

    @Override
    public boolean test(Set<String> flags) {
        return flags.contains(flag);
    }
}
