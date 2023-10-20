/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchLoader;

import java.util.Comparator;
import java.util.List;

public class ChunkSortBatchLoader<S, C> implements IBatchLoader<S, C> {
    private final Comparator<S> comparator;
    private final IBatchLoader<S, C> loader;

    public ChunkSortBatchLoader(Comparator<S> comparator, IBatchLoader<S, C> loader) {
        this.comparator = comparator;
        this.loader = loader;
    }

    @Override
    public List<S> load(int batchSize, C context) {
        List<S> list = loader.load(batchSize, context);
        if (!list.isEmpty()) {
            list.sort(comparator);
        }
        return list;
    }
}
