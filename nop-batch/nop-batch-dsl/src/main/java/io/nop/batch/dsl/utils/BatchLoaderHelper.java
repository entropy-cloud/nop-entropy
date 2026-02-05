package io.nop.batch.dsl.utils;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.loader.ListBatchLoader;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class BatchLoaderHelper {
    static final String KEY_LOADER = "batchLoader";

    public static <T> List<T> batchLoadWithProvider(int batchSize, IBatchChunkContext chunkCtx, IBatchLoaderProvider<T> provider) {
        IBatchTaskContext taskCtx = chunkCtx.getTaskContext();

        IBatchLoaderProvider.IBatchLoader loader = taskCtx.computeIfAbsent(KEY_LOADER, k -> {
            return provider.setup(taskCtx);
        });
        return loader.load(batchSize, chunkCtx);
    }

    public static <T> List<T> batchLoadWithFullList(int batchSize, IBatchChunkContext chunkCtx,
                                            Function<IBatchTaskContext, List<T>> listProvider) {
        return batchLoadWithProvider(batchSize, chunkCtx, taskCtx -> {
            List<T> list = listProvider.apply(taskCtx);
            if (list == null)
                list = Collections.emptyList();
            return new ListBatchLoader<>(list);
        });
    }
}
