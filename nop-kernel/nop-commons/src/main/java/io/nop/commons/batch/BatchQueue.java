package io.nop.commons.batch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class BatchQueue<T> {
    private final List<T> items = new ArrayList<>();
    private final int batchSize;
    private final Consumer<List<T>> onFlush;

    public BatchQueue(int batchSize, Consumer<List<T>> onFlush) {
        this.batchSize = batchSize;
        this.onFlush = onFlush;
    }

    public void add(T item) {
        items.add(item);
        if (items.size() >= batchSize) {
            onFlush.accept(items);
            items.clear();
        }
    }

    public void addAll(Collection<? extends T> items) {
        if (items != null) {
            for (T item : items) {
                add(item);
            }
        }
    }

    public void flush() {
        if (!items.isEmpty()) {
            onFlush.accept(items);
            items.clear();
        }
    }
}