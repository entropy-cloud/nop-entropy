package io.nop.batch.core;

public interface IEnhancedBatchLoader<S, C> extends IBatchLoader<S, C> {
    IBatchLoader<?, ?> getBaseLoader();
}
