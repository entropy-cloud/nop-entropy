/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.source;

import io.nop.api.core.json.IJsonString;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;

/**
 * 记录action所对应的源码
 */
public class SourceEvalAction implements IEvalAction, IJsonString, IWithSourceCode {
    private final String source;
    private final IEvalAction action;

    public SourceEvalAction(String source, IEvalAction action) {
        this.source = source;
        this.action = action;
    }

    public String getSource() {
        return source;
    }

    public String toString() {
        return source;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return action.invoke(ctx);
    }
}