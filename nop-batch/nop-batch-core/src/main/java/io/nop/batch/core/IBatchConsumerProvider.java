package io.nop.batch.core;

public interface IBatchConsumerProvider<R, C> {
    IBatchConsumer<R, C> setup(IBatchTaskContext context);
}
