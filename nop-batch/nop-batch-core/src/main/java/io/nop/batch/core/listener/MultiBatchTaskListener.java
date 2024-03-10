/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MultiBatchTaskListener implements IBatchTaskListener {
    static final Logger LOG = LoggerFactory.getLogger(MultiBatchTaskListener.class);

    private final List<IBatchTaskListener> list;

    public MultiBatchTaskListener(List<IBatchTaskListener> list) {
        this.list = list;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        for (IBatchTaskListener listener : list) {
            listener.onTaskBegin(context);
        }
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        for (int i = list.size() - 1; i >= 0; i--) {
            IBatchTaskListener listener = list.get(i);
            try {
                listener.onTaskEnd(exception, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.handle-task-end-fail", e);
            }
        }
    }
}
