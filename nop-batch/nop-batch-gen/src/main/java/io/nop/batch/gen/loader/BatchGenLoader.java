/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.loader;

import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.common.AbstractBatchResourceHandler;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.generator.BatchGenContextImpl;
import io.nop.batch.gen.generator.BatchGenState;
import io.nop.batch.gen.model.BatchGenModel;
import io.nop.batch.gen.model.BatchGenModelParser;

import java.util.ArrayList;
import java.util.List;

public class BatchGenLoader<C> extends AbstractBatchResourceHandler
        implements IBatchLoader<Object, C>, IBatchTaskListener {

    private BatchGenModel genModel;
    private BatchGenState genState;

    /**
     * 总共生成多少条记录
     */
    private long totalCount;

    private IBatchGenContext genContext;

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    @Override
    public synchronized void onTaskBegin(IBatchTaskContext context) {
        genModel = new BatchGenModelParser().parseFromResource(getResource(context));
        genState = new BatchGenState(genModel, totalCount);
        genContext = new BatchGenContextImpl();
    }

    @Override
    public synchronized void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        genModel = null;
        genState = null;
        genContext = null;
    }

    @Override
    public synchronized List<Object> load(int batchSize, C context) {
        List<Object> ret = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            if (!genState.hasNext())
                break;

            Object item = genState.next(genContext, true);
            ret.add(item);
        }
        return ret;
    }
}