package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IJobFireStore {
    List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions);

    List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions);

    List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                             long lockTimeoutMs);

    /**
     * 插入任务行并把 fire 从 DISPATCHING 推进到 RUNNING。
     *
     * @return {@code true} 表示实际插入并推进；{@code false} 表示 fire 已被并发流转出
     * DISPATCHING（如 revert/timeout），本调用静默跳过、未插入任何任务行。
     * 调用方（dispatcher）据此决定是否计入 dispatched 指标（check2 [P3-11]）。
     */
    boolean insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks);

    FireScheduleOutcome completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule);

    FireScheduleOutcome cancelFire(String jobFireId);

    NopJobFire loadFire(String jobFireId);

    NopJobFire getFireById(String jobFireId);

    Map<String, NopJobFire> batchLoadFires(Set<String> fireIds);

    /**
     * 拉取处于 DISPATCHING 状态的 fire，按 {@code startTime DESC, jobFireId DESC} 排序。
     * 支持游标分页：当 {@code cursorTime} 非空时，追加谓词
     * {@code startTime < cursorTime OR (startTime = cursorTime AND jobFireId < cursorId)}，
     * 保证本批返回的 row 在排序空间中严格位于 cursor 之前。
     *
     * @param limit      最多返回条数
     * @param partitions 分区过滤
     * @param cursorTime 上一批最后一条的 {@code startTime}；null 表示首批
     * @param cursorId   上一批最后一条的 {@code jobFireId}；当 {@code cursorTime} 非空时必须配合使用
     * @return 命中的 fire 列表，按 {@code startTime DESC, jobFireId DESC} 排序
     * @throws IllegalArgumentException 若 {@code cursorTime} 为空但 {@code cursorId} 非空
     */
    List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions,
                                           Timestamp cursorTime, String cursorId);

    /**
     * 把一个 DISPATCHING 状态的 fire 回退为 WAITING（重新可派发），用于 no-fitting-worker
     *（worker 满载的正常瞬态）的 defer（AR-86）。用乐观版本检查避免覆盖已并发流转的 fire。
     *
     * <p>{@code backoffUntilMs} 写入 fire 的 {@code startTime} 作为"最早可重新派发"截止
     *（WAITING 状态下 startTime 语义复用为 backoff-until）；{@code fetchWaitingFires} 会跳过
     * startTime 仍在未来的 WAITING fire，避免 DISPATCHING→WAITING→DISPATCHING 紧循环。
     *
     * @param fire           要回退的 fire（按 id 重载获取最新版本）
     * @param backoffUntilMs 回退后的"最早可重新派发"时刻（毫秒）
     * @return true 若版本检查通过并成功回退；false 若 fire 已不在 DISPATCHING（并发流转）
     */
    boolean revertDispatchingFireToWaiting(NopJobFire fire, long backoffUntilMs);

    /**
     * @return fire是否成功置为FAILED终态。false=版本冲突（已被并发推进终态），
     *         调用方据此决定是否继续连带取消任务（Bug C fix对称防护）
     */
    boolean failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage);
}
