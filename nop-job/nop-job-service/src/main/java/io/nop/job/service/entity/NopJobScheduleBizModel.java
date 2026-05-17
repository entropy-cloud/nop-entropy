
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;

import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.biz.INopJobScheduleBiz;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_ALREADY_ARCHIVED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED;

@BizModel("NopJobSchedule")
public class NopJobScheduleBizModel extends CrudBizModel<NopJobSchedule> implements INopJobScheduleBiz{
    protected IJobScheduleStore scheduleStore;

    public NopJobScheduleBizModel(){
        setEntityName(NopJobSchedule.class.getName());
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    @Override
    @BizMutation
    public void enableSchedule(@Name("id") String id, IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "enableSchedule", context);
        validateScheduleStatus(schedule, "enableSchedule", _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED);
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
        if (schedule.getNextFireTime() == null) {
            schedule.setNextFireTime(recalculateNextFireTime(schedule));
        }
        persistSchedule(schedule, "enableSchedule", context);
    }

    @Override
    @BizMutation
    public void disableSchedule(@Name("id") String id, IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "disableSchedule", context);
        if (isScheduleStatus(schedule, _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED)) {
            return;
        }

        validateScheduleStatus(schedule, "disableSchedule",
                _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED,
                _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED);
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED);
        persistSchedule(schedule, "disableSchedule", context);
    }

    @Override
    @BizMutation
    public void pauseSchedule(@Name("id") String id, IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "pauseSchedule", context);
        if (isScheduleStatus(schedule, _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED)) {
            return;
        }

        validateScheduleStatus(schedule, "pauseSchedule", _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED);
        persistSchedule(schedule, "pauseSchedule", context);
    }

    @Override
    @BizMutation
    public void resumeSchedule(@Name("id") String id, IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "resumeSchedule", context);
        validateScheduleStatus(schedule, "resumeSchedule", _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED);
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
        schedule.setNextFireTime(recalculateNextFireTime(schedule));
        persistSchedule(schedule, "resumeSchedule", context);
    }

    @Override
    @BizMutation
    public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                           IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "triggerNow", context);
        validateManualTriggerSchedule(schedule, "triggerNow");

        NopJobFire fire = buildManualFire(schedule, overrideParams, context);
        scheduleStore.insertManualFire(schedule, fire);
        afterEntityChange(schedule, "triggerNow", context);
    }

    @Override
    @BizMutation
    public void archiveSchedule(@Name("id") String id, IServiceContext context) {
        NopJobSchedule schedule = requireEntity(id, "archiveSchedule", context);
        if (isScheduleStatus(schedule, _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED)) {
            return;
        }

        validateScheduleStatus(schedule, "archiveSchedule",
                _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED,
                _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED,
                _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED,
                _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED);
        schedule.setNextFireTime(null);
        persistSchedule(schedule, "archiveSchedule", context);
    }

    private void persistSchedule(NopJobSchedule schedule, String action, IServiceContext context) {
        dao().updateEntityDirectly(schedule);
        afterEntityChange(schedule, action, context);
    }

    private void validateManualTriggerSchedule(NopJobSchedule schedule, String action) {
        if (schedule.getScheduleStatus() == null) {
            return;
        }

        if (schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED
                || schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED) {
            throw new NopException(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED)
                    .param("jobScheduleId", schedule.getJobScheduleId())
                    .param("jobName", schedule.getJobName())
                    .param("scheduleStatus", schedule.getScheduleStatus())
                    .param("action", action);
        }
    }

    private boolean isScheduleStatus(NopJobSchedule schedule, int status) {
        return schedule.getScheduleStatus() != null && schedule.getScheduleStatus() == status;
    }

    private void validateScheduleStatus(NopJobSchedule schedule, String action, int... allowedStatuses) {
        if (schedule.getScheduleStatus() != null
                && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED) {
            throw new NopException(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED)
                    .param("jobScheduleId", schedule.getJobScheduleId())
                    .param("jobName", schedule.getJobName())
                    .param("action", action);
        }

        for (int allowedStatus : allowedStatuses) {
            if (schedule.getScheduleStatus() != null && schedule.getScheduleStatus() == allowedStatus) {
                return;
            }
        }

        throw new NopException(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION)
                .param("jobScheduleId", schedule.getJobScheduleId())
                .param("jobName", schedule.getJobName())
                .param("scheduleStatus", schedule.getScheduleStatus())
                .param("allowedStatuses", Arrays.toString(allowedStatuses))
                .param("action", action);
    }

    private NopJobFire buildManualFire(NopJobSchedule schedule, Map<String, Object> overrideParams,
                                       IServiceContext context) {
        long now = scheduleStore.getCurrentTime();
        Timestamp fireTime = new Timestamp(now);

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_MANUAL);
        fire.setScheduledFireTime(fireTime);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        fire.setPlannerInstanceId(AppConfig.hostId());
        fire.setTriggeredBy(resolveTriggeredBy(context));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setRetryPolicyId(schedule.getRetryPolicyId());
        fire.setCreatedBy("system");
        fire.setCreateTime(fireTime);
        fire.setUpdatedBy("system");
        fire.setUpdateTime(fireTime);
        fire.setJobParamsSnapshot(JsonTool.stringify(resolveJobParams(schedule, overrideParams)));
        fire.setExecutorKind(schedule.getExecutorKind());
        return fire;
    }

    private Map<String, Object> resolveJobParams(NopJobSchedule schedule, Map<String, Object> overrideParams) {
        if (overrideParams != null) {
            return overrideParams;
        }

        Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
        if (scheduleParams != null) {
            return scheduleParams;
        }

        if (schedule.getJobParams() != null && !schedule.getJobParams().isEmpty()) {
            Map<String, Object> parsed = JsonTool.parseMap(schedule.getJobParams());
            if (parsed != null) {
                return parsed;
            }
        }
        return Collections.emptyMap();
    }

    private String resolveTriggeredBy(IServiceContext context) {
        String userName = null;
        if (context != null) {
            if (context.getUserContext() != null) {
                userName = context.getUserContext().getUserName();
            }
            if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
                userName = context.getContext().getUserName();
            }
        }
        return userName == null || userName.isEmpty() ? "system" : userName;
    }

    private Timestamp recalculateNextFireTime(NopJobSchedule schedule) {
        long next = JobTriggerCalculator.calculateNextFireTime(
                toTriggerSpec(schedule),
                toEvalContext(schedule),
                System.currentTimeMillis()
        );
        return next <= 0 ? null : new Timestamp(next);
    }

    private TriggerSpec toTriggerSpec(NopJobSchedule schedule) {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr(schedule.getCronExpr());
        spec.setRepeatInterval(defaultLong(schedule.getRepeatIntervalMs()));
        spec.setRepeatFixedDelay(schedule.getTriggerType() != null
                && schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        spec.setMaxExecutionCount(defaultInt(schedule.getMaxExecutionCount()));
        spec.setMinScheduleTime(toTime(schedule.getMinScheduleTime()));
        spec.setMaxScheduleTime(toTime(schedule.getMaxScheduleTime()));
        spec.setMisfireThreshold(defaultInt(schedule.getMisfireThresholdMs()));
        spec.setUseDefaultCalendar(schedule.getUseDefaultCalendar() != null && schedule.getUseDefaultCalendar() == 1);
        spec.setPauseCalendars(Collections.emptyList());
        spec.setMaxFailedCount(0);
        return spec;
    }

    private ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return new ITriggerEvalContext() {
            @Override
            public long getFireCount() {
                return defaultLong(schedule.getFireCount());
            }

            @Override
            public long getLastScheduledTime() {
                return toTime(schedule.getLastFireTime());
            }

            @Override
            public long getLastEndTime() {
                return toTime(schedule.getLastEndTime());
            }

            @Override
            public long getMinScheduleTime() {
                return toTime(schedule.getMinScheduleTime());
            }

            @Override
            public long getMaxScheduleTime() {
                return toTime(schedule.getMaxScheduleTime());
            }

            @Override
            public long getMaxExecutionCount() {
                return defaultInt(schedule.getMaxExecutionCount());
            }

            @Override
            public boolean isScheduleCompleted() {
                return schedule.getScheduleStatus() != null
                        && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
            }
        };
    }

    private long toTime(Timestamp value) {
        return value == null ? 0L : value.getTime();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
