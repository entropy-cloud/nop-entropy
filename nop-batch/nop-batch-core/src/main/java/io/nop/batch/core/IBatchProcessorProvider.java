package io.nop.batch.core;

public interface IBatchProcessorProvider<S, R, C> {
    IBatchProcessor<S, R, C> setup(IBatchTaskContext taskContext);
}