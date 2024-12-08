package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.commons.functional.IFunctionInvoker;

import java.util.List;

public class InvokerBatchLoader<S> implements IBatchLoaderProvider.IBatchLoader<S> {
    private final IFunctionInvoker invoker;
    private final IBatchLoaderProvider.IBatchLoader<S> loader;

    public InvokerBatchLoader(IFunctionInvoker invoker, IBatchLoaderProvider.IBatchLoader<S> loader) {
        this.invoker = invoker;
        this.loader = loader;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        return invoker.invoke(ctx -> loader.load(batchSize, ctx), context);
    }
}
