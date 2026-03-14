/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;

public class StateDescriptor<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final Class<T> valueType;
    private final T defaultValue;

    public StateDescriptor(String name, Class<T> valueType) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = null;
    }

    public StateDescriptor(String name, Class<T> valueType, T defaultValue) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueType() {
        return valueType;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}