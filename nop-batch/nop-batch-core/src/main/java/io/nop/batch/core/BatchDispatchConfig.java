package io.nop.batch.core;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@DataBean
public class BatchDispatchConfig<S> {
    private int loadBatchSize;
    private int fetchThreadCount;
    private BiFunction<S, IBatchTaskContext, Integer> partitionFn;
    private Executor executor;
    private BiConsumer<List<S>, IBatchChunkContext> beforeDispatch;

    public int getLoadBatchSize() {
        return loadBatchSize;
    }

    public void setLoadBatchSize(int loadBatchSize) {
        this.loadBatchSize = loadBatchSize;
    }

    public int getFetchThreadCount() {
        return fetchThreadCount;
    }

    public void setFetchThreadCount(int fetchThreadCount) {
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

    public BiConsumer<List<S>, IBatchChunkContext> getBeforeDispatch() {
        return beforeDispatch;
    }

    public void setBeforeDispatch(BiConsumer<List<S>, IBatchChunkContext> beforeDispatch) {
        this.beforeDispatch = beforeDispatch;
    }
}
