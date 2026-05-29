/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.model.builder;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;

import java.io.Serializable;

public class EvalFunctionCondition extends IterativeCondition<Object> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction action;

    public EvalFunctionCondition(IEvalFunction action) {
        this.action = action;
    }

    @Override
    public boolean filter(Object value, Context ctx) {
        return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));
    }
}
