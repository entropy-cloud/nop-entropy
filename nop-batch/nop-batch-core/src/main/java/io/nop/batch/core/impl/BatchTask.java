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
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.BatchConfigs;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.nop.batch.core.BatchErrors.ARG_MAX_CONCURRENCY_CAP;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_NAME;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CONCURRENCY_EXCEEDS_CAP;

public class BatchTask<S> implements IBatchTask {

    // P1-3.3-003（plan 2381 Phase 2）：首个非取消异常收集器——allOf 的异常交付受完成时序竞态影响
    // （兄弟线程循环顶的 BatchCancelException 可能抢先），CAS 保留真根因驱动 saveTaskState 终态。
    // 全仓调用点均每次新建 BatchTask 单次 executeAsync，per-execution 字段并发安全。
    final AtomicReference<Throwable> primaryErrorRef = new AtomicReference<>();
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

    /** 1.7-C7-012（plan 2546 W-06）：KILLED 撤离门（null = 保持既有拒绝）。 */
    private final Boolean allowRestartIfKilled;
    private final int startLimit;

    /**
     * 1.7-C7-012（plan 2546 W-06）：保留既有 11 参签名（3 处既有调用点零改），转调全参构造器。
     */
    public BatchTask(String taskName, long taskVersion,
                     IEvalFunction taskKeyExpr, Executor executor, int concurrency,
                     List<Consumer<IBatchTaskContext>> initializers,
                     IBatchLoaderProvider<S> loaderProvider,
                     IBatchChunkProcessorProvider<S> chunkProcessorProvider, IBatchStateStore stateStore,
                     Boolean allowStartIfComplete, int startLimit) {
        this(taskName, taskVersion, taskKeyExpr, executor, concurrency, initializers, loaderProvider,
                chunkProcessorProvider, stateStore, allowStartIfComplete, startLimit, null);
    }

    public BatchTask(String taskName, long taskVersion,
                     IEvalFunction taskKeyExpr, Executor executor, int concurrency,
                     List<Consumer<IBatchTaskContext>> initializers,
                     IBatchLoaderProvider<S> loaderProvider,
                     IBatchChunkProcessorProvider<S> chunkProcessorProvider, IBatchStateStore stateStore,
                     Boolean allowStartIfComplete, int startLimit, Boolean allowRestartIfKilled) {
        this.taskName = taskName;
        this.taskVersion = taskVersion;
        this.taskKeyExpr = taskKeyExpr;
        this.initializers = initializers;
        this.loaderProvider = loaderProvider;
        this.chunkProcessorProvider = chunkProcessorProvider;
        this.stateStore = stateStore;
        this.concurrency = normalizeConcurrency(concurrency);
        this.allowStartIfComplete = allowStartIfComplete;
        this.allowRestartIfKilled = allowRestartIfKilled;
        this.startLimit = startLimit;
        this.executor = getExecutor(executor);
    }

    /**
     * concurrency 装配期护栏（1.7-C8-003，plan 2498 Phase 5）：<=0 归一为 1（既有文档化行为），
     * 超过 {@link BatchConfigs#CFG_BATCH_MAX_CONCURRENCY_CAP} 装配期类型化 fail-fast——
     * 防止单任务把平台共享兜底池提交面放大到超界（线程占用/达界拒绝风暴在装配期即不可达）。
     */
    static int normalizeConcurrency(int concurrency) {
        int normalized = Math.max(concurrency, 1);
        int cap = BatchConfigs.CFG_BATCH_MAX_CONCURRENCY_CAP.get();
        if (normalized > cap) {
            throw new NopException(ERR_BATCH_CONCURRENCY_EXCEEDS_CAP)
                    .param(ARG_PARAM_NAME, "concurrency")
                    .param(ARG_PARAM_VALUE, concurrency)
                    .param(ARG_MAX_CONCURRENCY_CAP, cap);
        }
        return normalized;
    }

