package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface IBatchLoaderProvider<S> {
    IBatchLoader<S> setup(IBatchTaskContext context);

    default <R> IBatchLoaderProvider<R> withHook(Function<IBatchLoader<S>, IBatchLoader<R>> hook) {
        return ctx -> {
            return hook.apply(setup(ctx));
        };
    }

    /**
     * <p>
     * 批量装载一批数据
     *
     * @param <S>
     */
    interface IBatchLoader<S> {
        /**
         * 加载数据
         *
         * @param batchSize 最多装载多少条数据
         * @return 返回空集合表示所有数据已经加载完毕
         */
        List<S> load(int batchSize, IBatchChunkContext context);

        default CompletionStage<List<S>> loadAsync(int batchSize, IBatchChunkContext context) {
            return FutureHelper.futureCall(() -> load(batchSize, context));
        }
    }
}