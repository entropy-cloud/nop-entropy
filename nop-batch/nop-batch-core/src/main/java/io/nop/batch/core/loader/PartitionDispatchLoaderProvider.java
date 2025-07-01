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

    public PartitionDispatchLoaderProvider(IBatchLoaderProvider<S> loader,
                                           int loadBatchSize, BiFunction<S, IBatchTaskContext, Integer> partitionFn) {
        this.loader = loader;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = Guard.notNull(partitionFn, "partitionFn");
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * 20, item -> partitionFn.apply(item, context), 0);

        context.onAfterComplete(err -> {
            queue.finish();
        });

        IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
            MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex(), () -> loader.load(batchSize, ctx));
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