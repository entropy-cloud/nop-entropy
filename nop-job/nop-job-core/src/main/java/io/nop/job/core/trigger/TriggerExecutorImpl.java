/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.job.api.IJobInstanceState;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerAction;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerExecution;
import io.nop.job.core.ITriggerExecutor;
import io.nop.job.core.NopJobCoreConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_TRIGGER_FIRE_FAIL;

/**
 * @author canonical_entropy@163.com
 */
public class TriggerExecutorImpl implements ITriggerExecutor {
    static final Logger LOG = LoggerFactory.getLogger(TriggerExecutorImpl.class);

    private final IScheduledExecutor executor;
    private final IErrorMessageManager errorMessageManager;

    public TriggerExecutorImpl(IScheduledExecutor executor, IErrorMessageManager errorMessageManager) {
        this.executor = executor;
        this.errorMessageManager = errorMessageManager;
    }

    @Override
    public ITriggerExecution execute(ITrigger trigger, ITriggerAction action,
                                     ITriggerContext context) {

        LOG.info("nop.job.start-trigger:jobName={},instanceId={},status={}", context.getJobName(),
                context.getInstanceId(), context.getInstanceStatus());

        TriggerExecution task = new TriggerExecution(trigger, action, context);
        task.schedule();

        return task;
    }

    @Override
    public ITriggerExecution fireNow(ITriggerAction action, ITriggerContext context) {
        LOG.info("nop.job.fire-now:jobName={},instanceId={},status={}", context.getJobName(),
                context.getInstanceId(), context.getInstanceStatus());

        TriggerExecution task = new TriggerExecution(null, action, context);
        task.fireNow();
        return task;
    }

    class TriggerExecution extends Cancellable implements ITriggerExecution {
        private final ITrigger trigger;
        private final ITriggerAction action;
        private final ITriggerContext context;
        private Future<?> currentFuture;

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
        public IJobInstanceState getTriggerState() {
            return context;
        }

        public boolean isRunning() {
            return executing;
        }

        public boolean isDone() {
            return promise.isDone();
        }

        void clearFuture() {
            Future<?> f = this.currentFuture;
            if (f != null) {
                f.cancel(false);
                this.currentFuture = null;
            }
        }

        protected void doCancel(String cancelReason) {
            synchronized (context) {
                // 取消当前正在等待调度执行的任务
                scheduleIndex++;
                clearFuture();

                long currentTime = getCurrentTime();
                if (!context.isJobFinished()) {
                    if (NopJobCoreConstants.CANCEL_REASON_KILL.equals(cancelReason)) {
                        context.onJobKilled(currentTime);
                    } else if (NopJobCoreConstants.CANCEL_REASON_CANCEL.equals(cancelReason)) {
                        context.onInstanceCancelled(currentTime);
                    } else {
                        context.onJobSuspended(currentTime);
                    }
                }

                // 如果有ScheduleTask正在执行，则它会自己结束
                if (!isRunning())
                    promise.complete(null);
            }
        }

        void schedule() {
            long nextScheduleTime = calcNextScheduleTime(getCurrentTime() - 1);
            if (nextScheduleTime <= 0) {
                synchronized (context) {
                    context.onJobFinished(getCurrentTime());
                }
                promise.complete(null);
            } else {
                scheduleAt(nextScheduleTime, scheduleIndex);
            }
        }

        void fireNow() {
            synchronized (context) {
                try {
                    context.onBeginFireNow(getCurrentTime());

                    CompletionStage<JobFireResult> taskPromise = action.execute(true, context, TriggerExecution.this);
                    if (taskPromise == null) {
                        handleFireNowResult(null, null);
                    } else {
                        taskPromise.whenComplete(this::handleFireNowResult);
                    }
                } catch (Exception e) {
                    handleFireNowResult(null, e);
                }
            }
        }

        void handleFireNowResult(JobFireResult result, Throwable err) {
            synchronized (context) {
                context.onEndFireNow(getCurrentTime(), result, err);
            }
        }

        private long calcNextScheduleTime(long afterTime) {
            long current = getCurrentTime();
            return trigger.nextScheduleTime(Math.max(afterTime, current), context);
        }

        private void scheduleAt(long beginTime, long startIndex) {
            synchronized (context) {
                // 如果调用了pause/cancel/deactivate，则isCancelled为true，不再继续调度。
                if (isCancelled())
                    return;

                if (isDone())
                    return;

                if (startIndex != scheduleIndex)
                    return;

                long current = getCurrentTime();
                context.onSchedule(current, beginTime);

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
                synchronized (context) {
                    // 避免重入。每次调度scheduleIndex都会递增
                    if (startIndex != scheduleIndex) {
                        return null;
                    }

                    if (isRunning() || isCancelled() || isDone())
                        return null;

                    // 最多只会有一个线程进入executing状态
                    executing = true;

                    context.onInstanceBeginExecute(getCurrentTime());
                }

                try {
                    CompletionStage<JobFireResult> taskPromise = action.execute(false, context, TriggerExecution.this);
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

            void handleResult(JobFireResult result, Throwable err) {
                long nextScheduleTime = -1;
                synchronized (context) {
                    try {
                        context.onInstanceSuccess(getCurrentTime());

                        if (result == null)
                            result = JobFireResult.CONTINUE;

                        if (err != null) {
                            onException(context, result, err);
                        }

                        if (result.isErrorResult()) {
                            // 明确指定按照ERROR终止
                            if (result.getError() != null) {
                                err = NopRebuildException.rebuild(result.getError());
                            }

                            if (err == null)
                                err = new NopException(ERR_JOB_TRIGGER_FIRE_FAIL);
                        } else if (result.isCompleted()) {
                            nextScheduleTime = 0;
                        } else {
                            // 明确指定下次调度时间
                            if (result.getNextScheduleTime() > 0) {
                                nextScheduleTime = result.getNextScheduleTime();
                            } else {
                                // 动态计算下次调度时间
                                nextScheduleTime = calcNextScheduleTime(context.getScheduledExecTime());
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
                        try {
                            synchronized (context) {
                                context.onJobFinished(getCurrentTime());
                            }
                            promise.complete(null);
                        } catch (Exception e) {
                            promise.completeExceptionally(e);
                        }
                    }
                }
            }
        }
    }

    private void onException(ITriggerContext context, JobFireResult result, Throwable exception) {
        long current = getCurrentTime();
        ErrorBean error = null;
        if (result != null) {
            error = result.getError();
        } else {
            error = errorMessageManager.buildErrorMessage(ContextProvider.currentLocale(), exception, true, false);
        }

        if (result != null && result.isCompleted()) {
            context.onJobFailed(current, error);
        } else if (context.getExecFailCount() + 1 >= context.getMaxFailedCount()) {
            context.onJobFailed(current, error);
        } else {
            context.onInstanceFailed(current, error);
        }
    }

    protected long getCurrentTime() {
        return CoreMetrics.currentTimeMillis();
    }
}