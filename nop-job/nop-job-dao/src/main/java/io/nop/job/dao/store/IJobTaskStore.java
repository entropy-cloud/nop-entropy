package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobTask;

import java.sql.Timestamp;
import java.util.List;

public interface IJobTaskStore {
    boolean updateTask(NopJobTask task);

    List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions);

    /**
     * 带可选 worker 归因过滤的 fetchWaitingTasks 重载。
     * <p>
     * 当 {@code enforceAttribution=true} 时，SQL 追加
     * {@code AND (workerInstanceId = ? OR workerInstanceId IS NULL)}，
     * 用于 dedicated worker 池场景（partition/bestFit 模式下强制分配）。
     * 默认 {@code false} 保留 competing-consumer 行为（向后兼容）。
     */
    List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                       String workerInstanceId, boolean enforceAttribution);

    List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs);

    /**
     * 拉取处于 RUNNING_LIKE 状态（CLAIMED/SUSPICIOUS/RUNNING）的 task，按 {@code startTime DESC, jobTaskId DESC} 排序。
     * 支持游标分页：当 {@code cursorTime} 非空时，追加谓词
     * {@code startTime < cursorTime OR (startTime = cursorTime AND jobTaskId < cursorId)}，
     * 保证本批返回的 row 在排序空间中严格位于 cursor 之前。
     *
     * @param limit      最多返回条数
     * @param partitions 分区过滤
     * @param cursorTime 上一批最后一条的 {@code startTime}；null 表示首批
     * @param cursorId   上一批最后一条的 {@code jobTaskId}；当 {@code cursorTime} 非空时必须配合使用
     * @return 命中的 task 列表，按 {@code startTime DESC, jobTaskId DESC} 排序
     * @throws IllegalArgumentException 若 {@code cursorTime} 为空但 {@code cursorId} 非空
     */
    List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions,
                                       Timestamp cursorTime, String cursorId);

    List<NopJobTask> findTasksByFireId(String jobFireId);

    NopJobTask loadTask(String jobTaskId);

    long countInFlightTasks(String workerInstanceId);

    /**
     * 单 worker 已归因 task cost 聚合求和（WAITING + CLAIMED + SUSPICIOUS + RUNNING）。
     * 用于 worker 侧 {@code JobWorkerScannerImpl.scanOnce} 评估 myRemaining = myCapacity - myReserved。
     * <p>
     * 与 {@link #countInFlightTasks(String)} 状态集故意不对称（design §3.3.4）。
     * 无匹配行时返回 {@link ResourceVector#ZERO}。
     *
     * @param workerInstanceId worker 实例 id（通常为 {@code AppConfig.hostId()}）
     */
    ResourceVector sumReservedCost(String workerInstanceId);

    /**
     * 跨 worker 已归因 task cost 聚合求和，按 workerInstanceId 分组。
     * 用于 dispatcher 侧 WorkerLoad 派生（Plan 215）。
     * <p>
     * worker_instance_id 为 NULL 的历史行不返回。
     */
    java.util.List<io.nop.job.dao.store.WorkerReservedCost> sumReservedCostByWorker();

    /**
     * 重派发超时滞留的 WAITING 任务（AR-88）。筛选
     * {@code taskStatus=WAITING AND createTime < deadline}（含归因给已下线 worker 的任务），
     * 把 {@code workerInstanceId} 置 null（回到 competing-consumer，任意 worker 可认领），
     * 重置 {@code updateTime} 租约；用乐观版本检查避免覆盖已并发流转（CLAIMED/RUNNING）的任务。
     * 不直接判 FAILED——保留可执行机会。
     * <p>
     * 支持游标分页：当 {@code cursorTime} 非空时，在 {@code createTime < deadline} 之上叠加
     * {@code createTime < cursorTime OR (createTime = cursorTime AND jobTaskId < cursorId)}，
     * 保证本批返回的 row 在排序空间中严格位于 cursor 之前。UPDATE 仅置 workerInstanceId=null 但保留
     * taskStatus=WAITING，故行仍匹配 WHERE——cursor 必须推进才能避免重复 fetch。
     *
     * @param batchSize   本轮最多重置的条数
     * @param partitions  分区过滤（与本节点负责分区对齐）
     * @param deadlineMs  createTime 早于该时刻（毫秒）的 WAITING 任务视为超时滞留
     * @param cursorTime  上一批最后一条的 {@code createTime}；null 表示首批
     * @param cursorId    上一批最后一条的 {@code jobTaskId}；当 {@code cursorTime} 非空时必须配合使用
     * @return 被 fetch 出的 stale 列表（实体 workerInstanceId 已被 mutate 为 null，但
     *         createTime/jobTaskId 保留供 caller 推进 cursor）；按 {@code createTime DESC, jobTaskId DESC} 排序
     * @throws IllegalArgumentException 若 {@code cursorTime} 为空但 {@code cursorId} 非空
     */
    List<NopJobTask> resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs,
                                            Timestamp cursorTime, String cursorId);
}
