/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchConsumeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MultiBatchConsumeListener<R, C> implements IBatchConsumeListener<R, C> {
    final static Logger LOG = LoggerFactory.getLogger(MultiBatchConsumeListener.class);

    private final List<IBatchConsumeListener<R, C>> list;

    public MultiBatchConsumeListener(List<IBatchConsumeListener<R, C>> list) {
        this.list = list;
    }

    @Override
    public void onConsumeBegin(List<R> items, C context) {
        for (IBatchConsumeListener<R, C> listener : list) {
            listener.onConsumeBegin(items, context);
        }
    }

    @Override
    public void onConsumeEnd(Throwable exception, List<R> items, C context) {
        for (int i = list.size() - 1; i >= 0; i--) {
            IBatchConsumeListener<R, C> listener = list.get(i);
            try {
                listener.onConsumeEnd(exception, items, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.on-consume-end-fail", e);
            }
        }
    }
}
