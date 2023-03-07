/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.observe;

import java.util.Collection;
import java.util.Iterator;

public class ObservableCollection<E> implements Collection<E> {
    private final Collection<E> collection;
    private final ICollectionObserver observer;

    public ObservableCollection(Collection<E> collection, ICollectionObserver observer) {
        this.collection = collection;
        this.observer = observer;
    }

    protected Collection<E> getCollection() {
        return collection;
    }

    protected ICollectionObserver getObserver() {
        return observer;
    }

    @Override
    public Iterator<E> iterator() {
        return new ObservableIterator<>(collection.iterator(), observer);
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public boolean isEmpty() {
        return collection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return collection.contains(o);
    }

    @Override
    public Object[] toArray() {
        return collection.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return collection.toArray(a);
    }

    @Override
    public boolean add(E e) {
        observer.beforeModify(collection);
        boolean b = collection.add(e);
        observer.afterModify(b);
        return b;
    }

    @Override
    public boolean remove(Object o) {
        observer.beforeModify(collection);
        boolean b = collection.remove(o);
        observer.afterModify(b);
        return b;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return collection.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        observer.beforeModify(collection);
        boolean b = collection.addAll(c);
        observer.afterModify(b);
        return b;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        observer.beforeModify(collection);
        boolean b = collection.removeAll(c);
        observer.afterModify(b);
        return b;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        observer.beforeModify(collection);
        boolean b = collection.retainAll(c);
        observer.afterModify(b);
        return b;
    }

    @Override
    public void clear() {
        observer.beforeModify(collection);
        boolean empty = collection.isEmpty();
        collection.clear();
        observer.afterModify(!empty);
    }
}