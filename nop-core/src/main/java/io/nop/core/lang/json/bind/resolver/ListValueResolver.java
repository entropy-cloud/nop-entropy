/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.commons.lang.Undefined;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;

import java.util.ArrayList;
import java.util.List;

public class ListValueResolver implements IValueResolver {
    private final boolean ignoreNull;
    private final List<IValueResolver> items;

    public ListValueResolver(boolean ignoreNull, List<IValueResolver> items) {
        this.ignoreNull = ignoreNull;
        this.items = items;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        List<Object> ret = new ArrayList<>(items.size());
        for (IValueResolver item : items) {
            Object value = item.resolveValue(ctx);
            if (value == Undefined.undefined)
                continue;

            if (value == null) {
                if (ignoreNull)
                    continue;
                ret.add(null);
            } else {
                ret.add(value);
            }
        }
        return ret;
    }
}
