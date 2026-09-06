package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

/**
 * coordinator 侧远程 worker 客户端（plan 2254，dispatchMode=remote）。
 * <p>
 * 两方法模型：{@code startJob} 异步启动（worker 立即返回 taskId=DB jobTaskId，零映射），
 * {@code getJobStatus} 轮询进度/结果（返回平台 {@link TaskStatusBean}）。取消不经此接口——
 * 复用既有取消链（{@code IJobCancelHandler} → {@code nopJobInvoker_rpc} → 远程 cancelJob）。
 * <p>
 * 每次调用注入 {@code nop-svc-target-host = task.targetHost} header，经平台
 * {@code SpecificServiceInstanceFilter} 精确路由到目标 worker 实例。
 */
public interface IRemoteTaskClient {

    /**
     * 异步启动远程任务。返回远程 taskId（= DB jobTaskId）。
     *
     * @throws io.nop.api.core.exceptions.NopException 连接级失败（调用方写回 FAILED）
     */
    String startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);

    /**
     * 查询远程任务状态。返回平台 TaskStatusBean（RUNNING/SUCCESS/FAILURE/CANCELLED/TIMEOUT/NOT_FOUND）。
     */
    TaskStatusBean getJobStatus(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);
}
