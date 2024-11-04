package io.nop.batch.core;

import io.nop.batch.core.processor.CompositeBatchProcessor;

import java.util.function.Consumer;

public interface IBatchProcessorProvider<S, R> {
    IBatchProcessor<S, R> setup(IBatchTaskContext taskContext);

    default <T> IBatchProcessorProvider<S, T> then(IBatchProcessorProvider<R, T> processor) {
        return taskContext -> {
            IBatchProcessor<S, R> processor1 = setup(taskContext);
            IBatchProcessor<R, T> processor2 = processor.setup(taskContext);
            return processor1.then(processor2);
        };
    }

    /**
     * 逐条处理数据，可能产生后续数据（一条或多条），也可能不产生数据。类似于flatMap操作
     *
     * @param <S> 来源记录类型
     * @param <R> 处理后产生的记录类型
     */
    interface IBatchProcessor<S, R> {
        /**
         * 执行类似flatMap的操作
         *
         * @param item     输入数据对象
         * @param consumer 接收返回结果，可能为一条或者多条。也可能不产生数据导致consumer不会被调用
         * @param context  上下文信息
         */
        void process(S item, Consumer<R> consumer, IBatchChunkContext context);

        /**
         * 两个processor合成为一个processor
         */
        default <T> IBatchProcessor<S, T> then(IBatchProcessor<R, T> processor) {
            return new CompositeBatchProcessor<>(this, processor);
        }
    }
}