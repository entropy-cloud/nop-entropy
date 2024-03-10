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

import static io.nop.core.model.query.FilterOp.ALWAYS_FALSE;
import static io.nop.match.MatchErrors.ARG_FILTER_OP;
import static io.nop.match.MatchErrors.ERR_MATCH_ASSERT_OP_MATCH_FAIL;

public class AlwaysFalseMatchPattern implements IMatchPattern {
    public static final AlwaysFalseMatchPattern INSTANCE = new AlwaysFalseMatchPattern();

    @Override
    public Object toJson() {
        return "@" + ALWAYS_FALSE.name();
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (collectError) {
            state.buildError(ERR_MATCH_ASSERT_OP_MATCH_FAIL).param(ARG_FILTER_OP, ALWAYS_FALSE.name());
        }
        return false;
    }
}
