package io.nop.task.state;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.utils.TaskStepHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 258 测试基础设施：snapshot 语义的 in-memory state store，模拟 DB round-trip。
 *
 * <p>与 plan 257 的 {@code ResumeCapableTaskStateStore}（引用语义）的根本区别：
 * 每次 {@code saveStepState} 创建当前 state 的<strong>深拷贝 snapshot</strong>（非引用），
 * 使 save 之后 driver 对 live bean 的原地 mutate <strong>不影响</strong>已保存的 snapshot。
 * 这正是真实 {@link io.nop.task.dao.store.DaoTaskStateStore}（DB-backed）的行为——
 * DB round-trip 取回的是 save 时刻的 snapshot，非 driver mutate 后的引用。
 *
 * <p>因此本类能暴露 plan 258 要修的 production gap：
 * <ul>
 *   <li>无终态 save（pre-fix）：ACTIVE-time save 保存 ACTIVE snapshot，driver 之后 mutate live bean 到 COMPLETED/FAILED，
 *       但 snapshot 仍是 ACTIVE → resume load 取回 ACTIVE → reader isDone false → step 重执行。</li>
 *   <li>有终态 save（post-fix）：终态 driver 后追加 save，snapshot 被覆盖为 COMPLETED/FAILED →
 *       resume load 取回终态 → reader isDone true → 跳过 step body。</li>
 * </ul>
 *
 * <p>同时记录每次 save 的 snapshot 历史 + save 计数，使接线验证（#23）可观测：
 * ACTIVE 1 次 + 终态 1 次 = 2 次 save。
 */
public class SnapshotTaskStateStore extends DefaultTaskStateStore {
    private final Map<String, List<ITaskStepState>> snapshotHistory = new HashMap<>();
    private final Map<String, Integer> saveCounts = new HashMap<>();

    @Override
    public boolean isSupportPersist() {
        return true;
    }

    @Override
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType,
                                        ITaskRuntime taskRt) {
        String parentPath = parentState == null ? null : parentState.getStepPath();
        String stepPath = TaskStepHelper.buildStepPath(parentPath, stepName);
        List<ITaskStepState> history = snapshotHistory.get(stepPath);
        if (history == null || history.isEmpty())
            return null;
        // 返回最新 snapshot（深拷贝，非 live 引用）
        return copySnapshot(history.get(history.size() - 1));
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {
        ITaskStepState state = stepRt.getState();
        if (state == null || state.getStepPath() == null)
            return;
        String stepPath = state.getStepPath();
        snapshotHistory.computeIfAbsent(stepPath, k -> new ArrayList<>()).add(copySnapshot(state));
        saveCounts.merge(stepPath, 1, Integer::sum);
    }

    /**
     * 获取指定 stepPath 的 save 次数（接线验证 #23：ACTIVE 1 次 + 终态 1 次 = 2 次）。
     */
    public int getSaveCount(String stepPath) {
        return saveCounts.getOrDefault(stepPath, 0);
    }

    /**
     * 获取指定 stepPath 第 n 次（0-based）save 的 snapshot（用于证明 ACTIVE 先于终态被保存）。
     */
    public ITaskStepState getSnapshotAt(String stepPath, int index) {
        List<ITaskStepState> history = snapshotHistory.get(stepPath);
        if (history == null || index < 0 || index >= history.size())
            return null;
        return history.get(index);
    }

    /**
     * 获取指定 stepPath 的最新 snapshot。
     */
    public ITaskStepState getLatestSnapshot(String stepPath) {
        List<ITaskStepState> history = snapshotHistory.get(stepPath);
        if (history == null || history.isEmpty())
            return null;
        return history.get(history.size() - 1);
    }

    public void reset() {
        snapshotHistory.clear();
        saveCounts.clear();
    }

    /**
     * 深拷贝 snapshot：创建独立 {@link TaskStepStateBean}，拷贝 reader 依赖字段（stepStatus/resultValue/exception/stepPath）。
     * 关键性质：拷贝后与 live bean 完全解耦，driver 对 live bean 的 mutate 不影响此 snapshot。
     */
    static ITaskStepState copySnapshot(ITaskStepState src) {
        TaskStepStateBean copy = new TaskStepStateBean();
        copy.setStepInstanceId(src.getStepInstanceId());
        copy.setTaskInstanceId(src.getTaskInstanceId());
        copy.setStepPath(src.getStepPath());
        copy.setRunId(src.getRunId());
        copy.setStepType(src.getStepType());
        copy.setStepStatus(src.getStepStatus());
        copy.setResultValue(src.getResultValue());
        copy.setRetryAttempt(src.getRetryAttempt());
        // exception 为 transient，snapshot 拷贝其引用（reader 消费侧检查非 null + errorCode）
        copy.exception(src.exception());
        return copy;
    }
}
