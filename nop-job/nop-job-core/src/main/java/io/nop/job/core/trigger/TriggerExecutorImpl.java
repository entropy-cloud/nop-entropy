/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core.trigger;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.IErrorMessageManager;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.TriggerFireResult;
import io.nop.job.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.concurrent.*;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_FIRE_FAIL;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerExecutorImpl implements ITriggerExecutor {
    static final Logger LOG = LoggerFactory.getLogger(TriggerExecutorImpl.class);

    private final IScheduledExecutor executor;
    private final IErrorMessageManager errorMessageManager;
    private ITriggerHook globalHook;

    public TriggerExecutorImpl(IScheduledExecutor executor, IErrorMessageManager errorMessageManager) {
        this.executor = executor;
        this.errorMessageManager = errorMessageManager;
    }

    public void setGlobalHook(ITriggerHook hook) {
        this.globalHook = hook;
    }

    @Override
    public ITriggerExecution execute(boolean fireNow, ITrigger trigger, ITriggerAction action,
                                     ITriggerContext context) {

        LOG.info("nop.job.start-trigger:jobName={},epoch={},executionId={},status={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), context.getTriggerStatus());

        TriggerExecution task = new TriggerExecution(trigger, action, context);
        task.schedule(fireNow);

        return task;
    }

    class TriggerExecution extends Cancellable implements ITriggerExecution {
        private final ITrigger trigger;
        private final ITriggerAction action;
        private final ITriggerContext context;
        private Future<?> currentFuture;

        // 对于triggerContext的修改在此lock保护下进行
        private final Object lock = new Object();

        // 是否正在执行action
        private volatile boolean executing;

        private int scheduleIndex = 0;
        private final CompletableFuture<Void> promise = new CompletableFuture<>();

        public TriggerExecution(ITrigger trigger, ITriggerAction action, ITriggerContext context) {
            this.trigger = trigger;
            this.action = action;
            this.context = context;
            this.appendOnCancel(this::doCancel);
        }

        @Override
        public CompletionStage<Void> getFinishPromise() {
            return promise;
        }

        @Override
        public ITriggerState getTriggerState() {
            return context;
        }

        public boolean isExecuting() {
            return executing;
        }

        public boolean isDone() {
            return promise.isDone();
        }

        @Override
        public boolean fireNow() {
            synchronized (lock) {
                if (isExecuting() || isCancelled() || isDone())
                    return false;

                // executing=false表示当前没有ScheduleTask正在执行

                // 取消当前正在等待调度执行的任务
                scheduleIndex++;
                clearFuture();

                // 立刻发起一次调度
                onFireNow(context);
                currentFuture = executor.schedule(new ScheduleTask(), 0, TimeUnit.MILLISECONDS);
            }

            return true;
        }

        void clearFuture() {
            Future<?> f = this.currentFuture;
            if (f != null) {
                f.cancel(false);
                this.currentFuture = null;
            }
        }

        protected void doCancel(String cancelReason) {
            synchronized (lock) {
                // 取消当前正在等待调度执行的任务
                scheduleIndex++;
                clearFuture();

                if (!context.isDone()) {
                    if (CANCEL_REASON_KILL.equals(cancelReason)) {
                        onCancel(context);
                    } else {
                        if (JobCoreConstants.CANCEL_REASON_DEACTIVATE.equals(cancelReason)) {
                            context.deactivate();
                        }
                        onPaused(context);
                    }
                }

                // 如果有ScheduleTask正在执行，则它会自己结束
                if (!isExecuting())
                    promise.complete(null);
            }
        }

        void schedule(boolean fireNow) {
            if (fireNow) {
                synchronized (lock) {
                    onFireNow(context);
                    currentFuture = executor.schedule(new ScheduleTask(), 0, TimeUnit.MILLISECONDS);
                    return;
                }
            }
            long nextScheduleTime = calcNextScheduleTime(getCurrentTime() - 1);
            if (nextScheduleTime <= 0) {
                synchronized (lock) {
                    onCompleted(context);
                }
                promise.complete(null);
            } else {
                scheduleAt(nextScheduleTime, scheduleIndex);
            }
        }

        private long calcNextScheduleTime(long afterTime) {
            long current = getCurrentTime();
            long beginTime = trigger.nextScheduleTime(Math.max(afterTime, current), context);
            return beginTime;
        }

        private void scheduleAt(long beginTime, long startIndex) {
            synchronized (lock) {
                // 如果调用了pause/cancel/deactivate，则isCancelled为true，不再继续调度。
                if (isCancelled())
                    return;

                if (isDone())
                    return;

                if (startIndex != scheduleIndex)
                    return;

                long current = getCurrentTime();
                onSchedule(context, beginTime);
                currentFuture = executor.schedule(new ScheduleTask(), Math.max(beginTime - current, 0),
                        TimeUnit.MILLISECONDS);
            }
        }

        class ScheduleTask implements Callable<Void> {
            private final int startIndex;

            public ScheduleTask() {
                this.startIndex = scheduleIndex;
            }

            public Void call() {
                synchronized (lock) {
                    // 避免重入。每次调度scheduleIndex都会递增
                    if (startIndex != scheduleIndex) {
                        return null;
                    }

                    if (isExecuting() || isCancelled() || isDone())
                        return null;

                    // 最多只会有一个线程进入executing状态
                    executing = true;

                    onBeginExecute(context);
                }

                try {
                    CompletionStage<TriggerFireResult> taskPromise = action.execute(context, TriggerExecution.this);
                    if (taskPromise == null) {
                        handleResult(null, null);
                    } else {
                        taskPromise.whenComplete(this::handleResult);
                    }
                } catch (Exception e) {
                    handleResult(null, e);
                }
                return null;
            }

            void handleResult(TriggerFireResult result, Throwable err) {
                long nextScheduleTime = -1;
                synchronized (lock) {
                    try {
                        onEndExecute(context);

                        if (result == null)
                            result = TriggerFireResult.CONTINUE;

                        if (err != null) {
                            onException(context, result, err);
                        }

                        if (result.isErrorResult()) {
                            // 明确指定按照ERROR终止
                            onError(context, result);
                            if (result.getError() != null) {
                                err = NopRebuildException.rebuild(result.getError());
                            }

                            if (err == null)
                                err = new NopException(ERR_JOB_TRIGGER_FIRE_FAIL);
                        } else if (result.isCompletedResult()) {
                            onCompleted(context);
                        } else if (context.isActive()) {
                            // 明确指定下次调度时间
                            if (result.getNextScheduleTime() > 0) {
                                nextScheduleTime = result.getNextScheduleTime();
                            } else {
                                // 动态计算下次调度时间
                                nextScheduleTime = calcNextScheduleTime(context.getLastScheduleTime());
                            }
                            if (nextScheduleTime <= 0) {
                                onCompleted(context);
                            }
                        }
                    } finally {
                        // 任务状态切换完毕之后再修改executing状态
                        executing = false;
                    }
                }

                // triggerContext的状态已经转入结束状态，这里只需要resolve promise
                if (err != null) {
                    promise.completeExceptionally(err);
                } else {
                    if (nextScheduleTime > 0) {
                        scheduleAt(nextScheduleTime, startIndex);
                    } else {
                        promise.complete(null);
                    }
                }
            }
        }
    }

    private void onCancel(ITriggerContext context) {
        long current = getCurrentTime();
        context.onCancel(current);

        LOG.info("nop.job.on-cancel:jobName={},epoch={},executionId={},status={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), context.getTriggerStatus());

        if (globalHook != null) {
            globalHook.onCancel(current, context);
        }
    }

    private void onPaused(ITriggerContext context) {
        long current = getCurrentTime();
        context.onPaused(current);

        LOG.info("nop.job.on-paused:jobName={},epoch={},executionId={},status={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), context.getTriggerStatus());

        if (globalHook != null) {
            globalHook.onPaused(current, context);
        }
    }

    private void onCompleted(ITriggerContext context) {
        long current = getCurrentTime();
        context.onCompleted(current);

        LOG.info("nop.job.on-completed:jobName={},epoch={},executionId={},status={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), context.getTriggerStatus());

        if (globalHook != null) {
            globalHook.onCompleted(current, context);
        }
    }

    private void onException(ITriggerContext context, TriggerFireResult result, Throwable exception) {
        long current = getCurrentTime();
        ErrorBean error = null;
        if (result != null) {
            error = result.getError();
        } else {
            error = errorMessageManager.buildErrorMessage(ContextProvider.currentLocale(), exception, true, false);
        }
        context.onException(current, error);

        LOG.info("nop.job.on-error:jobName={},epoch={},executionId={},error={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), error, exception);

        if (globalHook != null) {
            globalHook.onException(current, exception, context);
        }
    }

    private void onError(ITriggerContext context, TriggerFireResult result) {
        long current = getCurrentTime();
        context.onError(current, result.getError());

        LOG.info("nop.job.on-error:jobName={},epoch={},executionId={},error={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), result.getError());

        if (globalHook != null) {
            globalHook.onError(current, context);
        }
    }

    private void onSchedule(ITriggerContext context, long nextScheduleTime) {
        long current = getCurrentTime();
        context.onSchedule(current, nextScheduleTime);

        LOG.debug("nop.job.schedule:jobName={},epoch={},executionId={},nextScheduleTime={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), new Timestamp(nextScheduleTime));

        if (globalHook != null) {
            globalHook.onSchedule(current, nextScheduleTime, context);
        }
    }

    private void onFireNow(ITriggerContext context) {
        long current = getCurrentTime();
        context.onFireNow(current);

        LOG.debug("nop.job.fire-now:jobName={},epoch={},executionId={}", context.getJobName(), context.getEpoch(),
                context.getLastExecutionId());

        if (globalHook != null) {
            globalHook.onFireNow(current, context);
        }
    }

    private void onBeginExecute(ITriggerContext context) {
        long current = getCurrentTime();
        context.onBeginExecute(current);

        LOG.debug("nop.job.begin-execute:jobName={},epoch={},executionId={}", context.getJobName(), context.getEpoch(),
                context.getLastExecutionId());
        if (globalHook != null) {
            globalHook.onBeginExecute(current, context);
        }
    }

    private void onEndExecute(ITriggerContext context) {
        long current = getCurrentTime();
        context.onEndExecute(current);

        LOG.debug("nop.job.end-execute:jobName={},epoch={}, executionId={},status={}", context.getJobName(),
                context.getEpoch(), context.getLastExecutionId(), context.getTriggerStatus());

        if (globalHook != null) {
            globalHook.onEndExecute(current, context);
        }
    }

    protected long getCurrentTime() {
        return CoreMetrics.currentTimeMillis();
    }
}