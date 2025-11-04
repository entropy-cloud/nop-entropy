/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.observe;

import java.util.ListIterator;

public class ObservableListIterator<E> implements ListIterator<E> {
    private final ListIterator<E> it;
    private final ICollectionObserver observer;

    public ObservableListIterator(ListIterator<E> it, ICollectionObserver observer) {
        this.it = it;
        this.observer = observer;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public E next() {
        return onReturn(it.next());
    }

    protected E onReturn(E value) {
        return value;
    }

    @Override
    public boolean hasPrevious() {
        return it.hasPrevious();
    }

    @Override
    public E previous() {
        return onReturn(it.previous());
    }

    @Override
    public int nextIndex() {
        return it.nextIndex();
    }

    @Override
    public int previousIndex() {
        return it.previousIndex();
    }

    @Override
    public void remove() {
        observer.beforeModify(it);
        it.remove();
        observer.afterModify(true);
    }

    @Override
    public void set(E e) {
        observer.beforeModify(it);
        it.set(e);
        observer.afterModify(true);
    }

    @Override
    public void add(E e) {
        observer.beforeModify(it);
        it.add(e);
        observer.afterModify(true);
    }
}