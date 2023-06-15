package io.nop.batch.core;

public interface IBatchRecordFilter<T> {
    boolean accept(T record, IBatchTaskContext context);
}
