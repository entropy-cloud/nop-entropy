/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.iterator;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.IoHelper;

import java.util.Iterator;

public class PeekingIterator<E> implements IPeekingIterator<E>, AutoCloseable {

    private final Iterator<? extends E> iterator;
    private boolean hasPeeked;
    private E peekedElement;

    public PeekingIterator(Iterator<? extends E> iterator) {
        this.iterator = Guard.notNull(iterator, "iterator is null");
    }

    public static <E> IPeekingIterator<E> forPeeking(Iterator<E> it) {
        if (it instanceof IPeekingIterator<?>)
            return ((IPeekingIterator<E>) it);
        return new PeekingIterator<E>(it);
    }

    @Override
    public boolean hasNext() {
        return hasPeeked || iterator.hasNext();
    }

    @Override
    public E next() {
        if (!hasPeeked) {
            return iterator.next();
        }
        E result = peekedElement;
        hasPeeked = false;
        peekedElement = null;
        return result;
    }

    @Override
    public void remove() {
        Guard.checkState(!hasPeeked, "Can't remove after you've peeked at next");
        iterator.remove();
    }

    @Override
    public E peek() {
        if (!hasPeeked) {
            peekedElement = iterator.next();
            hasPeeked = true;
        }
        return peekedElement;
    }

    @Override
    public void close() throws Exception {
        IoHelper.safeClose(iterator);
    }
}
