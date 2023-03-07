/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.pattern;

import io.nop.commons.functional.IEqualsChecker;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import static io.nop.match.MatchConstants.NAME_VAR;
import static io.nop.match.MatchErrors.ARG_VAR_NAME;
import static io.nop.match.MatchErrors.ARG_VAR_VALUE;
import static io.nop.match.MatchErrors.ERR_MATCH_NOT_EQUALS_VAR_VALUE;

public class VarMatchPattern implements IMatchPattern {
    private final IEqualsChecker<Object> equalsChecker;
    private final String varName;

    public VarMatchPattern(IEqualsChecker<Object> equalsChecker, String varName) {
        this.equalsChecker = equalsChecker;
        this.varName = varName;
    }

    public String toJson() {
        return '@' + NAME_VAR + ':' + varName;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        Object value = state.getScope().getEvalScope().getValue(varName);
        boolean b = equalsChecker.isEquals(value, state.getValue());
        if (!b) {
            if (collectError) {
                state.buildError(ERR_MATCH_NOT_EQUALS_VAR_VALUE).param(ARG_VAR_NAME, varName)
                        .param(ARG_VAR_VALUE, value).addToCollector(state.getErrorCollector());
            }
            return false;
        }
        return true;
    }
}
