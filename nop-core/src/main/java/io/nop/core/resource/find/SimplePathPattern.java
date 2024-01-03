/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.find;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.Collections;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_PATH_PATTERN;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_FILE_PATH_PATTERN;

public class SimplePathPattern {
    private final List<String> components;

    private SimplePathPattern(List<String> components) {
        this.components = components;
    }

    public static SimplePathPattern of(String pattern) {
        if (pattern == null || pattern.isEmpty())
            return new SimplePathPattern(Collections.emptyList());

        if (pattern.startsWith("/"))
            pattern = pattern.substring(1);

        if (pattern.endsWith("/"))
            pattern = pattern.substring(0, pattern.length() - 1);

        List<String> paths = StringHelper.split(pattern, '/');
        for (String component : paths) {
            String s = component.replace('*', '_');
            if (!StringHelper.isValidFileName(s))
                throw new NopException(ERR_RESOURCE_INVALID_FILE_PATH_PATTERN).param(ARG_PATH_PATTERN, pattern);
        }
        return new SimplePathPattern(paths);
    }

    public int size() {
        return components.size();
    }

    public boolean matchComponent(int index, String component) {
        if (index >= components.size())
            return false;

        String s = components.get(index);
        return StringHelper.matchSimplePattern(component, s);
    }
}