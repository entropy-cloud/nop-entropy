
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;

import io.nop.job.biz.INopJobFireBiz;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.coordinator.engine.IJobCancelHandler;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobFireStateMachine;
import io.nop.job.dao.helper.JobScheduleStateMachine;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.service.JobContextHelper;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

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
    protected IJobCancelHandler cancelHandler;

    public NopJobFireBizModel(){
        setEntityName(NopJobFire.class.getName());
    }

    /**
     * plan 2254: cancel 接线所需的 task 快照来源。BizModel bean 的 IoC 注入/装配对 backing
     * 实例不生效（_service.beans.xml 的 BizModel bean 不声明 property），因此按类型懒获取
     * （BeanContainer，容器中 IJobTaskStore bean 由 app-dao.beans.xml 装配）。
     */
    private IJobTaskStore taskStore() {
        return BeanContainer.getBeanByType(IJobTaskStore.class);
    }

    /**
     * plan 2254: 手动取消链接线——cancelFire 成功后对 in-flight 任务调用 cancelHandler
     * （coordinator 侧既有组件，超时路径已在用），经 executorKind 解析 invoker 通知 worker
     * 主动中断（executorKind=rpcPoll → RemoteJobInvoker.cancelAsync → 远程 cancelJob）。
     * 普通 setter（无 @Inject，仿 JobTimeoutCheckerImpl.setNamingService 可选注入先例）；
     * **生产装配**：容器存在 IJobCancelHandler bean（coordinator 部署 app-engine.beans.xml）
     * 时经 {@link #cancelHandler()} 懒取自动生效；测试可经反射注入 mock 覆盖。
     */
    public void setCancelHandler(IJobCancelHandler cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    private IJobCancelHandler cancelHandler() {
        if (cancelHandler != null) {
            return cancelHandler;
        }
        Object bean = BeanContainer.instance().tryGetBeanByType(IJobCancelHandler.class);
        return bean instanceof IJobCancelHandler ? (IJobCancelHandler) bean : null;
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

        // plan 2254: cancelFire 事务会把活动 task 全部置 CANCELED，事后加载为空——
        // 必须在调用前捕获 in-flight task 快照，用于取消后通知执行端主动中断（best-effort）。
        List<NopJobTask> inFlightTasks = taskStore().findTasksByFireId(id);

        FireScheduleOutcome outcome = fireStore.cancelFire(id);
        if (!outcome.fireUpdated()) {
            throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
        }
        if (!outcome.scheduleUpdated()) {
            LOG.warn("nop.job.cancel.schedule-counter-not-updated:fireId={}", id);
        }

        notifyCancel(inFlightTasks, fire, context);

        afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
    }

    /**
     * 取消后通知执行端主动中断（best-effort）：仅通知 in-flight（RUNNING_LIKE）任务，
     * 依赖注入的 cancelHandler（未装配则跳过）。DB 状态已 CANCELED，通知失败不影响结果。
     */
    private void notifyCancel(List<NopJobTask> tasks, NopJobFire fire, IServiceContext context) {
        IJobCancelHandler handler = cancelHandler();
        if (handler == null || tasks == null || tasks.isEmpty()) {
            return;
        }
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        for (NopJobTask task : tasks) {
            if (!JobTaskStateMachine.isInFlight(task.getTaskStatus())) {
                continue;
            }
            try {
                handler.cancelRunningTask(schedule, fire, task);
            } catch (Exception e) {
                LOG.warn("nop.job.cancel.notify-failed:fireId={},taskId={}",
                        fire.getJobFireId(), task.getJobTaskId(), e);
            }
        }
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
        fire.setSourceFireId(sourceFire.getJobFireId());
        fire.setScheduledFireTime(fireTime);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        fire.setPlannerInstanceId(AppConfig.hostId());
        fire.setTriggeredBy(JobContextHelper.resolveTriggeredBy(context));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setRetryPolicyId(schedule.getRetryPolicyId());
        fire.setJobParamsSnapshot(schedule.getJobParams());
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setDispatchMode(schedule.getDispatchMode());
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
