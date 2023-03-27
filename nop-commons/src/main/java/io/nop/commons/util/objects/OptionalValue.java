/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.objects;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.lang.Undefined;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 与java的Optional对象不同，OptionalValue允许null值。
 */
@ImmutableBean
public class OptionalValue implements Serializable {
    public static final OptionalValue UNDEFINED = new OptionalValue(Undefined.undefined);

    public static final OptionalValue NULL = new OptionalValue(null);

    public static final OptionalValue TRUE = new OptionalValue(true);

    public static final OptionalValue FALSE = new OptionalValue(false);

    public static final OptionalValue ZERO = new OptionalValue(0);

    public static final OptionalValue ZERO_LONG = new OptionalValue(0L);

    private final Object value;

    public OptionalValue(Object value) {
        this.value = value;
    }

    public static OptionalValue of(Object value) {
        if (value == null)
            return NULL;

        if (value == Undefined.undefined)
            return UNDEFINED;

        if (Boolean.TRUE.equals(value))
            return TRUE;

        if (Boolean.FALSE.equals(value))
            return FALSE;

        if (MathHelper.ZERO_INT.equals(value))
            return ZERO;

        if (MathHelper.ZERO_LONG.equals(value))
            return ZERO_LONG;

        return new OptionalValue(value);
    }

    public OptionalValue map(Function fn) {
        if (isPresent()) {
            Object v = fn.apply(value);
            return OptionalValue.of(v);
        } else {
            return this;
        }
    }

    public OptionalValue flatMap(Function fn) {
        if (isPresent()) {
            Object v = fn.apply(value);
            if (v instanceof OptionalValue)
                return (OptionalValue) v;
            return OptionalValue.of(v);
        } else {
            return this;
        }
    }

    public void ifPresent(Consumer<?> action) {
        if (value != null) {
            ((Consumer) action).accept(value);
        }
    }

    public Object orElse(Object defaultValue) {
        if (isPresent())
            return value;
        return defaultValue;
    }

    public OptionalValue filter(Predicate<?> predicate) {
        Objects.requireNonNull(predicate);
        if (!isPresent()) {
            return this;
        } else {
            return ((Predicate) predicate).test(value) ? this : UNDEFINED;
        }
    }

    public OptionalValue ignoreNull() {
        if (value == null)
            return UNDEFINED;
        return this;
    }

    public OptionalValue ignoreEmpty() {
        if (StringHelper.isEmptyObject(value))
            return UNDEFINED;
        return this;
    }

    public boolean isPresent() {
        return value != Undefined.undefined;
    }

    public String toString() {
        return OptionalValue.class.getSimpleName() + "[" + value + "]";
    }

    public Object getValue() {
        if (value == UNDEFINED)
            return null;
        return value;
    }

    public Object get() {
        return getValue();
    }

    public boolean asTruthy() {
        if (value == UNDEFINED)
            return false;
        return ConvertHelper.toTruthy(value);
    }

    public boolean asFalsy() {
        return !asTruthy();
    }

    public String asString() {
        return ConvertHelper.toString(getValue());
    }
}