package io.nop.job.dao.store;

/**
 * completeFireAndUpdateSchedule / cancelFire 的复合结果，让 caller 区分 fire 与 schedule 各自的更新状态。
 *
 * <p>fire 与 schedule 在 {@code @SingleSession} 下是同一缓存实体，schedule 的版本检查可能因并发修改
 *（如 planner 推进 nextFireTime）而失败。此结果让 caller 显式感知 schedule 失败，而非静默吞掉。
 */
public final class FireScheduleOutcome {
    private final boolean fireUpdated;
    private final boolean scheduleUpdated;

    public FireScheduleOutcome(boolean fireUpdated, boolean scheduleUpdated) {
        this.fireUpdated = fireUpdated;
        this.scheduleUpdated = scheduleUpdated;
    }

    /** fire 状态是否成功更新（版本检查通过） */
    public boolean fireUpdated() {
        return fireUpdated;
    }

    /** schedule 计数字段是否成功更新（版本检查通过） */
    public boolean scheduleUpdated() {
        return scheduleUpdated;
    }

    public static FireScheduleOutcome bothFailed() {
        return new FireScheduleOutcome(false, false);
    }

    public static FireScheduleOutcome fireOnly() {
        return new FireScheduleOutcome(true, false);
    }

    public static FireScheduleOutcome bothUpdated() {
        return new FireScheduleOutcome(true, true);
    }
}
