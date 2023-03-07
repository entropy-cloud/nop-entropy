/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.lattice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class SetLattice<T> implements ILattice<Set<T>> {
    private Set<T> value;

    public SetLattice() {
        this.value = Collections.emptySet();
    }

    public SetLattice(Set<T> value) {
        this.value = normalize(value);
    }

    private Set<T> normalize(Set<T> value) {
        if (value == null)
            return Collections.emptySet();
        return value;
    }

    @Override
    public Set<T> bot() {
        return Collections.emptySet();
    }

    public int size() {
        return value.size();
    }

    public MaxLattice<Integer> sizeLattice() {
        return new MaxLattice<>(size());
    }

    @Override
    public Set<T> value() {
        return value;
    }

    @Override
    public void merge(Set<T> e) {
        if (e == null || e == Collections.emptySet())
            return;

        if (this.value.isEmpty()) {
            value = new HashSet<>(e);
            return;
        }

        value.addAll(e);
    }

    @Override
    public SetLattice<T> cloneInstance() {
        if (value == Collections.emptySet()) {
            return new SetLattice<>(value);
        }
        return new SetLattice<>(new HashSet<>(value));
    }

    @Override
    public void assign(Set<T> e) {
        this.value = normalize(e);
    }

    public SetLattice<T> intersect(Set<T> set) {
        if (set == null || set.isEmpty())
            return new SetLattice<>(bot());

        if (value.isEmpty()) {
            return new SetLattice<>(new HashSet<>(set));
        }

        Set<T> ret = new HashSet<T>(value);
        ret.addAll(set);
        return new SetLattice<>(ret);
    }

    public SetLattice<T> project(Predicate<T> filter) {
        if (value.isEmpty()) {
            return new SetLattice<>(Collections.<T>emptySet());
        }

        Set<T> ret = new HashSet<T>();
        for (T elm : value) {
            if (filter.test(elm))
                ret.add(elm);
        }
        return new SetLattice<>(ret);
    }
}