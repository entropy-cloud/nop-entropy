
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;

import io.nop.job.biz.INopJobFireBiz;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.helper.JobFireStateMachine;
import io.nop.job.dao.helper.JobScheduleStateMachine;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.service.JobContextHelper;
import io.nop.job.service.fire.FireFactory;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_CANCEL_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_DELETE_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_RERUN_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_RERUN_DISCARDED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_SAVE_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_UPDATE_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED;

import java.util.Map;

@BizModel("NopJobFire")
public class NopJobFireBizModel extends CrudBizModel<NopJobFire> implements INopJobFireBiz{
    static final Logger LOG = LoggerFactory.getLogger(NopJobFireBizModel.class);

    protected IJobFireStore fireStore;
    protected IJobScheduleStore scheduleStore;

    public NopJobFireBizModel(){
        setEntityName(NopJobFire.class.getName());
    }

    @Override
    public boolean delete(String id, IServiceContext context) {
        throw new NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED)
                .param("jobFireId", id);
    }

    @Override
    public NopJobFire save(Map<String, Object> data, IServiceContext context) {
        throw new NopException(ERR_JOB_FIRE_SAVE_NOT_ALLOWED);
    }

    @Override
    public NopJobFire update(Map<String, Object> data, IServiceContext context) {
        throw new NopException(ERR_JOB_FIRE_UPDATE_NOT_ALLOWED);
    }

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    @Override
    @BizMutation
    public void cancelFire(@Name("id") String id, IServiceContext context) {
        NopJobFire fire = requireEntity(id, "cancelFire", context);
        if (!JobFireStateMachine.isActive(fire.getFireStatus())) {
            throwCancelNotAllowed(fire, "cancelFire");
        }

        FireScheduleOutcome outcome = fireStore.cancelFire(id);
        if (!outcome.fireUpdated()) {
            throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
        }
        if (!outcome.scheduleUpdated()) {
            LOG.warn("nop.job.cancel.schedule-counter-not-updated:fireId={}", id);
        }

        afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
    }

    @Override
    @BizMutation
    public void rerunFire(@Name("id") String id, IServiceContext context) {
        NopJobFire sourceFire = requireEntity(id, "rerunFire", context);
        if (!JobFireStateMachine.isTerminal(sourceFire.getFireStatus())) {
            throwRerunNotAllowed(sourceFire, "rerunFire");
        }

        NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());
        validateRerunSchedule(schedule, "rerunFire");

        NopJobFire rerunFire = buildRecoveryFire(sourceFire, schedule, context);
        if (!scheduleStore.insertManualFire(schedule, rerunFire)) {
            throw new NopException(ERR_JOB_FIRE_RERUN_DISCARDED)
                    .param("jobFireId", sourceFire.getJobFireId())
                    .param("jobScheduleId", schedule.getJobScheduleId())
                    .param("jobName", schedule.getJobName());
        }
        afterEntityChange(rerunFire, "rerunFire", context);
    }

    private void validateRerunSchedule(NopJobSchedule schedule, String action) {
        if (JobScheduleStateMachine.canTriggerNow(schedule.getScheduleStatus())) {
            return;
        }

        throw new NopException(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED)
                .param("jobScheduleId", schedule.getJobScheduleId())
                .param("jobName", schedule.getJobName())
                .param("scheduleStatus", schedule.getScheduleStatus())
                .param("action", action);
    }

    private NopJobFire buildRecoveryFire(NopJobFire sourceFire, NopJobSchedule schedule, IServiceContext context) {
        long now = scheduleStore.getCurrentTime();
        Timestamp fireTime = new Timestamp(now);

        NopJobFire fire = newEntity();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY);
        fire.setScheduledFireTime(fireTime);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        fire.setPlannerInstanceId(AppConfig.hostId());
        fire.setTriggeredBy(JobContextHelper.resolveTriggeredBy(context));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setRetryPolicyId(schedule.getRetryPolicyId());
        fire.setJobParamsSnapshot(schedule.getJobParams());
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setDispatchMode(schedule.getDispatchMode());
        FireFactory.fillBaseFireFields(fire, fireTime);
        return fire;
    }

    private void throwCancelNotAllowed(NopJobFire fire, String action) {
        throw new NopException(ERR_JOB_FIRE_CANCEL_NOT_ALLOWED)
                .param("jobFireId", fire.getJobFireId())
                .param("jobScheduleId", fire.getJobScheduleId())
                .param("jobName", fire.getJobName())
                .param("fireStatus", fire.getFireStatus())
                .param("action", action);
    }

    private void throwRerunNotAllowed(NopJobFire fire, String action) {
        throw new NopException(ERR_JOB_FIRE_RERUN_NOT_ALLOWED)
                .param("jobFireId", fire.getJobFireId())
                .param("jobScheduleId", fire.getJobScheduleId())
                .param("jobName", fire.getJobName())
                .param("fireStatus", fire.getFireStatus())
                .param("action", action);
    }
}
