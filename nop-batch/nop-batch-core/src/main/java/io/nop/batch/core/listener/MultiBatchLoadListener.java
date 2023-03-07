/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchLoadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MultiBatchLoadListener<S, C> implements IBatchLoadListener<S, C> {
    final static Logger LOG = LoggerFactory.getLogger(MultiBatchConsumerListener.class);

    private final List<IBatchLoadListener<S, C>> list;

    public MultiBatchLoadListener(List<IBatchLoadListener<S, C>> list) {
        this.list = list;
    }

    @Override
    public void onLoadBegin(int batchSize, C context) {
        for (IBatchLoadListener<S, C> listener : list) {
            try {
                listener.onLoadBegin(batchSize, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.handle-load-begin-fail", e);
            }
        }
    }

    @Override
    public void onLoadEnd(Throwable exception, List<S> items, int batchSize, C context) {
        for (IBatchLoadListener<S, C> listener : list) {
            try {
                listener.onLoadEnd(exception, items, batchSize, context);
            } catch (Exception e) {
                LOG.error("nop.err.batch.handle-load-end-fail", e);
            }
        }
    }
}
