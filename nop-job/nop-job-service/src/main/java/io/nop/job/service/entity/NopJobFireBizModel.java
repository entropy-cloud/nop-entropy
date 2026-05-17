
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
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;

import java.sql.Timestamp;

import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_CANCEL_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_FIRE_RERUN_NOT_ALLOWED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED;

@BizModel("NopJobFire")
public class NopJobFireBizModel extends CrudBizModel<NopJobFire> implements INopJobFireBiz{
    protected IJobFireStore fireStore;
    protected IJobScheduleStore scheduleStore;

    public NopJobFireBizModel(){
        setEntityName(NopJobFire.class.getName());
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
        NopJobFire fire = fireStore.loadFire(id);
        if (!isCancelableStatus(fire.getFireStatus())) {
            throwCancelNotAllowed(fire, "cancelFire");
        }

        if (!fireStore.cancelFire(id)) {
            throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
        }

        afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
    }

    @Override
    @BizMutation
    public void rerunFire(@Name("id") String id, IServiceContext context) {
        NopJobFire sourceFire = fireStore.loadFire(id);
        if (!isRerunnableStatus(sourceFire.getFireStatus())) {
            throwRerunNotAllowed(sourceFire, "rerunFire");
        }

        NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());
        validateRerunSchedule(schedule, "rerunFire");

        NopJobFire rerunFire = buildRecoveryFire(sourceFire, context);
        scheduleStore.insertManualFire(schedule, rerunFire);
        afterEntityChange(rerunFire, "rerunFire", context);
    }

    private boolean isCancelableStatus(Integer fireStatus) {
        return fireStatus != null
                && (fireStatus == _NopJobCoreConstants.FIRE_STATUS_WAITING
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_DISPATCHING
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_RUNNING);
    }

    private boolean isRerunnableStatus(Integer fireStatus) {
        return fireStatus != null
                && (fireStatus == _NopJobCoreConstants.FIRE_STATUS_SUCCESS
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_FAILED
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_CANCELED);
    }

    private void validateRerunSchedule(NopJobSchedule schedule, String action) {
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

    private NopJobFire buildRecoveryFire(NopJobFire sourceFire, IServiceContext context) {
        long now = scheduleStore.getCurrentTime();
        Timestamp fireTime = new Timestamp(now);

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(sourceFire.getJobScheduleId());
        fire.setNamespaceId(sourceFire.getNamespaceId());
        fire.setGroupId(sourceFire.getGroupId());
        fire.setJobName(sourceFire.getJobName());
        fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY);
        fire.setScheduledFireTime(fireTime);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        fire.setPlannerInstanceId(AppConfig.hostId());
        fire.setTriggeredBy(resolveTriggeredBy(context));
        fire.setPartitionIndex(sourceFire.getPartitionIndex());
        fire.setRetryPolicyId(sourceFire.getRetryPolicyId());
        fire.setCreatedBy("system");
        fire.setCreateTime(fireTime);
        fire.setUpdatedBy("system");
        fire.setUpdateTime(fireTime);
        fire.setJobParamsSnapshot(sourceFire.getJobParamsSnapshot());
        fire.setExecutorKind(sourceFire.getExecutorKind());
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
}
