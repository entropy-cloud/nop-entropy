/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.nop.commons.collections.MapOfInt;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * 利用底层的loader读取数据，然后按照partition切分成多个顺序队列。确保一个partition的数据不会同时有两个线程在处理。
 *
 * @param <S>
 */
public class AsyncFetchPartitionDispatchLoaderProvider<S>
        implements IBatchLoaderProvider<S> {
    static final Logger LOG = LoggerFactory.getLogger(AsyncFetchPartitionDispatchLoaderProvider.class);

    private final IBatchLoaderProvider<S> loader;
    private final Executor executor;
    private final int fetchThreadCount;
    private final int loadBatchSize;
    private final BiFunction<S, IBatchTaskContext, Integer> partitionFn;

    private int loadBatchMultiplyFactor = 30;
    private int maxLockQueueCountPerThread = 100;

    /**
     * 串行化load+addBatch。多个fetch线程并发执行时，保证数据页按底层loader的源序依次进入队列，
     * 否则同一partition内的记录处理顺序会被跨页打乱
     */
    private final Object fetchMutex = new Object();

    public AsyncFetchPartitionDispatchLoaderProvider(IBatchLoaderProvider<S> loader, Executor executor, int fetchThreadCount,
                                                     int loadBatchSize, BiFunction<S, IBatchTaskContext, Integer> partitionFn) {
        this.loader = loader;
        this.executor = executor;
        this.fetchThreadCount = fetchThreadCount;
        this.loadBatchSize = loadBatchSize;
        this.partitionFn = partitionFn;
    }

    public int getLoadBatchMultiplyFactor() {
        return loadBatchMultiplyFactor;
    }

    /** 1.7-C8-009（plan 2546 W-10）：factor 必须 >=1——<=0 会使队列容量退化为 0 导致挂死。 */
    public void setLoadBatchMultiplyFactor(int loadBatchMultiplyFactor) {
        if (loadBatchMultiplyFactor < 1)
            throw new io.nop.api.core.exceptions.NopException(
                    io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_FACTOR_INVALID)
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_NAME, "loadBatchMultiplyFactor")
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE, loadBatchMultiplyFactor);
        this.loadBatchMultiplyFactor = loadBatchMultiplyFactor;
    }

    public int getMaxLockQueueCountPerThread() {
        return maxLockQueueCountPerThread;
    }

    /** 1.7-C8-009（plan 2546 W-10）：maxLock 必须 >=1。 */
    public void setMaxLockQueueCountPerThread(int maxLockQueueCountPerThread) {
        if (maxLockQueueCountPerThread < 1)
            throw new io.nop.api.core.exceptions.NopException(
                    io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_FACTOR_INVALID)
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_NAME, "maxLockQueueCountPerThread")
                    .param(io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE, maxLockQueueCountPerThread);
        this.maxLockQueueCountPerThread = maxLockQueueCountPerThread;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        PartitionDispatchQueue<S> queue = new PartitionDispatchQueue<>(loadBatchSize * loadBatchMultiplyFactor,
                item -> partitionFn.apply(item, context), fetchThreadCount);
        queue.setMaxLockQueueCountPerThread(maxLockQueueCountPerThread);

        AtomicReference<Exception> exception = new AtomicReference<>();

        // 1.7-C8-008（plan 2546 W-09）：积压/许可水位 Gauge。meter id 必须**唯一**——
        // Tag.of 对 null 值 NPE（无 stateStore 的任务 taskId 为 null），且 Micrometer 对同 name+tags
        // 返回已存在的 Gauge（同名并发会串读）→ 用 exec.id 三级回退 + 精确注销。
        List<Gauge> gauges = registerWatermarkGauges(context, queue);

        context.onAfterComplete(err -> {
            queue.finish();
            // 注销：否则 meter-id 复用会让后续任务读到已结束队列的陈旧 0，且逐任务累积。
            io.micrometer.core.instrument.MeterRegistry registry =
                    io.nop.commons.metrics.GlobalMeterRegistry.instance();
            for (Gauge gauge : gauges) {
                try {
                    registry.remove(gauge);
                } catch (Exception e) {
                    LOG.warn("nop.warn.batch.dispatch-gauge-remove-fail", e);
                }
            }
        });

        IBatchLoader<S> resultLoader = (batchSize, ctx) -> {
            Exception err = exception.get();
            if (err != null)
                throw NopException.adapt(err);

            MapOfInt<List<S>> map = queue.takeBatch(batchSize, ctx.getThreadIndex(), null);
            if (map == null) {
                // takeBatch返回null表示不再有数据。此时fetch线程可能是因为异常而退出，
                // 需要再次检查异常，避免加载失败被静默吞掉、任务以0条记录"成功"结束
                err = exception.get();
                if (err != null)
                    throw NopException.adapt(err);
                return Collections.emptyList();
            }

            ctx.onAfterComplete(error -> {
                queue.completeBatch(map, ctx.getThreadIndex());
            });

            List<S> ret = new ArrayList<>(batchSize);
            map.forEachEntry((list, index) -> {
                ret.addAll(list);
            });
            return ret;
        };

        for (int i = 0; i < fetchThreadCount; i++) {
            final int threadIndex = i;
            executor.execute(() -> {
                try {
                    while (!context.isCancelled() && !queue.isFinished()) {
                        IBatchChunkContext ctx = context.newChunkContext();
                        ctx.setConcurrency(fetchThreadCount);
                        ctx.setThreadIndex(threadIndex);
                        try {
                            // load与addBatch在同一临界区内完成，保证页按源序入队
                            synchronized (fetchMutex) {
                                List<S> list = loader.load(loadBatchSize, ctx);
                                if (list.isEmpty()) {
                                    queue.markNoMorData();
                                    LOG.info("nop.batch.exit-fetch-thread:threadIndex={}", threadIndex);
                                    return;
                                }
                                queue.addBatch(list);
                            }
                            // 1.7-C6-008（plan 2545 W-07；closure audit B-1 修正）：这里**不得**对 fetch 线程
                            // 自建的一次性 ctx 调用 complete()。底层 loader（如 ResourceRecordLoaderProvider）
                            // 在本 ctx 上注册的 chunk 压实回调依赖 ctx.getChunkItems()，而 chunkItems 仅由
                            // 消费线程的 BatchChunkProcessor.loadItems 写入；fetch ctx 的 chunkItems 恒为 null，
                            // 调用 complete() 只会触发一个读不到 items 的空转压实（no-op）——回调看似执行、
                            // completedIndex 仍不推进，且掩盖了真实语义缺口。若将来为该 ctx 填入 chunkItems，
                            // 又会在数据尚未消费时就推进 completedIndex，把 at-least-once 降级为 at-most-once。
                            // 因此该组合（saveState + fetchThreadCount>0）在装配期即 fail-fast
                            // （见 BatchTaskBuilder.buildLoader），本循环不承担压实职责。
                        } catch (Exception e) {
                            LOG.error("nop.batch.exit-fetch-thread-when-fail:threadIndex={}", threadIndex, e);
                            // 保留第一个异常作为根因，避免被后续线程的次要异常覆盖
                            exception.compareAndSet(null, e);
                            return;
                        }
                    }
                } finally {
                    queue.exitFetchThread();
                }
            });
        }

        return resultLoader;
    }

    /**
     * 1.7-C8-008（plan 2546 W-09）：注册积压与可用许可 Gauge。
     * <p>
     * tags = {@code task.name} + {@code exec.id}；{@code exec.id} 取 taskId（非空）→ taskKey（非空）
     * → {@code "anon-"+identityHashCode(queue)}，保证 meter id 唯一且不触发 {@code Tag.of} 的
     * null 校验失败（无 stateStore 的 dispatch 任务 taskId 为 null）。
     */
    private List<Gauge> registerWatermarkGauges(IBatchTaskContext context, PartitionDispatchQueue<S> queue) {
        String taskName = context.getTaskName() == null ? "default" : context.getTaskName();
        String execId = context.getTaskId();
        if (StringHelper.isEmpty(execId))
            execId = context.getTaskKey();
        if (StringHelper.isEmpty(execId))
            execId = "anon-" + System.identityHashCode(queue);

        MeterRegistry registry = GlobalMeterRegistry.instance();
        List<Tag> tags = Arrays.asList(Tag.of("task.name", taskName), Tag.of("exec.id", execId));
        int capacity = queue.getCapacity();

        Gauge backlog = Gauge.builder("nop.batch.dispatch.backlog", queue, PartitionDispatchQueue::getCount)
                .description("Pending (not yet dispatched) records in the partition dispatch queue; capacity=" + capacity)
                .tags(tags)
                .register(registry);
        Gauge available = Gauge.builder("nop.batch.dispatch.available-permits", queue,
                        PartitionDispatchQueue::getAvailablePermits)
                .description("Available semaphore permits (backpressure headroom); capacity=" + capacity)
                .tags(tags)
                .register(registry);
        return Arrays.asList(backlog, available);
    }
}
