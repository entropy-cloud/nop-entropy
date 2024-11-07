/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.*;
import io.nop.batch.core.exceptions.BatchCancelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;

public class BatchTaskExecution implements IBatchTask {
    static final Logger LOG = LoggerFactory.getLogger(BatchTaskExecution.class);

    private final Executor executor;
    private final IBatchChunkProcessor chunkProcessor;
    private final IBatchStateStore stateStore;
    private final List<Consumer<IBatchTaskContext>> initializers;
    private final int concurrency;

    public BatchTaskExecution(Executor executor, int concurrency, List<Consumer<IBatchTaskContext>> initializers,
                              IBatchChunkProcessor chunkProcessor, IBatchStateStore stateStore) {

        this.executor = executor;
        this.initializers = initializers;
        this.chunkProcessor = chunkProcessor;
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

        try {
            if (initializers != null)
                initializers.forEach(initializer -> {
                    initializer.accept(context);
                });

            context.fireTaskBegin();
        } catch (Exception e) {
            // onTaskBegin中有可能分配资源，必须调用onTaskEnd来释放资源
            onTaskComplete(future, meter, e, context);
            return future;
        }

        // 多个线程可以并发执行。loader/processor/consumer都需要是线程安全的
        CompletableFuture<?>[] futures = new CompletableFuture[concurrency];
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
                    context.fireBeforeComplete();

                    // 任务执行完毕之后保存状态到数据库中，然后再触发context.complete()函数通知外部任务完成
                    if (stateStore != null) {
                        stateStore.saveTaskState(true, null, context);
                    }

                    context.complete();
                } catch (Exception e) {
                    err = e;
                }
            }
            if (err != null) {
                try {
                    // 任务执行完毕之后保存状态到数据库中，然后再触发context.complete()函数通知外部任务完成
                    if (stateStore != null) {
                        stateStore.saveTaskState(true, err, context);
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

        ProcessResult result;
        boolean success = true;
        try {
            chunkContext.getTaskContext().fireChunkBegin(chunkContext);

            result = chunkProcessor.process(chunkContext);

            chunkContext.fireBeforeComplete();

            chunkContext.getTaskContext().fireBeforeChunkEnd(chunkContext);

            if (stateStore != null)
                stateStore.saveTaskState(false, null, context);

            chunkContext.complete();
            chunkContext.getTaskContext().fireChunkEnd(null, chunkContext);

        } catch (Exception e) {
            success = false;
            LOG.error("nop.err.batch.task-chunk-fail:taskName={},taskId={},threadIndex={}",
                    context.getTaskName(), context.getTaskId(), threadIndex,
                    e);

            try {
                chunkContext.getTaskContext().fireChunkEnd(e, chunkContext);

                if (stateStore != null)
                    stateStore.saveTaskState(false, e, context);
            } finally {
                chunkContext.completeExceptionally(e);
            }

            // chunk处理失败时退出循环
            throw e;
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