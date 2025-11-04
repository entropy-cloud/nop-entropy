/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;

public class FixedValueResolver implements IValueResolver {
    public static final FixedValueResolver NULL_RESOLVER = new FixedValueResolver(null);

    private final Object value;

    private FixedValueResolver(Object value) {
        this.value = value;
    }

    public static FixedValueResolver valueOf(Object value) {
        if (value == null)
            return NULL_RESOLVER;
        return new FixedValueResolver(value);
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        return value;
    }
}
