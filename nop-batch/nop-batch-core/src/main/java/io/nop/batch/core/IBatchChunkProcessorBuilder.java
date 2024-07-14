package io.nop.batch.core;

public interface IBatchChunkProcessorBuilder<S> {
    IBatchChunkProcessor buildChunkProcessor(IBatchLoader<S, IBatchChunkContext> loader,
                                             int batchSize, double jitterRatio,
                                             IBatchConsumer<S, IBatchChunkContext> consumer
    );
}
