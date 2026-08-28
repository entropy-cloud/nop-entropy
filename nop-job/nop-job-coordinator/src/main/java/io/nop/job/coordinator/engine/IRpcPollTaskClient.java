package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.concurrent.CompletionStage;

/**
 * 三段式（start/poll/cancel）远程执行客户端（plan 2254，executorKind=rpcPoll）。
 * <p>
 * 假定具体的执行端（REST worker）提供分离的多个短 RPC 函数，单次调用必须在一定时间内
 * 返回（长任务在 worker 侧后台线程执行）：
 * <ul>
 *   <li>{@code startJob} 异步启动（worker 立即返回 taskId=DB jobTaskId，零映射）；</li>
 *   <li>{@code getJobStatus} 轮询进度/结果（返回平台 {@link TaskStatusBean}）；</li>
 *   <li>{@code cancelJob} 主动取消（best-effort）。</li>
 * </ul>
 * 三个方法**均为异步**（返回 {@link CompletionStage}）：调用方不阻塞等待 RPC 完成，
 * 因此单个挂起的调用不会独占轮询线程——集中式 {@link RpcPollTaskManager} 据此在同一
 * 注册表上并发出大量在途 getJobStatus。同步校验类错误（如 serviceName 缺失）也经
 * 返回的 future 失败透传，调用方只有一条错误通道。
 * <p>
 * 每次调用注入 {@code nop-svc-target-host = task.targetHost} header，经平台
 * {@code SpecificServiceInstanceFilter} 精确路由到目标 worker 实例。
 */
public interface IRpcPollTaskClient {

    /**
     * 异步启动远程任务。返回远程 taskId（= DB jobTaskId）。
     *
     * @throws io.nop.api.core.exceptions.NopException 连接级失败（future 失败，调用方写回 FAILED）
     */
    CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);

    /**
     * 异步查询远程任务状态。返回平台 TaskStatusBean（RUNNING/SUCCESS/FAILURE/CANCELLED/TIMEOUT/NOT_FOUND）。
     */
    CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);

    /**
     * 异步取消远程任务（best-effort）。返回是否成功发出取消请求。
     */
    CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);
}