/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang.impl;

import io.nop.commons.lang.IAttributeSet;

import java.util.HashMap;
import java.util.Map;

public class AttributeSetImpl implements IAttributeSet {
    private Map<String, Object> attributes;

    public AttributeSetImpl(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public AttributeSetImpl() {
        this(new HashMap<>());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, value);
    }
}
