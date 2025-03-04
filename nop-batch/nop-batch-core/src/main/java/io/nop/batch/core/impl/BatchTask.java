/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.BatchTaskGlobals;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessorProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;

public class BatchTask<S> implements IBatchTask {
    static final Logger LOG = LoggerFactory.getLogger(BatchTask.class);

    private final String taskName;
    private final long taskVersion;
    private final IEvalFunction taskKeyExpr;
    private final Executor executor;
    private final IBatchLoaderProvider<S> loaderProvider;
    private final IBatchChunkProcessorProvider<S> chunkProcessorProvider;
    private final IBatchStateStore stateStore;
    private final List<Consumer<IBatchTaskContext>> initializers;
    private final int concurrency;

    private final Boolean allowStartIfComplete;
    private final int startLimit;

    public BatchTask(String taskName, long taskVersion,
                     IEvalFunction taskKeyExpr, Executor executor, int concurrency,
                     List<Consumer<IBatchTaskContext>> initializers,
                     IBatchLoaderProvider<S> loaderProvider,
                     IBatchChunkProcessorProvider<S> chunkProcessorProvider, IBatchStateStore stateStore,
                     Boolean allowStartIfComplete, int startLimit) {
        this.taskName = taskName;
        this.taskVersion = taskVersion;
        this.taskKeyExpr = taskKeyExpr;
        this.initializers = initializers;
        this.loaderProvider = loaderProvider;
        this.chunkProcessorProvider = chunkProcessorProvider;
        this.stateStore = stateStore;
        this.concurrency = concurrency <= 0 ? 1 : concurrency;
        this.allowStartIfComplete = allowStartIfComplete;
        this.startLimit = startLimit;
        this.executor = getExecutor(executor, concurrency);
    }

    static Executor getExecutor(Executor executor, int concurrency) {
        if (executor != null)
            return executor;
        if (concurrency > 0) {
            return GlobalExecutors.cachedThreadPool();
        } else {
            return GlobalExecutors.syncExecutor();
        }
    }

    @Override
    public CompletableFuture<Void> executeAsync(IBatchTaskContext context) {
        // 如果context上尚未设置限制，则设置，否则以外部设置的为准
        if (context.getAllowStartIfComplete() == null && allowStartIfComplete != null)
            context.setAllowStartIfComplete(allowStartIfComplete);

        if (context.getStartLimit() <= 0)
            context.setStartLimit(startLimit);

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

            IBatchLoaderProvider.IBatchLoader<S> loader;
            IBatchChunkProcessorProvider.IBatchChunkProcessor<S> chunkProcessor;
            try {
                if (initializers != null)
                    initializers.forEach(initializer -> {
                        initializer.accept(context);
                    });

                loader = loaderProvider.setup(context);
                chunkProcessor = chunkProcessorProvider.setup(loader, context);

                context.fireTaskBegin();
            } catch (Exception e) {
                // onTaskBegin中有可能分配资源，必须调用onTaskEnd来释放资源
                onTaskComplete(future, meter, e, context);
                return future;
            }

            // 多个线程可以并发执行。loader/processor/consumer都需要是线程安全的
            CompletableFuture<?>[] futures = new CompletableFuture[concurrency];
            for (int i = 0; i < concurrency; i++) {
                futures[i] = executeChunkLoop(context, i, chunkProcessor);
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

    CompletableFuture<Void> executeChunkLoop(IBatchTaskContext context, int threadIndex,
                                             IBatchChunkProcessorProvider.IBatchChunkProcessor<S> chunkProcessor) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executor.execute(() -> {
            LOG.info("nop.batch.run-chunk-loop:threadIndex={},threadName={}", threadIndex, Thread.currentThread().getName());
            ContextProvider.runWithContext(ctx -> {
                propagateContext(ctx, context);

                BatchTaskGlobals.provideTaskContext(context);
                try {
                    do {
                        if (context.isCancelled())
                            throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

                        if (processChunk(context, threadIndex, chunkProcessor) != ProcessResult.CONTINUE)
                            break;

                    } while (true);

                    future.complete(null);
                } catch (Exception e) {
                    NopException.logIfNotTraced(LOG, "nop.batch.execute-chunk-loop-fail", e);
                    future.completeExceptionally(e);
                } finally {
                    BatchTaskGlobals.removeTaskContext();
                }
                return null;
            });

        });
        return future;
    }

    protected void propagateContext(IContext ctx, IBatchTaskContext taskCtx) {
        IServiceContext svcCtx = taskCtx.getServiceContext();
        if (svcCtx == null || svcCtx.getContext() == null || svcCtx.getContext().isClosed()) {
            ctx.setTenantId(null);
            ctx.setLocale(null);
            ctx.setUserName(null);
            ctx.setUserRefNo(null);
            ctx.setUserId(null);
            ctx.setCallIp(null);
            ctx.setTimezone(null);
            ctx.setTraceId(null);
            ctx.setPropagateRpcHeaders(null);
            return;
        }

        IContext baseCtx = svcCtx.getContext();
        if (baseCtx == ctx)
            return;

        ContextProvider.propagateContext(ctx, baseCtx, true);
    }

    /**
     * 读取并处理一个chunk, 返回STOP表示已经读取完毕
     */
    protected ProcessResult processChunk(IBatchTaskContext context, int threadIndex,
                                         IBatchChunkProcessorProvider.IBatchChunkProcessor<S> chunkProcessor) {
        IBatchChunkContext chunkContext = context.newChunkContext();
        chunkContext.setConcurrency(concurrency);
        chunkContext.setThreadIndex(threadIndex);

        IBatchTaskMetrics metrics = context.getMetrics();

        Object meter = metrics == null ? null : metrics.beginChunk();

        long beginTime = CoreMetrics.currentTimeMillis();
        LOG.info("nop.batch.process-chunk-begin:taskName={},taskId={},taskKey={},threadIndex={},processCount={}.skipCount={},retryCount={},completeCount={},completedIndex={}",
                context.getTaskName(), context.getTaskId(), context.getTaskKey(), threadIndex,
                context.getProcessItemCount(), context.getSkipItemCount(), context.getRetryItemCount(),
                context.getCompleteItemCount(), context.getCompletedIndex());

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
            chunkContext.getTaskContext().fireChunkEnd(chunkContext, null);

        } catch (Exception e) {
            success = false;
            LOG.error("nop.err.batch.task-chunk-fail:taskName={},taskId={},taskKey={},threadIndex={}",
                    context.getTaskName(), context.getTaskId(), context.getTaskKey(), threadIndex,
                    e);

            if (!syncCount)
                context.incCompleteItemCount(chunkContext.getCompletedItemCount());

            try {
                chunkContext.getTaskContext().fireChunkEnd(chunkContext, e);

                if (stateStore != null)
                    stateStore.saveTaskState(false, e, context);
            } finally {
                chunkContext.completeExceptionally(e);
            }

            // chunk处理失败时退出循环
            throw e;
        } finally {
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.batch.process-chunk-end:taskName={},taskId={},taskKey={},threadIndex={},usedTime={},processCount={}.skipCount={},retryCount={},completeCount={},completedIndex={}", context.getTaskName(),
                    context.getTaskId(), context.getTaskKey(), threadIndex, endTime - beginTime,
                    context.getProcessItemCount(), context.getSkipItemCount(), context.getRetryItemCount(),
                    context.getCompleteItemCount(), context.getCompletedIndex());

            if (metrics != null)
                metrics.endChunk(meter, success);
        }

        return result;
    }
}