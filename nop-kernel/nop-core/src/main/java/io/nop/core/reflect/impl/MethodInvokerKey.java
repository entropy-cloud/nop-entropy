/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import java.util.Arrays;

public final class MethodInvokerKey {
    private final boolean _static;
    private final String name;
    private final Class<?>[] argTypes;
    private int h;

    public MethodInvokerKey(boolean isStatic, String name, Class<?>... argTypes) {
        this._static = isStatic;
        this.name = name;
        this.argTypes = argTypes;
    }

    public boolean isStatic() {
        return _static;
    }

    public int hashCode() {
        if (h == 0) {
            h = isStatic() ? 0 : 1;
            h += name.hashCode() * 31;
            h += Arrays.hashCode(argTypes);
        }
        return h;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof MethodInvokerKey))
            return false;

        MethodInvokerKey other = (MethodInvokerKey) o;
        if (_static != other._static)
            return false;

        if (!name.equals(other.name))
            return false;

        if (!Arrays.equals(argTypes, other.argTypes))
            return false;

        return true;
    }

    public String getName() {
        return name;
    }

    public Class<?>[] getArgTypes() {
        return argTypes;
    }
}
