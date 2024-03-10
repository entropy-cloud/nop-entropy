/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.core.type.IGenericType;

import java.io.Serializable;

public class BeanInjectInfo implements Serializable {
    private static final long serialVersionUID = 2674636328093999252L;

    private final String ref;
    private final IGenericType type;

    private final String value;
    private final boolean optional;

    public BeanInjectInfo(String ref, IGenericType type, String value, boolean optional) {
        this.ref = ref;
        this.type = type;
        this.value = value;
        this.optional = optional;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ref != null) {
            sb.append("byName:").append(ref);
        } else if (type != null) {
            sb.append("byType:").append(type.getTypeName());
        }
        if (optional)
            sb.append('?');
        return sb.toString();
    }

    public String getValue() {
        return value;
    }

    public String getRef() {
        return ref;
    }

    public IGenericType getType() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }
}