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

import java.io.Serializable;
import java.util.Objects;

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

        if (Objects.equals(0, value))
            return ZERO;

        if (Objects.equals(0L, value))
            return ZERO_LONG;

        return new OptionalValue(value);
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