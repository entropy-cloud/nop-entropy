/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.config.IConfigValue;

import java.util.Objects;

public class StaticValue<T> implements IConfigValue<T> {
    private static final StaticValue NULL = new StaticValue(null);

    private final T value;

    public StaticValue(T value) {
        this.value = value;
    }

    public static <T> StaticValue<T> nullValue() {
        return (StaticValue<T>) NULL;
    }

    public static <T> StaticValue<T> valueOf(T value) {
        if (value == null)
            return nullValue();
        return new StaticValue<>(value);
    }

    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof StaticValue))
            return false;

        StaticValue other = (StaticValue) o;
        return Objects.equals(value, other.value);
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public T get() {
        return value;
    }
}
