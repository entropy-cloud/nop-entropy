/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.debug;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugBatchLoader implements IBatchLoader<String> {
    private int maxCount;
    private int readCount;

    public DebugBatchLoader() {
    }

    public DebugBatchLoader(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public synchronized List<String> load(int batchSize, IBatchChunkContext context) {
        if (readCount >= maxCount)
            return Collections.emptyList();

        List<String> ret = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            ret.add(String.valueOf(readCount++));
        }
        return ret;
    }
}
