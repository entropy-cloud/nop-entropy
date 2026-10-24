package io.nop.batch.core;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import static io.nop.batch.core.BatchErrors.ARG_MAX_CONCURRENCY_CAP;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_NAME;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CONCURRENCY_EXCEEDS_CAP;

@DataBean
public class BatchDispatchConfig<S> {
    private int loadBatchSize;
    private int fetchThreadCount;
    private BiFunction<S, IBatchTaskContext, Integer> partitionFn;
    private Executor executor;

    public int getLoadBatchSize() {
        return loadBatchSize;
    }

    public void setLoadBatchSize(int loadBatchSize) {
        this.loadBatchSize = loadBatchSize;
    }

    public int getFetchThreadCount() {
        return fetchThreadCount;
    }

    /**
     * fetchThreadCount 装配期护栏（1.7-C8-003，plan 2498 Phase 5）：超界类型化 fail-fast。
     * DSL 装配（ModelBasedBatchTaskBuilderFactory.buildDispatchConfig）与程序化装配共同经过本 setter，
     * 超界配置在任务装配期即报错，不会进入 AsyncFetch 线程创建路径。
     */
    public void setFetchThreadCount(int fetchThreadCount) {
        int cap = BatchConfigs.CFG_BATCH_MAX_CONCURRENCY_CAP.get();
        if (fetchThreadCount > cap) {
            throw new NopException(ERR_BATCH_CONCURRENCY_EXCEEDS_CAP)
                    .param(ARG_PARAM_NAME, "fetchThreadCount")
                    .param(ARG_PARAM_VALUE, fetchThreadCount)
                    .param(ARG_MAX_CONCURRENCY_CAP, cap);
        }
        this.fetchThreadCount = fetchThreadCount;
    }

    public BiFunction<S, IBatchTaskContext, Integer> getPartitionFn() {
        return partitionFn;
    }

    public void setPartitionFn(BiFunction<S, IBatchTaskContext, Integer> partitionFn) {
        this.partitionFn = partitionFn;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

}
