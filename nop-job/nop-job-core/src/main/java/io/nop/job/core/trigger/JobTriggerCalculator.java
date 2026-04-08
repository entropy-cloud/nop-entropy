package io.nop.job.core.trigger;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.Guard;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.ITriggerSpec;
import io.nop.job.core.ICalendar;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core.NopJobCoreConstants;

/**
 * 新 planner 使用的 trigger 纯计算入口。
 */
public final class JobTriggerCalculator {
    private JobTriggerCalculator() {
    }

    public static long calculateNextFireTime(ITriggerSpec spec, ITriggerEvalContext evalContext, long now) {
        return calculateNextFireTime(spec, evalContext, now, null);
    }

    public static long calculateNextFireTime(ITriggerSpec spec, ITriggerEvalContext evalContext,
                                             long now, ICalendar defaultCalendar) {
        Guard.notNull(spec, "spec");
        Guard.notNull(evalContext, "evalContext");

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, defaultCalendar);
        return trigger.nextScheduleTime(now, new ReadOnlyTriggerContext(evalContext));
    }

    private static final class ReadOnlyTriggerContext extends JobInstanceState implements ITriggerContext {
        private final ITriggerEvalContext evalContext;

        private ReadOnlyTriggerContext(ITriggerEvalContext evalContext) {
            this.evalContext = evalContext;
            setExecCount(evalContext.getFireCount());
            setScheduledExecTime(evalContext.getLastScheduledTime());
            setExecEndTime(evalContext.getLastEndTime());
            setInstanceStatus(evalContext.isScheduleCompleted() ?
                    NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED :
                    NopJobCoreConstants.JOB_INSTANCE_STATUS_UNKNOWN);
        }

        @Override
        public long getMinScheduleTime() {
            return evalContext.getMinScheduleTime();
        }

        @Override
        public long getMaxScheduleTime() {
            return evalContext.getMaxScheduleTime();
        }

        @Override
        public long getMaxExecutionCount() {
            return evalContext.getMaxExecutionCount();
        }

        @Override
        public long getMaxFailedCount() {
            return 0;
        }

        @Override
        public boolean isScheduleEnabled() {
            return !evalContext.isScheduleCompleted();
        }

        @Override
        public boolean isJobFinished() {
            return evalContext.isScheduleCompleted();
        }

        @Override
        public boolean isInstanceRunning() {
            return false;
        }

        @Override
        public void setScheduleEnabled(boolean scheduleEnabled) {
            throw unsupported();
        }

        @Override
        public void setMaxFailedCount(long maxFailedCount) {
            throw unsupported();
        }

        @Override
        public void setMaxExecutionCount(long maxExecutionCount) {
            throw unsupported();
        }

        @Override
        public void setMinScheduleTime(long minScheduleTime) {
            throw unsupported();
        }

        @Override
        public void setMaxScheduleTime(long maxScheduleTime) {
            throw unsupported();
        }

        @Override
        public void onSchedule(long currentTime, long nextScheduleTime) {
            throw unsupported();
        }

        @Override
        public void onInstanceBeginExecute(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onInstanceSuccess(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onInstanceFailed(long currentTime, ErrorBean exception) {
            throw unsupported();
        }

        @Override
        public void onInstanceCancelled(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onInstanceTimeout(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onBeginFireNow(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onEndFireNow(long currentTime, JobFireResult result, Throwable err) {
            throw unsupported();
        }

        @Override
        public void onJobFinished(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onJobFailed(long currentTime, ErrorBean error) {
            throw unsupported();
        }

        @Override
        public void onJobSuspended(long currentTime) {
            throw unsupported();
        }

        @Override
        public void onJobKilled(long currentTime) {
            throw unsupported();
        }

        @Override
        public void update(ITriggerSpec spec) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("read-only trigger eval context");
        }
    }
}
