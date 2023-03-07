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

import static io.nop.match.MatchErrors.ERR_MATCH_FIELD_VALUE_NOT_NULL;

public class IsNullMatchPattern implements IMatchPattern {
    public static final IsNullMatchPattern INSTANCE = new IsNullMatchPattern();

    @Override
    public Object toJson() {
        return null;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (state.getValue() != null) {
            if (collectError) {
                state.buildError(ERR_MATCH_FIELD_VALUE_NOT_NULL).addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}
