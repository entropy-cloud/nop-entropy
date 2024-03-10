/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.pattern;

import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.function.Predicate;

import static io.nop.match.MatchConstants.PATTERN_PREFIX;
import static io.nop.match.MatchErrors.ARG_FILTER_OP;
import static io.nop.match.MatchErrors.ERR_MATCH_ASSERT_OP_MATCH_FAIL;

public class AssertOpMatchPattern implements IMatchPattern {
    private final String filterOp;
    private final Predicate<Object> predicate;

    public AssertOpMatchPattern(String filterOp, Predicate<Object> predicate) {
        this.filterOp = filterOp;
        this.predicate = predicate;
    }

    @Override
    public Object toJson() {
        return PATTERN_PREFIX + filterOp;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (!predicate.test(state.getValue())) {
            if (collectError) {
                state.buildError(ERR_MATCH_ASSERT_OP_MATCH_FAIL).param(ARG_FILTER_OP, filterOp)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}