package io.nop.commons.collections.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class FillMinIterator<E> implements Iterator<E> {
    private final Iterator<E> it;
    private final int minCount;
    private final E defaultElement;

    private int count;

    public FillMinIterator(Iterator<E> it, int minCount, E defaultElement) {
        this.it = it;
        this.minCount = minCount;
        this.defaultElement = defaultElement;
    }

    @Override
    public boolean hasNext() {
        return count <= minCount || it.hasNext();
    }

    @Override
    public E next() {
        if (it.hasNext()) {
            count++;
            return it.next();
        }

        if (count < minCount) {
            count++;
            return defaultElement;
        }
        throw new NoSuchElementException("iterator count exceed limit: count=" + count + ",limit=" + minCount);
    }
}
