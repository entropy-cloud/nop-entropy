/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;

public class FlatMapIterator<S, R> implements Iterator<R> {
    private final Iterator<S> parentIt;
    private final Function<? super S, ? extends Iterable<R>> fn;
    private Iterator<R> it = Collections.<R>emptySet().iterator();

    public FlatMapIterator(Iterator<S> parentIt, Function<? super S, ? extends Iterable<R>> fn) {
        this.parentIt = parentIt;
        this.fn = fn;
//        hasNext();
    }

    @Override
    public boolean hasNext() {
        while (!it.hasNext()) {
            it = nextIterator();
            if (it == null) {
                it = Collections.<R>emptySet().iterator();
                return false;
            }
        }
        return true;
    }

    Iterator<R> nextIterator() {
        if (parentIt.hasNext()) {
            S v = parentIt.next();
            Iterable<R> next = fn.apply(v);
            if (next == null)
                return null;

            return next.iterator();
        }
        return null;
    }

    @Override
    public R next() {
        return it.next();
    }
}
