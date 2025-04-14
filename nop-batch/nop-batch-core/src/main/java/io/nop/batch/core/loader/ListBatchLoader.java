/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListBatchLoader<S, C> implements IBatchLoaderProvider.IBatchLoader<S> {
    private final List<S> list;

    private int offset = 0;

    public ListBatchLoader(List<S> list) {
        this.list = list == null ? Collections.emptyList() : list;
    }


    @Override
    public synchronized List<S> load(int batchSize, IBatchChunkContext context) {
        if (offset >= list.size())
            return Collections.emptyList();

        int n = Math.min(list.size() - offset, batchSize);
        List<S> ret = new ArrayList<>(list.subList(offset, n));
        offset += n;
        return ret;
    }
}
