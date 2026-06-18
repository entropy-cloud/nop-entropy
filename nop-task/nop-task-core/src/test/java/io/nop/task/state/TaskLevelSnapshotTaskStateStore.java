package io.nop.task.state;

import io.nop.commons.util.StringHelper;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 259 测试基础设施：task 级 snapshot 语义的 in-memory state store，模拟 DB round-trip（task envelope 层）。
 *
 * <p>继承 {@link SnapshotTaskStateStore}（plan 258 step 级 snapshot），追加 task 级 {@code saveTaskState}/
 * {@code loadTaskState} 的 snapshot 语义：每次 {@code saveTaskState} 创建当前 task state 的深拷贝 snapshot，
 * 使终态 driver 之后对 live bean 的 mutate 不影响已保存 snapshot。这正是真实
 * {@link io.nop.task.dao.store.DaoTaskStateStore}（DB-backed）的行为。
 *
 * <p>这使 plan 259 的 task 级终态 driver + saveTaskState 接线可观测：
 * <ul>
 *   <li>COMPLETED driver：mainStep 成功 → setTaskStatus(COMPLETED) + result 捕获 → saveTaskState → snapshot COMPLETED</li>
 *   <li>FAILED driver：mainStep 抛错 → setTaskStatus(FAILED) + exception 捕获 → saveTaskState → snapshot FAILED</li>
 *   <li>resume 短路：loadTaskState 取回终态 snapshot → taskRt.isTerminal() → mainStep 不重跑</li>
 * </ul>
 */
public class TaskLevelSnapshotTaskStateStore extends SnapshotTaskStateStore {
    private final Map<String, List<ITaskState>> taskSnapshotHistory = new HashMap<>();
    private final Map<String, Integer> taskSaveCounts = new HashMap<>();

    @Override
    public ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime taskRt) {
        TaskStateBean state = new TaskStateBean();
        state.setTaskName(taskName);
        state.setTaskVersion(taskVersion);
        state.setTaskInstanceId(StringHelper.generateUUID());
        state.setTaskStatus(TaskConstants.TASK_STATUS_ACTIVE);
        return state;
    }

    @Override
    public ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt) {
        List<ITaskState> history = taskSnapshotHistory.get(taskInstanceId);
        if (history == null || history.isEmpty())
            return null;
        // 返回最新 snapshot（深拷贝，非 live 引用）——模拟 fresh DB load
        return copyTaskSnapshot(history.get(history.size() - 1));
    }

    @Override
    public void saveTaskState(ITaskRuntime taskRt) {
        ITaskState state = taskRt.getTaskState();
        if (state == null || state.getTaskInstanceId() == null)
            return;
        String taskInstanceId = state.getTaskInstanceId();
        taskSnapshotHistory.computeIfAbsent(taskInstanceId, k -> new ArrayList<>()).add(copyTaskSnapshot(state));
        taskSaveCounts.merge(taskInstanceId, 1, Integer::sum);
    }

    /**
     * 获取指定 taskInstanceId 的 save 次数（接线验证 #23：终态 driver 出口调用 saveTaskState）。
     */
    public int getTaskSaveCount(String taskInstanceId) {
        return taskSaveCounts.getOrDefault(taskInstanceId, 0);
    }

    /**
     * 获取指定 taskInstanceId 的最新 snapshot。
     */
    public ITaskState getLatestTaskSnapshot(String taskInstanceId) {
        List<ITaskState> history = taskSnapshotHistory.get(taskInstanceId);
        if (history == null || history.isEmpty())
            return null;
        return history.get(history.size() - 1);
    }

    /**
     * 获取指定 taskInstanceId 第 n 次（0-based）save 的 snapshot。
     */
    public ITaskState getTaskSnapshotAt(String taskInstanceId, int index) {
        List<ITaskState> history = taskSnapshotHistory.get(taskInstanceId);
        if (history == null || index < 0 || index >= history.size())
            return null;
        return history.get(index);
    }

    /**
     * plan 260: 预载一个 task snapshot（模拟从 DB load 一个已存在的终态 task，exception 可控），
     * 供 resume 短路区分（FAILED/KILLED/TIMEOUT）与合成 exception 路径的 focused 测试。
     */
    public void preloadTaskSnapshot(String taskInstanceId, ITaskState state) {
        taskSnapshotHistory.computeIfAbsent(taskInstanceId, k -> new ArrayList<>()).add(copyTaskSnapshot(state));
    }

    @Override
    public void reset() {
        super.reset();
        taskSnapshotHistory.clear();
        taskSaveCounts.clear();
    }

    /**
     * 深拷贝 task state snapshot：创建独立 {@link TaskStateBean}，拷贝终态 driver 依赖字段。
     * 关键性质：拷贝后与 live bean 完全解耦，driver 对 live bean 的 mutate 不影响此 snapshot。
     */
    static ITaskState copyTaskSnapshot(ITaskState src) {
        TaskStateBean copy = new TaskStateBean();
        copy.setTaskInstanceId(src.getTaskInstanceId());
        copy.setTaskName(src.getTaskName());
        copy.setTaskVersion(src.getTaskVersion());
        copy.setTaskStatus(src.getTaskStatus());
        copy.setResultValue(src.getResultValue());
        copy.setRequest(src.getRequest());
        copy.setResponse(src.getResponse());
        // exception 为 transient，snapshot 拷贝其引用（resume 短路消费侧检查非 null + errorCode）
        copy.exception(src.exception());
        return copy;
    }
}
