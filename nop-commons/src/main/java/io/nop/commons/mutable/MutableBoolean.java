/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.mutable;

public class MutableBoolean implements IMutableValue<Boolean> {
    private boolean value;

    public MutableBoolean() {
    }

    public MutableBoolean(boolean b) {
        this.value = b;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void setValue(Boolean value) {
        this.value = value != null && value;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean b) {
        this.value = b;
    }

    public boolean compareAndSet(boolean expected, boolean newValue) {
        if (value == expected) {
            value = newValue;
            return true;
        }
        return false;
    }

    public boolean toggle() {
        value = !value;
        return value;
    }
}