    /**
     * 兜底池形态（1.7-C8-003 评估结论，plan 2498 Phase 5）：维持平台共享 cached 池兜底。
     * 该池经 R1-02 裁定④基线加固——池宽上界由配置项 nop.commons.concurrent.cached-pool.maxPoolSize
     * 控制（缺省 200），达界提交获得显式拒绝信号（nop.err.commons.concurrent.cached-pool-saturated），
     * 线程爆炸面已在设施层闭合；本模块装配期护栏（normalizeConcurrency）在其上再封单任务并发上界。
     * 不迁移到 globalWorker：chunk 循环为长任务体，按平台族契约（调度槽 vs 执行体）不得常驻平台共享 worker。
     */
    static Executor getExecutor(Executor executor) {
        if (executor != null)
            return executor;
        // concurrency在构造器中已归一化为>=1且不超装配期上界，chunk循环统一由cachedThreadPool执行
        return GlobalExecutors.cachedThreadPool();
    }

    @Override
    public CompletableFuture<Void> executeAsync(IBatchTaskContext context) {
        // P1-3.3-003（plan 2381）：per-execution 重置（防御性——全仓调用点均每次新建实例，
        // 此重置保证即便未来出现实例复用也不会跨执行携带旧根因）
        primaryErrorRef.set(null);
        // 如果context上尚未设置限制，则设置，否则以外部设置的为准
        if (context.getAllowStartIfComplete() == null && allowStartIfComplete != null)
            context.setAllowStartIfComplete(allowStartIfComplete);

        if (context.getStartLimit() <= 0)
            context.setStartLimit(startLimit);

        // 1.7-C7-012（plan 2546 W-06）：缺省注入（对齐 allowStartIfComplete 模式）
        if (context.getAllowRestartIfKilled() == null && allowRestartIfKilled != null)
            context.setAllowRestartIfKilled(allowRestartIfKilled);

        // 1.7-C8-004（plan 2546 W-07）：metrics 解析/创建与 beginTask 一并放在 setTaskName 之后——
        // BatchTaskMetricsImpl 构造器即按 tag 建全部 meter，无法"延后重绑"；若在 setTaskName 之前
        // 建 meters，全部 tag 会落 "default"。单漏斗覆盖 DSL/exp/biz 三链（外部已注入时不覆盖）。
        // 用单元素数组承载 meter：它必须在 try 内 setTaskName 之后才创建（lambda 捕获要求 effectively-final）
        Object[] meterRef = new Object[1];

        // 1.7-C1-001（plan 2545 W-01）：保存外层上下文——嵌套批任务（processor 内同步执行子任务）
        // 在此覆盖并 remove 会确定性清空外层 TL，使外层后半程 xpl 监听助手 NPE/失聪。
        Object prevTaskContext = BatchTaskGlobals.saveTaskContext();
        BatchTaskGlobals.provideTaskContext(context);
        try {
            // 如果context中已经设置，则以context中的值为准
            if (context.getTaskName() == null)
                context.setTaskName(taskName);
            if (context.getTaskVersion() == null)
                context.setTaskVersion(taskVersion);

            initTaskKey(context);

            // 1.7-C8-004（plan 2546 W-07）：此处 taskName/taskVersion/taskKey 均已就绪
            IBatchTaskMetrics metrics = context.getMetrics();
            if (metrics == null && BatchConfigs.CFG_BATCH_METRICS_ENABLED.get()) {
                metrics = new io.nop.batch.core.metrics.BatchTaskMetricsImpl(
                        io.nop.commons.metrics.GlobalMeterRegistry.instance(), null, null, context.getTaskName());
                context.setMetrics(metrics);
            }
            meterRef[0] = metrics == null ? null : metrics.beginTask();

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

                if(context.getParams() != null)
                    context.getParams().forEach(context.getEvalScope()::setLocalValue);

                loader = loaderProvider.setup(context);
                chunkProcessor = chunkProcessorProvider.setup(loader, context);

                context.fireTaskBegin();
            } catch (Exception e) {
                // onTaskBegin中有可能分配资源，必须调用onTaskEnd来释放资源
                onTaskComplete(future, meterRef[0], e, context);
                return future;
            }

            // 多个线程可以并发执行。loader/processor/consumer都需要是线程安全的
            CompletableFuture<?>[] futures = new CompletableFuture[concurrency];
            try {
                for (int i = 0; i < concurrency; i++) {
                    futures[i] = executeChunkLoop(context, i, chunkProcessor);
                }
            } catch (Exception e) {
                // C6-002（plan 2516，含 C7-002A 吸收面）：提交循环包络——任一 executor.execute 同步拒绝时，
                // 已提交的 futures[0..i-1] 幽灵链必须逐个显式结算，否则 allOf 永不触发 → onTaskComplete 不执行 →
                // reader/output/JDBC连接泄漏 + 任务行永久 RUNNING。future 终态化使 allOf 立即触发；
                // 已在运行的加载线程由既有 fail-fast cancel（CANCEL_REASON_STOP）短路承接，不中断数据一致性。
                // 随后直达 onTaskComplete 既有终态化+关闭链（与 setup 失败路径同型）。
                for (CompletableFuture<?> submitted : futures) {
                    if (submitted != null)
                        submitted.completeExceptionally(e);
                }
                onTaskComplete(future, meterRef[0], e, context);
                return future;
            }

            CompletableFuture.allOf(futures).whenComplete((ret, err) -> {
                onTaskComplete(future, meterRef[0], err, context);
            });

            return future;

        } finally {
            BatchTaskGlobals.restoreTaskContext(prevTaskContext);
        }
    }

    static Throwable unwrapCompletionException(Throwable err) {
        while (err instanceof CompletionException && err.getCause() != null)
            err = err.getCause();
        return err;
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
        // allOf回调交付的异常被CompletionException包装，这里解包为原始异常，
        // 便于stateStore按异常类型判定任务状态（如BatchCancelException对应KILLED/CANCELLED状态）
        err = unwrapCompletionException(err);

        // P1-3.3-003（plan 2381）：primaryError 优先——首个非取消异常（executeChunkLoop catch
        // 中 CAS 收集）优先于 allOf 的时序性交付，消除 cancel 异常掩蔽 fail-fast 根因 → KILLED 误分类。
        // err==null（成功路径）或 err 即 primary 时维持原值。
        Throwable primary = primaryErrorRef.get();
        if (primary != null && err != null && err != primary)
            err = primary;

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
                } catch (Throwable x) {
                    // C6-009 S4/S6（plan 2516）：终态写失败不吞不替换——ERROR 显式留痕（携 taskId/taskName）
                    // 后放弃本次状态写（行残留由裁定① stale 接管承接），X 不得替代原始 err
                    // （S6：setup 失败路径进入时对外仍以原始 e 结算）。context.completeExceptionally(err) 必达。
                    LOG.error("nop.err.batch.task-terminal-save-fail:taskName={},taskId={}",
                            context.getTaskName(), context.getTaskId(), x);
                } finally {
                    context.completeExceptionally(err);
                }
            }
        } finally {
            // C6-009 S5（plan 2516）：metrics.endTask 独立 try——endTask 抛出不得跳过 FutureHelper.complete，
            // 否则返回 future 成为孤儿、调用方永久挂死。不改 finally 内顺序（plan 2496/2381 已修面零回退）。
            try {
                if (metrics != null)
                    metrics.endTask(meter, err == null);
            } catch (Throwable x) {
                LOG.error("nop.err.batch.task-metrics-end-fail:taskName={},taskId={}",
                        context.getTaskName(), context.getTaskId(), x);
            }

            // 1.7-C7-005（plan 2546 W-01）：跳过可审计——被跳过的记录不进 completedItems、
            // 也不进 historyStore（saveProcessed 仅收成功集），故终态必须显式汇总规模，
            // 否则运维无从得知"实际处理了多少、跳过了多少"（死信账本为独立能力，见 plan Deferred）。
            long skipped = context.getSkipItemCount();
            if (skipped > 0)
                LOG.warn("nop.warn.batch.task-skipped-items:taskName={},taskId={},taskKey={},skipItemCount={}",
                        context.getTaskName(), context.getTaskId(), context.getTaskKey(), skipped);

            FutureHelper.complete(future, null, err);
        }
    }

    CompletableFuture<Void> executeChunkLoop(IBatchTaskContext context, int threadIndex,
                                             IBatchChunkProcessorProvider.IBatchChunkProcessor<S> chunkProcessor) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            executor.execute(() -> {
            LOG.info("nop.batch.run-chunk-loop:threadIndex={},threadName={}", threadIndex, Thread.currentThread().getName());
            ContextProvider.runWithContext(ctx -> {
                // C6-010（plan 2496，与 C6-001 同 diff）：守卫起点上移到 lambda 首行——propagateContext /
                // provideTaskContext 等守卫生效前的段抛出的异常也必须被结算管线捕获
                Object prevTaskContext = null;
                try {
                    propagateContext(ctx, context);

                    // 1.7-C1-001（plan 2545 W-01）：同 executeAsync——保存/恢复而非裸 provide/remove
                    prevTaskContext = BatchTaskGlobals.saveTaskContext();
                    BatchTaskGlobals.provideTaskContext(context);

                    do {
                        if (context.isCancelled())
                            throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);

                        if (processChunk(context, threadIndex, chunkProcessor) != ProcessResult.CONTINUE)
                            break;

                    } while (true);

                    future.complete(null);
                } catch (Throwable e) {
                    // C6-001（plan 2496）：catch 宽度放宽到 Throwable（含 Error）——Error 逃逸曾使 future
                    // 永不结算 → allOf 永不触发 → afterComplete 关闭链失守 → reader/writer/连接泄漏 +
                    // 任务行永久 RUNNING + 调用方挂死。结算管线对任何 Throwable 都必须走通。
                    NopException.logIfNotTraced(LOG, "nop.batch.execute-chunk-loop-fail", e);
                    // P1-3.3-003（plan 2381）：CAS 收集首个非取消异常作为 primaryError
                    //（时序竞态下 allOf 可能交付兄弟线程的 BatchCancelException，掩蔽真根因）
                    if (!(e instanceof BatchCancelException))
                        primaryErrorRef.compareAndSet(null, e);
                    // 先完成future，确保allOf报告的是原始异常而不是兄弟线程随后抛出的BatchCancelException
                    future.completeExceptionally(e);

                    // fail-fast: 任一chunk失败后通知其他线程尽快停止，避免失败后继续处理剩余数据
                    if (!context.isCancelled())
                        context.cancel(ICancellable.CANCEL_REASON_STOP);
                } finally {
                    BatchTaskGlobals.restoreTaskContext(prevTaskContext);
                }
                return null;
            });
        });

        } catch (RejectedExecutionException rex) {
            // C6-002（plan 2516 裁定⑥）：提交点防御——REE 在 lambda 执行前抛出，lambda 内 catch Throwable 管不到。
            // future 终态化使 allOf 立即触发清理链（lambda 未执行无数据泄漏面），任务不再滞留 RUNNING。
            LOG.error("nop.err.batch.chunk-loop-submit-rejected:taskName={},taskId={},threadIndex={}",
                    context.getTaskName(), context.getTaskId(), threadIndex, rex);
            future.completeExceptionally(rex);
        }
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

            incCount(context, chunkContext);

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

            if (!syncCount) {
                incCount(context, chunkContext);
            }

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
            LOG.info("nop.batch.process-chunk-end:taskName={},taskId={},taskKey={},threadIndex={},usedTime={}," +
                            "processCount={}.skipCount={},retryCount={},completeCount={},historyCount={},completedIndex={}", context.getTaskName(),
                    context.getTaskId(), context.getTaskKey(), threadIndex, endTime - beginTime,
                    context.getProcessItemCount(), context.getSkipItemCount(), context.getRetryItemCount(),
                    context.getCompleteItemCount(), context.getHistoryItemCount(), context.getCompletedIndex());

            if (metrics != null)
                metrics.endChunk(meter, success);
        }

        return result;
    }

    void incCount(IBatchTaskContext context, IBatchChunkContext chunkContext) {
        context.incCompleteItemCount(chunkContext.getCompletedItemCount() - chunkContext.getHistoryItemCount());
        context.incHistoryItemCount(chunkContext.getHistoryItemCount());
    }
}