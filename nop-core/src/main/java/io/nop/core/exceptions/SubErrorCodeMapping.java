/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.exceptions;

import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.Objects;

public class SubErrorCodeMapping {
    /**
     * 对错误参数的过滤条件。参数名--> 参数值
     */
    private final Map<String, String> filter;

    private final ErrorCodeMapping mapping;

    public SubErrorCodeMapping(Map<String, String> filter, ErrorCodeMapping mapping) {
        this.filter = filter;
        this.mapping = mapping;
    }

    public Map<String, String> getFilter() {
        return filter;
    }

    public ErrorCodeMapping getMapping() {
        return mapping;
    }

    public boolean isSameFilter(SubErrorCodeMapping mapping) {
        return filter.equals(mapping.filter);
    }

    public boolean matchParams(Map<String, ?> params) {
        for (Map.Entry<String, String> entry : filter.entrySet()) {
            Object value = params.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), StringHelper.toString(value, "")))
                return false;
        }
        return true;
    }
}
