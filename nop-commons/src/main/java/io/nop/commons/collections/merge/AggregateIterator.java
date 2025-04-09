package io.nop.commons.collections.merge;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class AggregateIterator<S, R> implements Iterator<R> {
    private final Iterator<S> iterator;
    private final AggregateOperator<S, R> aggregator;

    public interface AggregateOperator<S, R> {
        R aggregate(S value);

        R getFinalResult();
    }

    public AggregateIterator(Iterator<S> iterator, AggregateOperator<S, R> aggregator) {
        this.iterator = iterator;
        this.aggregator = aggregator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public R next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        while (iterator.hasNext()) {
            S value = iterator.next();
            R result = aggregator.aggregate(value);
            if (result != null) {
                return result;
            }
        }

        return aggregator.getFinalResult();
    }
}