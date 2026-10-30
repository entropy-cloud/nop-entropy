package io.nop.job.coordinator.engine;

import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * rpcPoll 轮询/取消请求的冻结标量快照（plan 2513 1.4-C4-001 裁定①(d)）。
 * <p>
 * 注册时（{@link RpcPollTaskManager#register}）一次性从 schedule/fire/task 三实体提取
 * 轮询与取消 RPC 所需的最小标量集合，此后 {@link RpcPollTaskManager.PollEntry} 不再持有
 * 三实体引用——实体 JSON payload 对象图不被在途条目钉住（C5-005 驻留放大器消除）。
 * 轮询条目终结/取消所需的全部请求参数（header id、targetHost、serviceName 所在的
 * jobParams 等）均由本快照标量构造。
 * <p>
 * 本类为同模块内部视图（package-private），冻结后视为不可变：注册之后 fire/task 实体的
 * 后续变更不反映到在途条目的轮询请求中（与既有「capture 时冻结」语义一致）。
 */
final class RpcPollRequest {
    private final String taskId;
    private final String scheduleId;
    private final String fireId;
    private final String jobName;
    private final String groupId;
    private final String targetHost;
    private final Integer shardingIndex;
    private final Integer shardingTotal;
    private final Long execCount;
    private final Long scheduledFireTime;
    private final Integer timeoutSeconds;
    /** jobParams 冻结浅拷贝（serviceName / headers / data 所在载体）。 */
    private final Map<String, Object> jobParams;

    private RpcPollRequest(String taskId, String scheduleId, String fireId, String jobName, String groupId,
                           String targetHost, Integer shardingIndex, Integer shardingTotal, Long execCount,
                           Long scheduledFireTime, Integer timeoutSeconds, Map<String, Object> jobParams) {
        this.taskId = taskId;
        this.scheduleId = scheduleId;
        this.fireId = fireId;
        this.jobName = jobName;
        this.groupId = groupId;
        this.targetHost = targetHost;
        this.shardingIndex = shardingIndex;
        this.shardingTotal = shardingTotal;
        this.execCount = execCount;
        this.scheduledFireTime = scheduledFireTime;
        this.timeoutSeconds = timeoutSeconds;
        this.jobParams = jobParams;
    }

    /** 注册时从三实体提取标量冻结快照（此后实体引用即可释放）。 */
    static RpcPollRequest of(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
        Map<String, Object> params = resolveJobParams(schedule, fire, task);
        return new RpcPollRequest(
                task.getJobTaskId(),
                schedule != null ? schedule.getJobScheduleId() : null,
                fire != null ? fire.getJobFireId() : null,
                fire != null ? fire.getJobName() : null,
                fire != null ? fire.getGroupId() : null,
                task.getTargetHost(),
                task.getShardingIndex(),
                task.getShardingTotal(),
                schedule != null ? schedule.getFireCount() : null,
                fire != null && fire.getScheduledFireTime() != null ? fire.getScheduledFireTime().getTime() : null,
                schedule != null ? schedule.getTimeoutSeconds() : null,
                params);
    }

    String getTaskId() {
        return taskId;
    }

    String getScheduleId() {
        return scheduleId;
    }

    String getFireId() {
        return fireId;
    }

    String getJobName() {
        return jobName;
    }

    String getGroupId() {
        return groupId;
    }

    String getTargetHost() {
        return targetHost;
    }

    Integer getShardingIndex() {
        return shardingIndex;
    }

    Integer getShardingTotal() {
        return shardingTotal;
    }

    Long getExecCount() {
        return execCount;
    }

    Long getScheduledFireTime() {
        return scheduledFireTime;
    }

    Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    Map<String, Object> getJobParams() {
        return jobParams;
    }

    /**
     * 降级重建轻量实体壳（仅含快照标量，非注册时捕获的实体引用）：供未覆写窄参数方法的
     * {@link IRpcPollTaskClient} 实现回退到实体参数形态——壳对象按调用即建即弃，不驻留。
     */
    NopJobSchedule toScheduleShell() {
        NopJobSchedule shell = new NopJobSchedule();
        shell.setJobScheduleId(scheduleId);
        shell.setFireCount(execCount);
        shell.setTimeoutSeconds(timeoutSeconds);
        if (jobParams != null) {
            shell.getJobParamsComponent().set_jsonMap(jobParams);
        }
        return shell;
    }

    NopJobFire toFireShell() {
        NopJobFire shell = new NopJobFire();
        shell.setJobFireId(fireId);
        shell.setJobName(jobName);
        shell.setGroupId(groupId);
        if (scheduledFireTime != null) {
            shell.setScheduledFireTime(new java.sql.Timestamp(scheduledFireTime));
        }
        if (jobParams != null) {
            shell.getJobParamsSnapshotComponent().set_jsonMap(jobParams);
        }
        return shell;
    }

    NopJobTask toTaskShell() {
        NopJobTask shell = new NopJobTask();
        shell.setJobTaskId(taskId);
        shell.setTargetHost(targetHost);
        shell.setShardingIndex(shardingIndex);
        shell.setShardingTotal(shardingTotal);
        return shell;
    }

    private static Map<String, Object> resolveJobParams(NopJobSchedule schedule, NopJobFire fire,
                                                        NopJobTask task) {
        Map<String, Object> jobParams = task.getEffectiveParams(fire);
        if (jobParams.isEmpty() && schedule != null) {
            Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
            if (scheduleParams != null) {
                jobParams = new LinkedHashMap<>(scheduleParams);
            }
        }
        return jobParams;
    }
}
