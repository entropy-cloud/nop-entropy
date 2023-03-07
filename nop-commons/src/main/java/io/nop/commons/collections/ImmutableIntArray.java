/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.Arrays;

public class ImmutableIntArray implements IntArray {
    public static ImmutableIntArray EMPTY = new ImmutableIntArray(new int[0]);

    private final int[] items;
    private final int size;
    private final boolean ordered;

    public ImmutableIntArray(int[] items, int size, boolean ordered) {
        this.items = items;
        this.size = size;
        this.ordered = ordered;
    }

    public ImmutableIntArray(int[] items) {
        this(items, items.length, true);
    }

    @Override
    public boolean isOrdered() {
        return ordered;
    }

    @Override
    public IntArray sort() {
        if (ordered)
            return this;

        int[] copy = new int[size];
        System.arraycopy(items, 0, copy, 0, size);
        Arrays.sort(copy);
        return new ImmutableIntArray(copy, size, true);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int get(int index) {
        return items[index];
    }

    @Override
    public void copyTo(int[] to, int toIndex) {
        System.arraycopy(items, 0, to, toIndex, size);
    }

    @Override
    public MutableIntArray toMutable() {
        return new MutableIntArray(this);
    }

    @Override
    public ImmutableIntArray toImmutable() {
        return this;
    }

    @Override
    public int indexOf(int value) {
        int[] items = this.items;
        for (int i = 0, n = size; i < n; i++)
            if (items[i] == value)
                return i;
        return -1;
    }

    @Override
    public int lastIndexOf(int value) {
        int[] items = this.items;
        for (int i = size - 1; i >= 0; i--) {
            if (items[i] == value)
                return i;
        }
        return -1;
    }

    @Override
    public IntArray merge(IntArray array) {
        if (isEqual(array))
            return this;

        MutableIntArray ret = toMutable();
        return ret.merge(array);
    }

    @Override
    public IntArray merge(int value) {
        if (contains(value))
            return this;

        MutableIntArray ret = toMutable();
        return ret.add(value);
    }

    @Override
    public boolean isEqual(IntArray o) {
        if (this == o)
            return true;
        if (size != o.size())
            return false;

        int[] items = this.items;
        for (int i = 0, n = size; i < n; i++) {
            if (items[i] != o.get(i))
                return false;
        }
        return true;
    }
}
