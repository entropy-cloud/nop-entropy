/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.SourceLocation;

public abstract class AbstractConfigReference<T> implements IConfigReference<T> {
    private SourceLocation location;
    private final String name;
    private final Class<T> valueType;
    private T defaultValue;

    public AbstractConfigReference(SourceLocation location, String name, Class<T> valueType, T defaultValue) {
        this.name = name;
        this.location = location;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[name=").append(name).append(",valueType=").append(valueType);
        sb.append(",value=").append(getAssignedValue()).append(",defaultValue=").append(defaultValue);
        sb.append("]");
        return sb.toString();
    }

    public void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final Class<T> getValueType() {
        return valueType;
    }

    @Override
    public T getDefaultValue() {
        return defaultValue;
    }

}