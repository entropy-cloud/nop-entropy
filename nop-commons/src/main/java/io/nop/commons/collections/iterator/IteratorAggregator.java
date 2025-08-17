package io.nop.commons.collections.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class IteratorAggregator<T> implements Iterator<List<T>> {
    private final PushbackIterator<T> sourceIterator;
    private final BiPredicate<List<T>, T> shouldAddToCurrentBatch;
    private List<T> nextBatch;

    public IteratorAggregator(Iterator<T> sourceIterator,
                              BiPredicate<List<T>, T> shouldAddToCurrentBatch) {
        this.sourceIterator = new PushbackIterator<>(sourceIterator);
        this.shouldAddToCurrentBatch = shouldAddToCurrentBatch;
        this.nextBatch = null;
    }

    @Override
    public boolean hasNext() {
        if (nextBatch != null) {
            return true;
        }

        nextBatch = new ArrayList<>();

        while (sourceIterator.hasNext()) {
            T item = sourceIterator.next();

            if (!nextBatch.isEmpty() && !shouldAddToCurrentBatch.test(nextBatch, item)) {
                // 当前项不适合加入当前批次，返回当前批次
                sourceIterator.pushback(item);
                return true;
            }

            nextBatch.add(item);
        }

        if (nextBatch.isEmpty()) {
            nextBatch = null;
            return false;
        }

        return true;
    }

    @Override
    public List<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        List<T> result = nextBatch;
        nextBatch = null;
        return result;
    }

    // 便捷工厂方法
    public static <T> Iterator<List<T>> aggregate(Iterator<T> sourceIterator,
                                                  BiPredicate<List<T>, T> shouldAddToCurrentBatch) {
        return new IteratorAggregator<>(sourceIterator, shouldAddToCurrentBatch);
    }

    // 基于大小限制的便捷工厂方法
    public static <T> Iterator<List<T>> aggregateBySize(Iterator<T> sourceIterator,
                                                        long maxSize,
                                                        Function<T, Long> sizeExtractor) {
        return aggregate(sourceIterator, (batch, item) -> {
            long currentSize = batch.stream().mapToLong(sizeExtractor::apply).sum();
            return currentSize + sizeExtractor.apply(item) <= maxSize;
        });
    }
}