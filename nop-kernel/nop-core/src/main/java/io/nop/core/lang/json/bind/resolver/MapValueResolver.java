/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.commons.lang.Undefined;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.json.bind.IValueResolver;

import java.util.Map;

public class MapValueResolver implements IValueResolver {
    private final boolean ignoreNull;
    private final Map<String, IValueResolver> props;

    public MapValueResolver(boolean ignoreNull, Map<String, IValueResolver> props) {
        this.ignoreNull = ignoreNull;
        this.props = props;
    }

    @Override
    public Object resolveValue(IEvalContext ctx) {
        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(props.size());

        for (Map.Entry<String, IValueResolver> entry : props.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue().resolveValue(ctx);
            if (value == Undefined.undefined) {
                continue;
            }

            if (value == null) {
                if (ignoreNull)
                    continue;
                ret.put(name, null);
            } else {
                if (name.startsWith("...") && value instanceof Map<?, ?>) {
                    ret.putAll((Map<String, Object>) value);
                } else {
                    ret.put(name, value);
                }
            }
        }
        return ret;
    }
}
