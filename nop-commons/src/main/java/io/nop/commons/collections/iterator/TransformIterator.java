/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import io.nop.commons.collections.IterableIterator;

import java.util.Iterator;
import java.util.function.Function;

public class TransformIterator<T, R> implements IterableIterator<R> {
    private final Iterator<T> it;
    private final Function<T, R> fn;

    public TransformIterator(Iterator<T> it, Function<T, R> fn) {
        this.it = it;
        this.fn = fn;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public R next() {
        return fn.apply(it.next());
    }
}