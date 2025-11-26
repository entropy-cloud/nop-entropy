/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.mutable;

import java.util.function.Supplier;

public class MutableValue<T> implements IMutableValue<T> {
    private T value;

    public MutableValue(T value) {
        this.value = value;
    }

    public MutableValue() {

    }

    public T lazyGet(Supplier<T> supplier) {
        if (value == null)
            value = supplier.get();
        return value;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }

    public T get() {
        return getValue();
    }

    public void set(T value) {
        setValue(value);
    }
}
