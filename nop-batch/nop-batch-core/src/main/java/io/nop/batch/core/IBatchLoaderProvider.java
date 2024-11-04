package io.nop.batch.core;

public interface IBatchLoaderProvider<S, C> {
    IBatchLoader<S, C> setup(IBatchTaskContext context);
}