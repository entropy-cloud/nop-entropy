package io.nop.batch.core;

import io.nop.batch.core.consumer.FilteredBatchConsumer;

import java.util.List;

public interface IBatchConsumerProvider<R> {

    IBatchConsumer<R> setup(IBatchTaskContext context);

    default IBatchConsumerProvider<R> withFilter(IBatchRecordFilter<R, IBatchChunkContext> filter) {
        return context -> {
            IBatchConsumer<R> consumer = setup(context);
            return new FilteredBatchConsumer<>(filter, consumer);
        };
    }

    /**
     * 批量消费一组数据对象。
     *
     * @param <R>
     */
    interface IBatchConsumer<R> {
        /**
         * @param items   待处理的对象集合
         * @param context 上下文对象
         */
        void consume(List<R> items, IBatchChunkContext context);
    }
}
