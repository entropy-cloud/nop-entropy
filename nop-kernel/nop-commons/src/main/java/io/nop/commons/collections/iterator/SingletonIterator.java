package io.nop.commons.collections.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SingletonIterator<E> implements Iterator<E> {
    private final E element;
    private boolean hasNext;

    public SingletonIterator(E element) {
        this.element = element;
        this.hasNext = true;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public E next() {
        if (!hasNext) {
            throw new NoSuchElementException("No more elements");
        }
        hasNext = false;
        return element;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }
}