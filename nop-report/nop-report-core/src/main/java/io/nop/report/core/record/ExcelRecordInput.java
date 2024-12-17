package io.nop.report.core.record;

import io.nop.dataset.record.IRecordInput;

import java.io.IOException;

public class ExcelRecordInput<T> implements IRecordInput<T> {
    private long readCount;

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public T next() {
        return null;
    }
}
