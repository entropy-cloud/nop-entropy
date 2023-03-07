/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.api;

import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;

public class EvalCode extends AbstractEvalAction implements ISourceLocationGetter, IJsonString {
    private final ExprEvalAction action;
    private final String code;

    public EvalCode(ExprEvalAction action, String code) {
        this.action = action;
        this.code = code;
    }

    public String toString() {
        return code;
    }

    @Override
    public SourceLocation getLocation() {
        return action.getLocation();
    }

    public ExprEvalAction getAction() {
        return action;
    }

    public String getCode() {
        return code;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return action.invoke(ctx);
    }
}