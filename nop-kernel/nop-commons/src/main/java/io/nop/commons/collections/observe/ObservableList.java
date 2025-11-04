/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.observe;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

public class ObservableList<E> extends ObservableCollection<E> implements List<E> {
    public ObservableList(List<E> collection, ICollectionObserver observer) {
        super(collection, observer);
    }

    protected List<E> getCollection() {
        return (List<E>) super.getCollection();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        ICollectionObserver observer = getObserver();
        List<E> list = getCollection();
        observer.beforeModify(list);
        boolean b = list.addAll(index, c);
        observer.afterModify(true);
        return b;
    }

    @Override
    public E get(int index) {
        return getCollection().get(index);
    }

    @Override
    public E set(int index, E element) {
        ICollectionObserver observer = getObserver();
        List<E> list = getCollection();
        observer.beforeModify(list);
        E old = list.set(index, element);
        observer.afterModify(true);
        return old;
    }

    @Override
    public void add(int index, E element) {
        ICollectionObserver observer = getObserver();
        List<E> list = getCollection();
        observer.beforeModify(list);
        list.add(index, element);
        observer.afterModify(true);
    }

    @Override
    public E remove(int index) {
        ICollectionObserver observer = getObserver();
        List<E> list = getCollection();
        observer.beforeModify(list);
        E old = list.remove(index);
        observer.afterModify(true);
        return old;
    }

    @Override
    public int indexOf(Object o) {
        return getCollection().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return getCollection().lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return null;
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return null;
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return new ObservableList<>(getCollection().subList(fromIndex, toIndex), getObserver());
    }
}