package io.nop.job.coordinator.engine;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class DefaultJobCancelHandler implements IJobCancelHandler {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobCancelHandler.class);

    @Override
    public void cancelRunningTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
        String executorRef = resolveExecutorRef(schedule, fire);
        IJobInvoker invoker = resolveInvoker(executorRef);
        if (invoker == null) {
            LOG.debug("nop.job.cancel.invoker-not-found:scheduleId={},fireId={},taskId={},executorRef={}",
                    schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), executorRef);
            return;
        }

        try {
            CompletionStage<Boolean> promise = invoker.cancelAsync(new CancelJobExecutionContext(schedule, fire, task));
            if (promise != null) {
                promise.whenComplete((ret, err) -> {
                    if (err != null) {
                        LOG.warn("nop.job.cancel.invoke-failed:scheduleId={},fireId={},taskId={}",
                                schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), err);
                    }
                });
            }
        } catch (Exception e) {
            LOG.warn("nop.job.cancel.invoke-failed:scheduleId={},fireId={},taskId={}",
                    schedule.getJobScheduleId(), fire.getJobFireId(), task.getJobTaskId(), e);
        }
    }

    private IJobInvoker resolveInvoker(String executorRef) {
        if (executorRef == null || executorRef.isBlank() || !BeanContainer.isInitialized()) {
            return null;
        }

        Object bean = BeanContainer.tryGetBean(executorRef);
        if (!(bean instanceof IJobInvoker)) {
            bean = BeanContainer.tryGetBean("jobInvoker_" + executorRef);
        }
        return bean instanceof IJobInvoker ? (IJobInvoker) bean : null;
    }

    private String resolveExecutorRef(NopJobSchedule schedule, NopJobFire fire) {
        Map<String, Object> executorSnapshot = fire.getExecutorSnapshotComponent().get_jsonMap();
        Object executorRef = executorSnapshot == null ? null : executorSnapshot.get("executorRef");
        return executorRef instanceof String ? (String) executorRef : schedule.getExecutorRef();
    }

    private static Map<String, Object> resolveJobParams(NopJobSchedule schedule, NopJobFire fire) {
        Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
        if (jobParams != null) {
            return jobParams;
        }
        Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
        return scheduleParams == null ? Collections.emptyMap() : scheduleParams;
    }

    private static final class CancelJobExecutionContext extends JobInstanceState implements IJobExecutionContext {
        private final long minScheduleTime;
        private final long maxScheduleTime;
        private final long maxExecutionCount;
        private final boolean jobFinished;
        private final boolean instanceRunning;
        private final boolean scheduleEnabled;

        private CancelJobExecutionContext(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            setJobDefId(schedule.getJobScheduleId());
            setJobGroup(schedule.getGroupId());
            setJobName(schedule.getJobName());
            setJobVersion(0L);
            setJobParams(resolveJobParams(schedule, fire));
            setInstanceId(task.getJobTaskId());
            setExecCount(defaultLong(schedule.getFireCount()));
            setScheduledExecTime(toTime(fire.getScheduledFireTime()));
            setExecBeginTime(toTime(task.getStartTime()));
            setExecEndTime(toTime(task.getEndTime()));
            setOnceTask(schedule.getTriggerType() != null
                    && schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_ONCE);
            setManualFire(fire.getTriggerSource() != null
                    && fire.getTriggerSource() == _NopJobCoreConstants.TRIGGER_SOURCE_MANUAL);
            setFiredBy(fire.getTriggeredBy());
            setChangeVersion(defaultLong(task.getVersion()));
            setInstanceStatus(defaultInt(task.getTaskStatus()));

            setAttribute("jobScheduleId", schedule.getJobScheduleId());
            setAttribute("jobFireId", fire.getJobFireId());
            setAttribute("jobTaskId", task.getJobTaskId());
            setAttribute("executorRef", schedule.getExecutorRef());

            this.minScheduleTime = toTime(schedule.getMinScheduleTime());
            this.maxScheduleTime = toTime(schedule.getMaxScheduleTime());
            this.maxExecutionCount = defaultLong(schedule.getMaxExecutionCount());
            this.jobFinished = schedule.getScheduleStatus() != null
                    && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
            this.instanceRunning = task.getTaskStatus() != null
                    && task.getTaskStatus() == _NopJobCoreConstants.TASK_STATUS_RUNNING;
            this.scheduleEnabled = schedule.getScheduleStatus() != null
                    && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED;
        }

        @Override
        public long getMinScheduleTime() {
            return minScheduleTime;
        }

        @Override
        public long getMaxScheduleTime() {
            return maxScheduleTime;
        }

        @Override
        public long getMaxExecutionCount() {
            return maxExecutionCount;
        }

        @Override
        public long getMaxFailedCount() {
            return 0;
        }

        @Override
        public boolean isJobFinished() {
            return jobFinished;
        }

        @Override
        public boolean isInstanceRunning() {
            return instanceRunning;
        }

        @Override
        public boolean isScheduleEnabled() {
            return scheduleEnabled;
        }

        private static long toTime(java.sql.Timestamp value) {
            return value == null ? 0L : value.getTime();
        }

        private static long defaultLong(Number value) {
            return value == null ? 0L : value.longValue();
        }

        private static int defaultInt(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
