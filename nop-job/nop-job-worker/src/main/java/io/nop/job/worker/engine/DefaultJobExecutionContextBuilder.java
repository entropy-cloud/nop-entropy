package io.nop.job.worker.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ErrorBean;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_EXECUTION_FAILED;

public class DefaultJobExecutionContextBuilder implements IJobExecutionContextBuilder {
    @Override
    public IJobExecutionContext buildContext(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
        return new WorkerJobExecutionContext(schedule, fire, task);
    }

    @Override
    public JobTaskExecutionUpdate buildResultUpdate(NopJobTask task, JobFireResult result, Throwable err) {
        if (err != null) {
            ErrorBean error = new ErrorBean(ERR_JOB_EXECUTION_FAILED.getErrorCode()).description(err.getMessage());
            return new JobTaskExecutionUpdate(_NopJobCoreConstants.TASK_STATUS_FAILED, error, null, true);
        }

        if (result == null) {
            return new JobTaskExecutionUpdate(_NopJobCoreConstants.TASK_STATUS_SUCCESS, null, null, false);
        }

        if (result.isErrorResult()) {
            ErrorBean error = result.getError();
            if (error == null) {
                error = new ErrorBean(ERR_JOB_EXECUTION_FAILED.getErrorCode());
            }
            return new JobTaskExecutionUpdate(_NopJobCoreConstants.TASK_STATUS_FAILED, error, null, true);
        }

        return new JobTaskExecutionUpdate(
                _NopJobCoreConstants.TASK_STATUS_SUCCESS,
                null,
                result.getNextScheduleTime() > 0 ? result.getNextScheduleTime() : null,
                result.isCompleted()
        );
    }

    private static final class WorkerJobExecutionContext extends JobInstanceState implements IJobExecutionContext {
        private final long minScheduleTime;
        private final long maxScheduleTime;
        private final long maxExecutionCount;
        private final boolean jobFinished;
        private final boolean instanceRunning;
        private final boolean scheduleEnabled;

        private WorkerJobExecutionContext(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            setJobDefId(schedule.getJobScheduleId());
            setJobGroup(schedule.getGroupId());
            setJobName(schedule.getJobName());
            setJobVersion(0L);
            setJobParams(resolveJobParams(schedule, fire, task));
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
            setAttribute("executorKind", schedule.getExecutorKind());
            setAttribute("timeoutSeconds", schedule.getTimeoutSeconds() != null ? schedule.getTimeoutSeconds() : 0);

            // Sharding info and target host are now stored as entity columns (set by
            // RpcBroadcastTaskBuilder). RpcJobInvoker reads them from attributes and
            // injects them as RPC headers.
            if (task.getShardingIndex() != null) {
                setAttribute("shardingIndex", task.getShardingIndex());
            }
            if (task.getShardingTotal() != null) {
                setAttribute("shardingTotal", task.getShardingTotal());
            }

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

        private static Map<String, Object> resolveJobParams(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
            if (jobParams == null) {
                jobParams = schedule.getJobParamsComponent().get_jsonMap();
            }
            if (jobParams == null) {
                jobParams = new HashMap<>();
            } else {
                jobParams = new HashMap<>(jobParams);
            }

            // Inject targetHost as header so that RpcJobInvoker can propagate it.
            // targetHost is stored as a column on NopJobTask by RpcBroadcastTaskBuilder.
            if (task.getTargetHost() != null && !task.getTargetHost().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> headers = (Map<String, Object>) jobParams.computeIfAbsent("headers", k -> new HashMap<String, Object>());
                headers.put(ApiConstants.HEADER_SVC_TARGET_HOST, task.getTargetHost());
            }

            return jobParams;
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
