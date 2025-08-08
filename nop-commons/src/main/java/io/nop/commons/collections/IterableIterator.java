/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.commons.collections.iterator.FilterIterator;
import io.nop.commons.collections.iterator.FlatMapIterator;
import io.nop.commons.collections.iterator.TransformIterator;
import io.nop.commons.util.CollectionHelper;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IterableIterator<T> extends Iterator<T>, Iterable<T> {
    @Override
    default Iterator<T> iterator() {
        return this;
    }

    default <R> IterableIterator<R> map(Function<T, R> transformer) {
        return new TransformIterator<>(iterator(), transformer);
    }

    default <R> IterableIterator<R> flatMap(Function<T, ? extends Iterable<R>> transformer) {
        return new FlatMapIterator<>(iterator(), transformer);
    }

    default IterableIterator<T> filter(Predicate<T> filter) {
        return new FilterIterator<>(iterator(), filter);
    }

    default List<T> toList() {
        return CollectionHelper.iteratorToList(iterator());
    }
}