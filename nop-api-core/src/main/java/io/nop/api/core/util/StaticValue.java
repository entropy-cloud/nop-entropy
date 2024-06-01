/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.util.Objects;

import static io.nop.api.core.ApiErrors.ARG_VAR;
import static io.nop.api.core.ApiErrors.ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL;

public final class StaticValue<T> implements IConfigValue<T> {
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

    public static <T> StaticValue<T> build(String varName, Class<T> targetType, Object value) {
        return valueOf(castValue(varName, targetType, value));
    }

    public <R> StaticValue<R> cast(String varName, Class<R> targetType) {
        if (value == null || targetType.isInstance(value))
            return (StaticValue<R>) this;
        return build(varName, targetType, value);
    }

    public static <R> R castValue(String varName, Class<R> targetType, Object value) {
        return ConvertHelper.convertConfigTo(targetType, value,
                err -> new NopException(ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL)
                        .param(ARG_VAR, varName));
    }

    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof StaticValue<?>))
            return false;

        StaticValue<?> other = (StaticValue<?>) o;
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
