package io.nop.xlang.expr.flags;

import io.nop.api.core.util.Guard;

import java.util.Set;
import java.util.function.Predicate;

public class MatchFlagPredicate implements Predicate<Set<String>> {
    public static final String FLAG_EMPTY = "EMPTY";
    public static final String FLAG_ALL = "ALL";

    private final String flag;

    public MatchFlagPredicate(String flag) {
        this.flag = Guard.notEmpty(flag, "flag");
    }

    @Override
    public boolean test(Set<String> flags) {
        if (flag.equals(FLAG_EMPTY) || flag.equals(FLAG_ALL))
            return flags.isEmpty();

        return flags.contains(flag);
    }
}
