package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IJobFireStore {
    List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions);

    List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions);

    List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                             long lockTimeoutMs);

    void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks);

    void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule);

    boolean cancelFire(String jobFireId);

    NopJobFire loadFire(String jobFireId);

    Map<String, NopJobFire> batchLoadFires(Set<String> fireIds);

    List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions);

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

    void updateRetryRecordId(String jobFireId, String retryRecordId);

    void failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage);
}
