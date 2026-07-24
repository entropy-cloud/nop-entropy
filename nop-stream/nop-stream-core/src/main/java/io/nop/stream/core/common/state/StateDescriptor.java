/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;

import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.common.typeutils.JsonToolSerializer;
import io.nop.stream.core.common.typeutils.TypeSerializer;

public class StateDescriptor<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final Class<T> valueType;
    private final T defaultValue;

    private TypeSerializer<?> serializer;

    public StateDescriptor(String name, Class<T> valueType) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = null;
        this.serializer = new JsonToolSerializer<>();
    }

    public StateDescriptor(String name, Class<T> valueType, T defaultValue) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.serializer = new JsonToolSerializer<>();
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

    @SuppressWarnings("unchecked")
    public <S> TypeSerializer<S> getSerializer() {
        return (TypeSerializer<S>) serializer;
    }

    public void setSerializer(TypeSerializer<?> serializer) {
        this.serializer = serializer;
    }
}
