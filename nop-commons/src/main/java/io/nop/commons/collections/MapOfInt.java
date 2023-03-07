/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import io.nop.api.core.util.ICloneable;

import java.util.PrimitiveIterator.OfInt;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

public interface MapOfInt<T> extends ICloneable {

    MapOfInt<T> cloneInstance();

    default T computeIfAbsent(int index, IntFunction<T> fn) {
        T obj = get(index);
        if (obj == null) {
            obj = fn.apply(index);
            put(index, obj);
        }
        return obj;
    }

    T get(int index);

    void set(int index, T value);

    T put(int index, T value);

    T remove(int index);

    boolean remove(int index, T value);

    boolean containsKey(int index);

    OfInt keysIterator();

    IntArray keySet();

    void clear();

    int size();

    boolean isEmpty();

    void forEachEntry(ObjIntConsumer<T> consumer);
}