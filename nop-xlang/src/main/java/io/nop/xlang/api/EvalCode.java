/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.source.IWithSourceCode;

public class EvalCode extends ExprEvalAction implements ISourceLocationGetter, IJsonString, IWithSourceCode {
    private final String code;

    public EvalCode(IExecutableExpression action, String code) {
        super(action);
        this.code = code;
    }

    public static AbstractEvalAction addSource(ExprEvalAction action, String code) {
        if (action instanceof IWithSourceCode)
            return action;
        return new EvalCode(action.getExpr(), code);
    }

    @Override
    public String getSource() {
        return code;
    }

    public String toString() {
        return code;
    }

    public String getCode() {
        return code;
    }
}