/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.pattern;

import io.nop.core.model.query.IBetweenOperator;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.match.MatchConstants.KEY_PREFIX;
import static io.nop.match.MatchErrors.ARG_EXCLUDE_MAX;
import static io.nop.match.MatchErrors.ARG_EXCLUDE_MIN;
import static io.nop.match.MatchErrors.ARG_FILTER_OP;
import static io.nop.match.MatchErrors.ARG_MAX;
import static io.nop.match.MatchErrors.ARG_MIN;
import static io.nop.match.MatchErrors.ERR_MATCH_BETWEEN_CHECK_FAIL;

public class BetweenMatchPattern implements IMatchPattern {
    private final String filterOp;
    private final IBetweenOperator operator;
    private final Object minValue;
    private final Object maxValue;
    private final boolean excludeMin;
    private final boolean excludeMax;

    public BetweenMatchPattern(String filterOp, IBetweenOperator operator, Object minValue, Object maxValue,
                               boolean excludeMin, boolean excludeMax) {
        this.filterOp = filterOp;
        this.operator = operator;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.excludeMin = excludeMin;
        this.excludeMax = excludeMax;
    }

    @Override
    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(KEY_PREFIX, filterOp);
        ret.put("min", minValue);
        ret.put("max", maxValue);
        ret.put("excludeMin", excludeMin);
        ret.put("excludeMax", excludeMax);
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (!operator.test(state.getValue(), minValue, maxValue, excludeMin, excludeMax)) {
            if (collectError) {
                state.buildError(ERR_MATCH_BETWEEN_CHECK_FAIL).param(ARG_FILTER_OP, filterOp).param(ARG_MIN, minValue)
                        .param(ARG_MAX, maxValue).param(ARG_EXCLUDE_MIN, excludeMin).param(ARG_EXCLUDE_MAX, excludeMax);
            }
            return false;
        }
        return true;
    }
}