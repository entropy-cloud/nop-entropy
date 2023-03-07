/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.MatchState;

import java.util.function.Predicate;

public class ExprPredicate implements Predicate<MatchState> {
    private final IEvalAction expr;

    public ExprPredicate(IEvalAction expr) {
        this.expr = expr;
    }

    @Override
    public boolean test(MatchState matchState) {
        return ConvertHelper.toTruthy(expr.invoke(matchState.getScope()));
    }
}
