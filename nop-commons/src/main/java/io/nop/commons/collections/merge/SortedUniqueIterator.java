package io.nop.commons.collections.merge;

import io.nop.commons.functional.IEqualsChecker;

import java.util.Iterator;
import java.util.Objects;

public class SortedUniqueIterator<E> implements Iterator<E> {
    private final Iterator<E> iterator;
    private E next;
    private final IEqualsChecker<E> equalsChecker;

    public SortedUniqueIterator(Iterator<E> iterator, IEqualsChecker<E> equalsChecker) {
        this.iterator = iterator;
        this.equalsChecker = equalsChecker;
        // 初始化时预取第一个元素作为next
        if (iterator.hasNext()) {
            next = iterator.next();
        } else {
            next = null;
        }
    }

    public SortedUniqueIterator(Iterator<E> iterator) {
        this(iterator, Objects::equals);
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new java.util.NoSuchElementException();
        }
        E current = next;
        next = null;
        // 寻找下一个不等于current的元素
        while (iterator.hasNext()) {
            E candidate = iterator.next();
            if (!equalsChecker.isEquals(candidate, current)) {
                next = candidate;
                break;
            }
        }
        return current;
    }
}