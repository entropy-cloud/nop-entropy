/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.MatchState;

import java.util.function.Function;

public class ExprStringFunction implements Function<MatchState, String> {
    private final IEvalAction expr;

    public ExprStringFunction(IEvalAction expr) {
        this.expr = expr;
    }

    @Override
    public String apply(MatchState matchState) {
        return ConvertHelper.toString(expr.invoke(matchState.getScope()));
    }
}
