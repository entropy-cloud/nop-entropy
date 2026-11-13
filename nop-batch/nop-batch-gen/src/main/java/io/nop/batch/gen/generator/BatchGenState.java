/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.generator;

import io.nop.api.core.beans.TreeBean;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.model.IBatchGenCaseModel;

import java.util.List;
import java.util.Map;

public class BatchGenState {
    private final IBatchGenCaseModel model;
    private final long totalCount;

    private final List<BatchGenState> subCases;

    /**
     * 目前已经生成了多少条记录
     */
    private long genCount;

    /**
     * 正在生成subCases中哪个分支的记录
     */
    private int subIndex;

    public BatchGenState(IBatchGenCaseModel model, long totalCount) {
        this.model = model;
        this.totalCount = totalCount;
        this.subCases = model.isSequential() ? BatchGenStateBuilder.buildSeqStates(model.getSubCases(), totalCount)
                : BatchGenStateBuilder.buildSubStates(model.getSubCases(), totalCount);
    }

    public Map<String, Object> getOutputVars() {
        return model.getOutputVars();
    }

    /**
     * 1.7-C1-002（plan 2545 W-02）：游标载体为共享可变状态，consume 阶段由多 chunk 线程
     * 在 LoaderState 锁外并发驱动（sequential + useBatchRequestGenerator + concurrency>1）。
     * 全部读写收进本对象监视器——粒度即游标载体本身，天然正确。
     */
    public synchronized boolean hasNext() {
        return genCount < totalCount;
    }

    public synchronized long getGenCount() {
        return genCount;
    }

    public synchronized int getSubIndex() {
        return subIndex;
    }

    public TreeBean getWhen() {
        return model.getWhen();
    }

    public boolean isSequential() {
        return model.isSequential();
    }

    public synchronized Object next(IBatchGenContext context, boolean produce) {
        if (!hasNext())
            throw new IllegalStateException("nop.err.batch.generator-already-finished");

        genCount++;

        if (isSequential())
            return !produce ? null : new SequentialBatchRequestGenerator<>(subCases, context);

        if (subCases.size() > 0) {
            if (!subHasNext()) {
                throw new IllegalStateException("nop.err.batch.generator-already-finished");
            }

            BatchGenState state = subCases.get(subIndex);
            return state.next(context, produce);
        }

        if (!produce)
            return null;

        return context.getProducer().produce(model.getMergedTemplate(), model.getBeanType(), context);
    }

    private boolean subHasNext() {
        if (subIndex >= subCases.size())
            return false;

        do {
            BatchGenState state = subCases.get(subIndex);
            if (state.hasNext())
                return true;
            subIndex++;
        } while (subIndex < subCases.size());

        return false;
    }
}