package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;

import java.util.function.BiFunction;

public class AdaptedBatchLoaderProvider<S> implements IBatchLoaderProvider<S> {
    private final BiFunction<IBatchLoader<S>, IBatchTaskContext, IBatchLoader<S>> adapter;
    private final IBatchLoaderProvider<S> provider;

    public AdaptedBatchLoaderProvider(BiFunction<IBatchLoader<S>, IBatchTaskContext, IBatchLoader<S>> adapter,
                                      IBatchLoaderProvider<S> provider) {
        this.adapter = adapter;
        this.provider = provider;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = provider.setup(context);
        return adapter.apply(loader, context);
    }

    /** 1.7-C6-008（plan 2545 W-07；closure audit R3 Major-1）：透传被包装 provider 的压实能力。 */
    @Override
    public boolean hasChunkCompaction() {
        return provider.hasChunkCompaction();
    }
}