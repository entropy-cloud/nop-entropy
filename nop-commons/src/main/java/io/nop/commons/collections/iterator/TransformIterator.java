package io.nop.commons.collections.iterator;

import java.util.Iterator;
import java.util.function.Function;

public class TransformIterator<T, R> implements Iterator<R> {
    private final Iterator<T> it;
    private final Function<T, R> fn;

    public TransformIterator(Iterator<T> it, Function<T, R> fn) {
        this.it = it;
        this.fn = fn;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public R next() {
        return fn.apply(it.next());
    }
}