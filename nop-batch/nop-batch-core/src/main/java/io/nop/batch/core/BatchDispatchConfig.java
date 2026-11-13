package io.nop.batch.core;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import static io.nop.batch.core.BatchErrors.ARG_MAX_CONCURRENCY_CAP;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_FACTOR_INVALID;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_NAME;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CONCURRENCY_EXCEEDS_CAP;

@DataBean
public class BatchDispatchConfig<S> {
    private int loadBatchSize;
    private int fetchThreadCount;
    private BiFunction<S, IBatchTaskContext, Integer> partitionFn;
    private Executor executor;

    /**
     * 1.7-C8-009（plan 2546 W-10）：dispatch 队列容量倍数。0 = 未配置（保留 Provider 缺省 30）。
     * 负值在 setter 内 fail-fast——原实现公共 setter 全仓零调用方（幽灵旋钮），而 factor<=0
     * 会使队列容量退化为 0 导致永久挂死。
     */
    private int loadBatchMultiplyFactor;

    /** 1.7-C8-009（plan 2546 W-10）：单次 take 最大认领分区数。0 = 未配置（保留缺省 100）。 */
    private int maxLockQueueCountPerThread;

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

    public int getLoadBatchMultiplyFactor() {
        return loadBatchMultiplyFactor;
    }

    public void setLoadBatchMultiplyFactor(int loadBatchMultiplyFactor) {
        checkDispatchFactor("loadBatchMultiplyFactor", loadBatchMultiplyFactor);
        this.loadBatchMultiplyFactor = loadBatchMultiplyFactor;
    }

    public int getMaxLockQueueCountPerThread() {
        return maxLockQueueCountPerThread;
    }

    public void setMaxLockQueueCountPerThread(int maxLockQueueCountPerThread) {
        checkDispatchFactor("maxLockQueueCountPerThread", maxLockQueueCountPerThread);
        this.maxLockQueueCountPerThread = maxLockQueueCountPerThread;
    }

    /**
     * 1.7-C8-009：0 = 未配置哨兵（保留 Provider 缺省）；负值 = 配置错误（fail-fast，不静默忽略）。
     */
    private static void checkDispatchFactor(String name, int value) {
        if (value < 0)
            throw new NopException(ERR_BATCH_DISPATCH_FACTOR_INVALID)
                    .param(ARG_PARAM_NAME, name)
                    .param(ARG_PARAM_VALUE, value);
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
