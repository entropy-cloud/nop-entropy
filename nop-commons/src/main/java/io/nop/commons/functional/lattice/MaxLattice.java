/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.lattice;

import java.util.Objects;

public class MaxLattice<T extends Comparable<T>> implements ILattice<T> {
    private T value;

    public MaxLattice() {
    }

    public MaxLattice(T value) {
        this.value = value;
    }

    public MaxLattice<T> cloneInstance() {
        return new MaxLattice<>(value);
    }

    @Override
    public T bot() {
        return null;
    }

    public T value() {
        return value;
    }

    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof MaxLattice))
            return false;
        MaxLattice<T> other = (MaxLattice<T>) o;

        return Objects.equals(value, other.value);
    }

    @Override
    public void merge(T e) {
        if (e == null)
            return;

        if (value == null) {
            value = e;
            return;
        }

        if (value.compareTo(e) < 0) {
            value = e;
        }
    }

    @Override
    public void assign(T e) {
        this.value = e;
    }
}
