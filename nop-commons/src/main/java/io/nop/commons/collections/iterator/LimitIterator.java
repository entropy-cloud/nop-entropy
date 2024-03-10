/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import java.util.Iterator;

public class LimitIterator<E> implements Iterator<E> {
    private final Iterator<E> it;
    private final long limit;
    private int count;

    public LimitIterator(Iterator<E> it, long limit) {
        this.it = it;
        this.limit = limit;
    }

    @Override
    public boolean hasNext() {
        return count <= limit && it.hasNext();
    }

    @Override
    public E next() {
        if (count < limit) {
            E o = it.next();
            count++;
            return o;
        }
        throw new IllegalStateException("iterator count exceed limit: count=" + count + ",limit=" + limit);
    }
}