/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api.source;

import io.nop.api.core.json.IJsonString;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalPredicate;

public class SourceEvalPredicate implements IEvalPredicate, IJsonString, IWithSourceCode {
    private final String source;
    private final IEvalPredicate action;

    public SourceEvalPredicate(String source, IEvalPredicate action) {
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
    public boolean passConditions(IEvalContext ctx) {
        return action.passConditions(ctx);
    }
}

