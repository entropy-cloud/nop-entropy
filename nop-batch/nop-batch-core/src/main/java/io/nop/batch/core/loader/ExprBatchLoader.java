/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;

import java.util.List;

public class ExprBatchLoader<S>
        implements IBatchLoader<S, IBatchChunkContext>, IBatchTaskListener {
    private final IEvalAction action;

    private IBatchLoader<S, IBatchChunkContext> loader;

    public ExprBatchLoader(IEvalAction action) {
        this.action = Guard.notNull(action, "action");
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        List<S> list = CollectionHelper.toList(action.invoke(context));
        this.loader = new ListBatchLoader<>(list);
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        this.loader = null;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        return loader.load(batchSize, context);
    }
}
