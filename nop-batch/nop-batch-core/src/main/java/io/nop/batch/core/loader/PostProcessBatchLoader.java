package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.List;

public class PostProcessBatchLoader<S> implements IBatchLoaderProvider.IBatchLoader<S> {
    private final IBatchLoaderProvider.IBatchLoader<S> loader;
    private final IEvalFunction afterLoad;

    public PostProcessBatchLoader(IBatchLoaderProvider.IBatchLoader<S> loader, IEvalFunction afterLoad) {
        this.loader = loader;
        this.afterLoad = afterLoad;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        List<S> items = loader.load(batchSize, context);
        if (items != null && !items.isEmpty()) {
            afterLoad.call2(null, items, context, context.getEvalScope());
        }
        return items;
    }
}
