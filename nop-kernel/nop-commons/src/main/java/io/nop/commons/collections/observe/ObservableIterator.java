/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.observe;

import java.util.Iterator;

public class ObservableIterator<E> implements Iterator<E> {
    private final Iterator<E> iterator;
    private final ICollectionObserver observer;

    public ObservableIterator(Iterator<E> iterator, ICollectionObserver observer) {
        this.iterator = iterator;
        this.observer = observer;
    }

    public ICollectionObserver getObserver() {
        return observer;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public E next() {
        return onReturn(iterator.next());
    }

    protected E onReturn(E element) {
        return element;
    }

    @Override
    public void remove() {
        observer.beforeModify(iterator);
        iterator.remove();
        observer.afterModify(true);
    }
}