/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.DisabledEvalScope;

public class ValueResolveOptions {
    /**
     * 忽略所有null值
     */
    private boolean ignoreNull = true;

    private IEvalContext scope = DisabledEvalScope.INSTANCE;

    public IEvalContext getScope() {
        return scope;
    }

    public void setScope(IEvalContext scope) {
        this.scope = scope;
    }

    public boolean isIgnoreNull() {
        return ignoreNull;
    }

    public void setIgnoreNull(boolean ignoreNull) {
        this.ignoreNull = ignoreNull;
    }
}