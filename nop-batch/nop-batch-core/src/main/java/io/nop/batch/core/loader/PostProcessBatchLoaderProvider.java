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
}