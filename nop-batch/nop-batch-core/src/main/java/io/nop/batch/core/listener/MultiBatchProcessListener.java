/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchProcessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class MultiBatchProcessListener<S, R, C> implements IBatchProcessListener<S, R, C> {
    final static Logger LOG = LoggerFactory.getLogger(MultiBatchProcessListener.class);

    private final List<IBatchProcessListener<S, R, C>> list;

    public MultiBatchProcessListener(List<IBatchProcessListener<S, R, C>> list) {
        this.list = list;
    }

    @Override
    public void onProcessBegin(S item, Consumer<R> consumer, C context) {
        for (IBatchProcessListener<S, R, C> listener : list) {
            listener.onProcessBegin(item, consumer, context);
        }
    }

    @Override
    public void onProcessEnd(Throwable exception, S item, Consumer<R> consumer, C context) {
        for (int i = list.size() - 1; i >= 0; i--) {
            IBatchProcessListener<S, R, C> listener = list.get(i);
            try {
                listener.onProcessEnd(exception, item, consumer, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.handle-process-end-fail", e);
            }
        }
    }
}
