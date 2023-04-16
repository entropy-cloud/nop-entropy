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
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.Undefined;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;

/**
 * 记录了值所对应的源码位置
 */
@ImmutableBean
public class ValueWithLocation implements Serializable, ISourceLocationGetter, IDeepCloneable {
    private static final long serialVersionUID = -5452045237445388102L;

    public static final ValueWithLocation NULL_VALUE = new ValueWithLocation(null, null);
    public static final ValueWithLocation UNDEFINED_VALUE = new ValueWithLocation(null, Undefined.undefined);

    private final SourceLocation loc;
    private final Object value;

    protected ValueWithLocation(SourceLocation loc, Object value) {
        this.loc = loc;
        this.value = value;
    }

    private Object readResolve() {
        if (value == null) {
            if (loc == null)
                return NULL_VALUE;
        } else if (value == Undefined.undefined) {
            return UNDEFINED_VALUE;
        }
        return this;
    }

    public String toString() {
        return value + "@loc=" + loc;
    }

    public static ValueWithLocation of(SourceLocation loc, Object value) {
        if (loc == null && value == null)
            return NULL_VALUE;
        if (value == Undefined.undefined)
            return UNDEFINED_VALUE;
        if (value instanceof ValueWithLocation)
            return (ValueWithLocation) value;
        return new ValueWithLocation(loc, value);
    }

    public boolean isUndefined() {
        return value == Undefined.undefined;
    }

    public boolean isNull() {
        return value == null || value == Undefined.undefined;
    }

    public boolean isEmpty() {
        if (value == Undefined.undefined || value == null)
            return true;
        return StringHelper.isEmptyObject(value);
    }

    public String asString() {
        return ConvertHelper.toString(value);
    }

    public String asString(String defaultValue) {
        String str = asString();
        if (str == null)
            str = defaultValue;
        return str;
    }

    public Class<?> getValueClass() {
        return value == null ? null : value.getClass();
    }

    public boolean isCDataText() {
        return value instanceof CDataText;
    }

    public boolean isStringValue() {
        return value instanceof String || value instanceof CDataText;
    }

    public Object getValue() {
        return value;
    }

    public SourceLocation getLocation() {
        return loc;
    }

    public ValueWithLocation addRef(String ref) {
        if (StringHelper.isEmpty(ref))
            return this;

        if (loc == null)
            return this;

        SourceLocation loc = this.loc.addRef(ref);
        return ValueWithLocation.of(loc, value);
    }

    @Override
    public ValueWithLocation deepClone() {
        if (this.value == null)
            return this;

        Object cloned = CloneHelper.deepClone(value);
        if (cloned == value)
            return this;
        return newValueWithLocation(loc, cloned);
    }

    protected ValueWithLocation newValueWithLocation(SourceLocation loc, Object value) {
        return new ValueWithLocation(loc, value);
    }
}