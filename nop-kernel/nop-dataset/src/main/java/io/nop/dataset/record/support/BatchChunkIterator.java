package io.nop.dataset.record.support;

import io.nop.dataset.record.IRecordInput;

import java.util.Iterator;
import java.util.List;

public class BatchChunkIterator<T> implements Iterator<List<T>> {
    private final IRecordInput<T> input;
    private final int chunkSize;

    public BatchChunkIterator(IRecordInput<T> input, int chunkSize) {
        this.input = input;
        this.chunkSize = chunkSize;
    }

    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    @Override
    public List<T> next() {
        return input.readBatch(chunkSize);
    }
}