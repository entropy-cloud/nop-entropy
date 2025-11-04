/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang;

import io.nop.api.core.exceptions.NopException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.nop.commons.CommonErrors.ARG_ATTR_NAME;
import static io.nop.commons.CommonErrors.ERR_NULL_ATTRIBUTE_VALUE;

public interface IAttributeSet {
    Map<String, Object> getAttributes();

    default Set<String> getAttributeNames() {
        Map<String, Object> attrs = getAttributes();
        if (attrs == null)
            return Collections.emptySet();
        return attrs.keySet();
    }

    default Object getAttribute(String name) {
        Map<String, Object> attrs = getAttributes();
        if (attrs == null)
            return null;
        return attrs.get(name);
    }

    default Object requireAttribute(String name) {
        Object value = getAttribute(name);
        if (value == null)
            throw new NopException(ERR_NULL_ATTRIBUTE_VALUE).param(ARG_ATTR_NAME, name);
        return value;
    }

    void setAttribute(String name, Object value);

    default void addAttributes(Map<String, Object> attrs) {
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                setAttribute(entry.getKey(), entry.getValue());
            }
        }
    }

    default void removeAttribute(String name) {
        Map<String, Object> attrs = getAttributes();
        if (attrs == null)
            return;
        attrs.remove(name);
    }
}