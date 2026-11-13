/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.collections.MapOfInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 利用底层的loader读取数据，然后按照partition切分成多个顺序队列。确保一个partition的数据不会同时有两个线程在处理。
 *
 * @param <S>
 */
public class PartitionDispatchLoaderProvider<S>
        implements IBatchLoaderProvider<S> {

    private final IBatchLoaderProvider<S> loader;
    private final int loadBatchSize;
    private final BiFunction<S, IBatchTaskContext, Integer> partitionFn;
    private int loadBatchMultiplyFactor = 30;
    private int maxLockQueueCountPerThread = 100;

    public PartitionDispatchLoaderProvider(IBatchLoaderProvider<S> loader,
                                           int loadBatchSize, BiFunction<S, IBatchTaskContext, Integer> partitionFn) {
        this.loader = loader;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = Guard.notNull(partitionFn, "partitionFn");
    }

    public int getLoadBatchMultiplyFactor() {
        return loadBatchMultiplyFactor;
    }

    /** 1.7-C8-009（plan 2546 W-10）：factor 必须 >=1——<=0 会使队列容量退化为 0 导致挂死。 */
    public void setLoadBatchMultiplyFactor(int loadBatchMultiplyFactor) {
        if (loadBatchMultiplyFactor < 1)
            throw new io.nop.api.core.exceptions.NopException(
                    io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_FACTOR_INVALID)
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_NAME, "loadBatchMultiplyFactor")
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE, loadBatchMultiplyFactor);
        this.loadBatchMultiplyFactor = loadBatchMultiplyFactor;
    }

    public int getMaxLockQueueCountPerThread() {
        return maxLockQueueCountPerThread;
    }

    /** 1.7-C8-009（plan 2546 W-10）：maxLock 必须 >=1。 */
    public void setMaxLockQueueCountPerThread(int maxLockQueueCountPerThread) {
        if (maxLockQueueCountPerThread < 1)
            throw new io.nop.api.core.exceptions.NopException(
                    io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_FACTOR_INVALID)
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_NAME, "maxLockQueueCountPerThread")
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE, maxLockQueueCountPerThread);
        this.maxLockQueueCountPerThread = maxLockQueueCountPerThread;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * loadBatchMultiplyFactor,
                item -> partitionFn.apply(item, context), 0);
        queue.setMaxLockQueueCountPerThread(maxLockQueueCountPerThread);

        context.onAfterComplete(err -> {
            queue.finish();
        });

        IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
            MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex(), () -> loader.load(loadBatchSize, ctx));
            if (map == null) {
                return Collections.emptyList();
            }

            ctx.onAfterComplete(error -> {
                queue.completeBatch(map, ctx.getThreadIndex());
            });

            List<S> ret = new ArrayList<>(batchSize);
            map.forEachEntry((list, index) -> {
                ret.addAll(list);
            });
            return ret;
        };

        return resultLoader;
    }
}