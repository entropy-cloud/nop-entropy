package io.nop.dataset.record.impl;

import io.nop.api.core.util.Guard;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordResourceMeta;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TransformRecordInput<S, T> implements IRecordInput<T> {
    private final IRecordInput<S> source;
    private final Function<S, T> transformer;
    private final IRecordResourceMeta meta;

    public TransformRecordInput(IRecordInput<S> source, Function<S, T> transformer, IRecordResourceMeta meta) {
        this.source = Guard.notNull(source, "source");
        this.transformer = Guard.notNull(transformer, "transformer");
        this.meta = meta;
    }

    @Override
    public IRecordResourceMeta getMeta() {
        return meta;
    }

    public IRecordInput<S> getSource() {
        return source;
    }

    @Override
    public long getTotalCount() {
        return source.getTotalCount();
    }

    @Override
    public long getRemainingCount() {
        return source.getRemainingCount();
    }

    @Override
    public long skip(long count) {
        return source.skip(count);
    }

    @Override
    public IRecordInput<T> limit(long maxCount) {
        return new TransformRecordInput<>(source.limit(maxCount), transformer, meta);
    }

    @Nonnull
    @Override
    public List<T> readBatch(int maxCount) {
        return source.readBatchWithTransformer(maxCount, transformer);
    }

    @Override
    public void readBatch(int maxCount, Consumer<T> ret) {
        source.readBatchWithTransformer(maxCount, transformer, ret);
    }

    @Nonnull
    @Override
    public List<T> readAll() {
        return source.readAllWithTransformer(transformer);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    @Override
    public boolean hasNext() {
        return source.hasNext();
    }

    @Override
    public T next() {
        return transformer.apply(source.next());
    }

    @Override
    public long getReadCount() {
        return source.getReadCount();
    }

    @Override
    public void remove() {
        source.remove();
    }
}
