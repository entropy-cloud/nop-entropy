/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.lang.IEnumLike;

public abstract class EnumLike<E extends IEnumLike<E>> implements IEnumLike<E> {
    private final int ordinal;
    private final String name;

    public EnumLike(String name, int ordinal) {
        this.ordinal = ordinal;
        this.name = Guard.notEmpty(name, "enum name");
    }

    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return ordinal;
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public int ordinal() {
        return ordinal;
    }

    @Override
    public String label() {
        return name;
    }

    public String name() {
        return name;
    }

    @Override
    public int compareTo(E o) {
        if (this.getClass() != o.getClass())
            throw new ClassCastException();
        return Integer.compare(ordinal, o.ordinal());
    }
}