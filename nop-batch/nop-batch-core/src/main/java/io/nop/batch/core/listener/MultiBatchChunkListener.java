/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MultiBatchChunkListener implements IBatchChunkListener {
    static final Logger LOG = LoggerFactory.getLogger(MultiBatchChunkListener.class);

    private final List<IBatchChunkListener> list;

    public MultiBatchChunkListener(List<IBatchChunkListener> list) {
        this.list = list;
    }

    @Override
    public void onChunkBegin(IBatchChunkContext context) {
        for (IBatchChunkListener listener : list) {
            listener.onChunkBegin(context);
        }
    }

    @Override
    public void onChunkEnd(Throwable exception, IBatchChunkContext context) {
        for (int i = list.size() - 1; i >= 0; i--) {
            IBatchChunkListener listener = list.get(i);
            try {
                listener.onChunkEnd(exception, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.handle-chunk-end-fail", e);
            }
        }
    }
}