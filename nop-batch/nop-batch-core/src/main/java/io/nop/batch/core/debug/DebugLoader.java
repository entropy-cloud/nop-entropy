/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.debug;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugLoader implements IBatchLoader<String, IBatchChunkContext>, IBatchTaskListener {
    private int maxCount;
    private int readCount;

    public DebugLoader() {
    }

    public DebugLoader(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        readCount = 0;
    }

    @Override
    public List<String> load(int batchSize, IBatchChunkContext context) {
        if (readCount >= maxCount)
            return Collections.emptyList();

        List<String> ret = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            ret.add(String.valueOf(readCount++));
        }
        return ret;
    }
}
