/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import io.nop.api.core.util.SourceLocation;

public class OverrideConfigReference<T> implements IConfigReference<T> {
    private final IConfigReference<T> defaultRef;
    private final IConfigReference<T> ref;

    public OverrideConfigReference(IConfigReference<T> ref, IConfigReference<T> defaultRef) {
        this.ref = ref;
        this.defaultRef = defaultRef;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[name=").append(ref.getName()).append(",valueType=").append(getValueType());
        sb.append(",defaultRef=").append(defaultRef.getName());
        sb.append("]");
        return sb.toString();
    }

    IConfigReference<T> getImpl() {
        return ref;
    }

    @Override
    public IConfigValue<T> getProvider() {
        return this;
    }

    @Override
    public boolean isDynamic() {
        return ref.isDynamic() || defaultRef.isDynamic();
    }

    public SourceLocation getLocation() {
        T value = getAssignedValue();
        if (value != null)
            return getImpl().getLocation();
        return defaultRef.getLocation();
    }

    @Override
    public String getName() {
        return ref.getName();
    }

    @Override
    public Class<T> getValueType() {
        return ref.getValueType();
    }

    @Override
    public T getDefaultValue() {
        return defaultRef.get();
    }

    @Override
    public T getAssignedValue() {
        return getImpl().getAssignedValue();
    }
}