/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchLoadListener;
import io.nop.batch.core.IBatchLoader;

import java.util.List;

public class BatchLoaderWithListener<S, C> implements IBatchLoader<S, C> {
    private final IBatchLoader<S, C> loader;
    private final IBatchLoadListener<S, C> listener;

    public BatchLoaderWithListener(IBatchLoader<S, C> loader, IBatchLoadListener<S, C> listener) {
        this.loader = loader;
        this.listener = listener;
    }

    @Override
    public List<S> load(int batchSize, C context) {
        listener.onLoadBegin(batchSize, context);

        Throwable exception = null;
        List<S> items = null;
        try {
            items = loader.load(batchSize, context);
            return items;
        } catch (Exception e) {
            exception = e;
            throw NopException.adapt(e);
        } finally {
            listener.onLoadEnd(exception, items, batchSize, context);
        }
    }
}