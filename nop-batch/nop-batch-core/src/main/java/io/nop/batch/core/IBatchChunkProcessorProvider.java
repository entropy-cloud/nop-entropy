package io.nop.batch.core;

import io.nop.api.core.util.ProcessResult;

public interface IBatchChunkProcessorProvider<S> {
    IBatchChunkProcessor<S> setup(IBatchLoaderProvider.IBatchLoader<S> loader, IBatchTaskContext batchTaskContext);

    /**
     * 处理一个批次的数据。包含了loader=>processor=>consumer的完整调用过程。
     * 一般会使用{@link io.nop.batch.core.processor.BatchChunkProcessor}实现类，不会直接用到这个接口
     */
    interface IBatchChunkProcessor<S> {
        ProcessResult process(IBatchChunkContext context);
    }
}
