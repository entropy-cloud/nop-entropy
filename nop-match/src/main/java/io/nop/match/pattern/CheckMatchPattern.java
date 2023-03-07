/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.pattern;

import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.function.Predicate;

import static io.nop.match.MatchConstants.NAME_CHECK;
import static io.nop.match.MatchConstants.PATTERN_PREFIX;
import static io.nop.match.MatchErrors.ERR_MATCH_CHECK_MATCH_FAIL;

public class CheckMatchPattern implements IMatchPattern {
    private final String expr;
    private final Predicate<MatchState> predicate;

    public CheckMatchPattern(String expr, Predicate<MatchState> predicate) {
        this.expr = expr;
        this.predicate = predicate;
    }

    @Override
    public Object toJson() {
        return PATTERN_PREFIX + NAME_CHECK + ':' + expr;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (!predicate.test(state)) {
            if (collectError) {
                state.buildError(ERR_MATCH_CHECK_MATCH_FAIL).addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}
