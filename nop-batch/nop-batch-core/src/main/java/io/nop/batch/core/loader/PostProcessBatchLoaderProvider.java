package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.eval.IEvalFunction;

public class PostProcessBatchLoaderProvider<S> implements IBatchLoaderProvider<S> {
    private final IBatchLoaderProvider<S> loaderProvider;
    private final IEvalFunction afterLoad;

    public PostProcessBatchLoaderProvider(IBatchLoaderProvider<S> loaderProvider, IEvalFunction afterLoad) {
        this.loaderProvider = loaderProvider;
        this.afterLoad = afterLoad;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = loaderProvider.setup(context);
        return new PostProcessBatchLoader<>(loader, afterLoad);
    }

    /** 1.7-C6-008（plan 2545；closure audit R2 BL-1）：透传被包装 provider 的 chunk 压实能力。 */
    @Override
    public boolean hasChunkCompaction() {
        return loaderProvider.hasChunkCompaction();
    }
}