/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.api.core.util.Guard;

import java.util.Arrays;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.ObjIntConsumer;

public class IntArrayMap<V> implements MapOfInt<V> {
    private final Object UNDEFINED = new Object();
    private final Object[] values;
    private int size;

    public IntArrayMap(int capacity) {
        this.values = new Object[capacity];
        Arrays.fill(values, UNDEFINED);
    }

    public IntArrayMap(Object[] values, int size) {
        this.values = values;
        this.size = size;
    }

    @Override
    public IntArrayMap<V> cloneInstance() {
        return new IntArrayMap<>(values.clone(), size);
    }

    public int capacity() {
        return values.length;
    }

    @Override
    public V get(int index) {
        if (index < 0 || index >= values.length)
            return null;

        Object v = values[index];
        if (v == UNDEFINED)
            return null;
        return (V) v;
    }

    @Override
    public void set(int index, V value) {
        Guard.checkPositionIndex(index, capacity());
        Object oldValue = values[index];
        values[index] = value;
        if (oldValue == UNDEFINED)
            size++;
    }

    @Override
    public V put(int index, V value) {
        Guard.checkPositionIndex(index, capacity());
        Object oldValue = values[index];
        values[index] = value;
        if (oldValue == UNDEFINED) {
            size++;
            return null;
        }
        return (V) oldValue;
    }

    @Override
    public V remove(int index) {
        if (index < 0 || index >= capacity())
            return null;

        Object oldValue = values[index];
        if (oldValue == UNDEFINED) {
            return null;
        }
        values[index] = UNDEFINED;
        size--;
        return (V) oldValue;
    }

    @Override
    public boolean remove(int index, V value) {
        if (index < 0 || index >= capacity())
            return false;

        Object oldValue = values[index];
        if (oldValue != value) {
            return false;
        }
        values[index] = UNDEFINED;
        return true;
    }

    @Override
    public boolean containsKey(int index) {
        if (index < 0 || index >= capacity())
            return false;
        return values[index] != UNDEFINED;
    }

    @Override
    public OfInt keysIterator() {
        return new KeysIterator();
    }

    @Override
    public IntArray keySet() {
        MutableIntArray ret = new MutableIntArray(capacity());
        for (int i = 0, n = capacity(); i < n; i++) {
            if (values[i] != UNDEFINED)
                ret.add(i);
        }
        return ret;
    }

    class KeysIterator implements OfInt {
        private int index;

        @Override
        public int nextInt() {
            if (hasNext()) {
                return index++;
            }
            throw new IllegalStateException("iterator eof");
        }

        @Override
        public boolean hasNext() {
            while (index < values.length) {
                if (values[index] != UNDEFINED)
                    return true;
                index++;
            }

            return false;
        }

        @Override
        public void remove() {
            IntArrayMap.this.remove(index);
        }
    }

    @Override
    public void clear() {
        Arrays.fill(values, UNDEFINED);
        size = 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size <= 0;
    }

    @Override
    public void forEachEntry(ObjIntConsumer<V> consumer) {
        for (int i = 0, n = values.length; i < n; i++) {
            Object value = values[i];
            if (value != UNDEFINED) {
                consumer.accept((V) value, i);
            }
        }
    }
}