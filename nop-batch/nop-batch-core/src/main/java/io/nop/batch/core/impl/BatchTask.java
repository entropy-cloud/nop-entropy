/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.impl;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkListener;
import io.nop.batch.core.IBatchChunkProcessor;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;

public class BatchTask implements IBatchTask {
    static final Logger LOG = LoggerFactory.getLogger(BatchTask.class);

    private final Executor executor;
    private final IBatchChunkProcessor chunkProcessor;
    private final IBatchChunkListener chunkListener;
    private final IBatchTaskListener taskListener;
    private final IBatchStateStore stateStore;
    private final int concurrency;

    public BatchTask(Executor executor, int concurrency, IBatchChunkProcessor chunkProcessor,
                     IBatchChunkListener chunkListener, IBatchTaskListener taskListener, IBatchStateStore stateStore) {
        this.executor = executor;
        this.chunkProcessor = chunkProcessor;
        this.chunkListener = chunkListener;
        this.taskListener = taskListener;
        this.stateStore = stateStore;
        this.concurrency = concurrency;
    }

    @Override
    public CompletableFuture<Void> executeAsync(IBatchTaskContext context) {
        IBatchTaskMetrics metrics = context.getMetrics();
        Object meter = metrics == null ? null : metrics.beginTask();

        if (stateStore != null) {
            stateStore.loadTaskState(context);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        if (taskListener != null) {
            try {
                taskListener.onTaskBegin(context);
            } catch (Exception e) {
                // onTaskBegin中有可能分配资源，必须调用onTaskEnd来释放资源
                onTaskComplete(future, meter, e, context);
                return future;
            }
        }

        // 多个线程可以并发执行。loader/processor/consumer都需要是线程安全的
        CompletableFuture[] futures = new CompletableFuture[concurrency];
        for (int i = 0; i < concurrency; i++) {
            futures[i] = executeChunkLoop(context, i);
        }

        CompletableFuture.allOf(futures).whenComplete((ret, err) -> {
            onTaskComplete(future, meter, err, context);
        });

        return future;
    }

    void onTaskComplete(CompletableFuture<Void> future, Object meter, Throwable err, IBatchTaskContext context) {
        IBatchTaskMetrics metrics = context.getMetrics();

        try {
            if (err == null) {
                try {
                    context.awaitAsyncResults();
                    context.fireBeforeComplete(null);
                    if (taskListener != null) {
                        taskListener.onTaskEnd(null, context);
                    }

                    // 任务执行完毕之后保存状态到数据库中，然后再触发context.complete()函数通知外部任务完成
                    if (stateStore != null) {
                        stateStore.saveTaskState(context);
                    }

                    context.complete();
                } catch (Exception e) {
                    err = e;
                }
            }
            if (err != null) {
                try {
                    context.cancelAsyncResults();
                    context.fireBeforeComplete(err);

                    if (taskListener != null) {
                        taskListener.onTaskEnd(err, context);
                    }

                    // 任务执行完毕之后保存状态到数据库中，然后再触发context.complete()函数通知外部任务完成
                    if (stateStore != null) {
                        stateStore.saveTaskState(context);
                    }
                } finally {
                    context.completeExceptionally(err);
                }
            }
        } finally {
            if (metrics != null)
                metrics.endTask(meter, err == null);

            FutureHelper.complete(future, null, err);
        }
    }

    CompletableFuture<Void> executeChunkLoop(IBatchTaskContext context, int threadIndex) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executor.execute(() -> {
            try {
                do {
                    if (context.isCancelled())
                        throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

                    if (processChunk(context, threadIndex) != ProcessResult.CONTINUE)
                        break;

                } while (true);

                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * 读取并处理一个chunk, 返回STOP表示已经读取完毕
     */
    protected ProcessResult processChunk(IBatchTaskContext context, int threadIndex) {
        IBatchChunkContext chunkContext = context.newChunkContext();
        chunkContext.setConcurrency(concurrency);
        chunkContext.setThreadIndex(threadIndex);

        IBatchTaskMetrics metrics = context.getMetrics();

        Object meter = metrics == null ? null : metrics.beginChunk();

        long beginTime = CoreMetrics.currentTimeMillis();
        LOG.info("nop.batch.process-chunk-begin:taskName={},taskId={},threadIndex={}",
                context.getTaskName(), context.getTaskId(), threadIndex);

        ProcessResult result = ProcessResult.CONTINUE;
        boolean success = true;
        try {
            if (stateStore != null)
                stateStore.loadChunkState(chunkContext);

            if (chunkListener != null) {
                chunkListener.onChunkBegin(chunkContext);
            }

            result = chunkProcessor.process(chunkContext);

            chunkContext.awaitAsyncResults();
            chunkContext.fireBeforeComplete(null);

            if (chunkListener != null)
                chunkListener.onChunkEnd(null, chunkContext);

            if (stateStore != null)
                stateStore.saveTaskState(context);

            chunkContext.complete();

        } catch (Throwable e) {
            success = false;
            LOG.error("nop.err.batch.task-chunk-fail:taskName={},taskId={},threadIndex={}",
                    context.getTaskName(), context.getTaskId(),threadIndex,
                    e);

            try {
                chunkContext.cancelAsyncResults();
                chunkContext.fireBeforeComplete(e);

                if (chunkListener != null) {
                    chunkListener.onChunkEnd(e, chunkContext);
                }

                if (stateStore != null)
                    stateStore.saveTaskState(context);
            } finally {
                chunkContext.completeExceptionally(e);
            }
        } finally {
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.batch.process-chunk-end:taskName={},taskId={},threadIndex={},usedTime={}", context.getTaskName(),
                    context.getTaskId(), threadIndex, endTime - beginTime);

            if (metrics != null)
                metrics.endChunk(meter, success);
        }

        return result;
    }
}