package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchRecordFilter;

import java.util.List;

public class FilteredBatchLoader<S> implements IBatchLoaderProvider.IBatchLoader<S> {
    private final IBatchRecordFilter<S, IBatchChunkContext> filter;
    private final IBatchLoaderProvider.IBatchLoader<S> loader;

    public FilteredBatchLoader(IBatchRecordFilter<S, IBatchChunkContext> filter, IBatchLoaderProvider.IBatchLoader<S> loader) {
        this.filter = filter;
        this.loader = loader;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        do {
            List<S> list = loader.load(batchSize, context);
            if (list == null || list.isEmpty())
                return list;

            list = filter.filter(list, context);
            if (!list.isEmpty())
                return list;
        } while (true);
    }
}