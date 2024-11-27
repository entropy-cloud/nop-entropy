/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.BatchTaskGlobals;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessor;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;

public class BatchTaskExecution implements IBatchTask {
    static final Logger LOG = LoggerFactory.getLogger(BatchTaskExecution.class);

    private final String taskName;
    private final long taskVersion;
    private final IEvalFunction taskKeyExpr;
    private final Executor executor;
    private final IBatchChunkProcessor chunkProcessor;
    private final IBatchStateStore stateStore;
    private final List<Consumer<IBatchTaskContext>> initializers;
    private final int concurrency;

    public BatchTaskExecution(String taskName, long taskVersion,
                              IEvalFunction taskKeyExpr, Executor executor, int concurrency,
                              List<Consumer<IBatchTaskContext>> initializers,
                              IBatchChunkProcessor chunkProcessor, IBatchStateStore stateStore) {
        this.taskName = taskName;
        this.taskVersion = taskVersion;
        this.taskKeyExpr = taskKeyExpr;
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

        BatchTaskGlobals.provideTaskContext(context);
        try {
            // 如果context中已经设置，则以context中的值为准
            if (context.getTaskName() == null)
                context.setTaskName(taskName);
            if (context.getTaskVersion() == null)
                context.setTaskVersion(taskVersion);

            initTaskKey(context);

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

        } finally {
            BatchTaskGlobals.removeTaskContext();
        }
    }

    void initTaskKey(IBatchTaskContext context) {
        String taskKey = context.getTaskKey();
        if (taskKeyExpr != null && StringHelper.isEmpty(taskKey)) {
            taskKey = ConvertHelper.toString(taskKeyExpr.call1(null, context, context.getEvalScope()));
        }
        if (StringHelper.isEmpty(taskKey))
            taskKey = StringHelper.generateUUID();
        context.setTaskKey(taskKey);
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
            BatchTaskGlobals.provideTaskContext(context);
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
            } finally {
                BatchTaskGlobals.removeTaskContext();
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
        LOG.info("nop.batch.process-chunk-begin:taskName={},taskId={},taskKey={},threadIndex={}",
                context.getTaskName(), context.getTaskId(), context.getTaskKey(), threadIndex);

        boolean syncCount = false;
        ProcessResult result;
        boolean success = true;
        try {
            chunkContext.getTaskContext().fireChunkBegin(chunkContext);

            result = chunkProcessor.process(chunkContext);

            chunkContext.fireBeforeComplete();

            chunkContext.getTaskContext().fireBeforeChunkEnd(chunkContext);

            context.incCompleteItemCount(chunkContext.getCompletedItemCount());
            syncCount = true;

            if (stateStore != null)
                stateStore.saveTaskState(false, null, context);

            chunkContext.complete();
            chunkContext.getTaskContext().fireChunkEnd(null, chunkContext);

        } catch (Exception e) {
            success = false;
            LOG.error("nop.err.batch.task-chunk-fail:taskName={},taskId={},taskKey={},threadIndex={}",
                    context.getTaskName(), context.getTaskId(), context.getTaskKey(), threadIndex,
                    e);

            if (!syncCount)
                context.incCompleteItemCount(chunkContext.getCompletedItemCount());

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
            LOG.info("nop.batch.process-chunk-end:taskName={},taskId={},taskKey={},threadIndex={},usedTime={}", context.getTaskName(),
                    context.getTaskId(), context.getTaskKey(), threadIndex, endTime - beginTime);

            if (metrics != null)
                metrics.endChunk(meter, success);
        }

        return result;
    }
}