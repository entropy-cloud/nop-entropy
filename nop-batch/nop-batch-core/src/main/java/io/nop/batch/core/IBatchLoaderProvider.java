package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface IBatchLoaderProvider<S> {
    IBatchLoader<S> setup(IBatchTaskContext context);

    /**
     * 1.7-C6-008（plan 2545 W-07；closure audit R2 BL-1 修正）：本 provider 的 loader 是否在
     * <b>chunk 级</b> {@code onAfterComplete} 上承担状态压实职责（标记 processingItems 完成、
     * 推进 completedIndex）。
     * <p>
     * 这是判断「saveState 语义是否依赖消费 chunk ctx」的唯一能力标记。压缩面（如
     * {@link io.nop.batch.core.loader.ResourceRecordLoaderProvider} 在 saveState=true 时）
     * 必须覆写为 {@code true}；任务级 state store 接线（{@code IBatchStateStore}）与 jdbc/orm
     * 的「任务结束关连接」不属于 chunk 压实，不覆写。
     * <p>
     * 用途：异步 fetch 派发下 load 与 consume 跨线程解耦，压实回调无法附着到实际消费该批数据的
     * chunk ctx（fetch ctx 的 {@code getChunkItems()} 恒为 null），故
     * {@code 压实=true + fetchThreadCount>0} 必须在装配期 fail-fast。
     */
    default boolean hasChunkCompaction() {
        return false;
    }

    default <R> IBatchLoaderProvider<R> withHook(Function<IBatchLoader<S>, IBatchLoader<R>> hook) {
        // 1.7-C6-008（plan 2545 W-07；closure audit R3 Major-1）：必须转发 hasChunkCompaction()——
        // 裸 lambda 会静默擦除压实能力标记，使装配期 fail-fast 守卫失效。
        IBatchLoaderProvider<S> self = this;
        return new IBatchLoaderProvider<R>() {
            @Override
            public IBatchLoader<R> setup(IBatchTaskContext context) {
                return hook.apply(self.setup(context));
            }

            @Override
            public boolean hasChunkCompaction() {
                return self.hasChunkCompaction();
            }
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
         * 加载数据。这个方法必须是线程安全的
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