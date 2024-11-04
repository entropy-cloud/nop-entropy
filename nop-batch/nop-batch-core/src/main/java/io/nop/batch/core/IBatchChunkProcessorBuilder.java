package io.nop.batch.core;

public interface IBatchChunkProcessorBuilder<S> {
    IBatchChunkProcessor buildChunkProcessor(IBatchLoaderProvider.IBatchLoader<S> loader,
                                             int batchSize, double jitterRatio,
                                             IBatchConsumerProvider.IBatchConsumer<S> consumer
    );
}
