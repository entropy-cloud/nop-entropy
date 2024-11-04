/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;

import java.util.Comparator;
import java.util.List;

public class ChunkSortBatchLoader<S> implements IBatchLoader<S> {
    private final Comparator<S> comparator;
    private final IBatchLoader<S> loader;

    public ChunkSortBatchLoader(Comparator<S> comparator, IBatchLoader<S> loader) {
        this.comparator = comparator;
        this.loader = loader;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        List<S> list = loader.load(batchSize, context);
        if (list != null && !list.isEmpty()) {
            list.sort(comparator);
        }
        return list;
    }
}
