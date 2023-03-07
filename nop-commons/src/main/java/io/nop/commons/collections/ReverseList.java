/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

public class ReverseList<T> extends AbstractList<T> implements RandomAccess {
    private final List<T> forwardList;

    public ReverseList(List<T> forwardList) {
        this.forwardList = forwardList;
    }

    public List<T> getForwardList() {
        return forwardList;
    }

    private int reverseIndex(int index) {
        int size = size();
        return (size - 1) - index;
    }

    private int reversePosition(int index) {
        int size = size();
        return size - index;
    }

    @Override
    public void add(int index, T element) {
        forwardList.add(reversePosition(index), element);
    }

    @Override
    public void clear() {
        forwardList.clear();
    }

    @Override
    public T remove(int index) {
        return forwardList.remove(reverseIndex(index));
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        subList(fromIndex, toIndex).clear();
    }

    @Override
    public T set(int index, T element) {
        return forwardList.set(reverseIndex(index), element);
    }

    @Override
    public T get(int index) {
        return forwardList.get(reverseIndex(index));
    }

    @Override
    public int size() {
        return forwardList.size();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return new ReverseList<T>(forwardList.subList(reversePosition(toIndex), reversePosition(fromIndex)));
    }

    @Override
    public Iterator<T> iterator() {
        return listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        int start = reversePosition(index);
        final ListIterator<T> forwardIterator = forwardList.listIterator(start);
        return new ListIterator<T>() {

            @Override
            public void add(T e) {
                forwardIterator.add(e);
                forwardIterator.previous();
            }

            @Override
            public boolean hasNext() {
                return forwardIterator.hasPrevious();
            }

            @Override
            public boolean hasPrevious() {
                return forwardIterator.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return forwardIterator.previous();
            }

            @Override
            public int nextIndex() {
                return reversePosition(forwardIterator.nextIndex());
            }

            @Override
            public T previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException();
                }
                return forwardIterator.next();
            }

            @Override
            public int previousIndex() {
                return nextIndex() - 1;
            }

            @Override
            public void remove() {
                forwardIterator.remove();
            }

            @Override
            public void set(T e) {
                forwardIterator.set(e);
            }
        };
    }
}