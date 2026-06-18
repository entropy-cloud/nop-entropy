package io.nop.task.ext.reliability;

import io.nop.commons.util.StringHelper;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.state.TaskStateBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 259 Phase 2 cross-restart resume E2E 基础设施：task 级 snapshot 语义的 in-memory state store。
 *
 * <p>继承 {@link SnapshotResumeTaskStateStore}（plan 258 step 级 snapshot），追加 task 级
 * {@code newTaskState}/{@code loadTaskState}/{@code saveTaskState} 的 snapshot 语义。
 * 每次 {@code saveTaskState} 创建当前 task state 的深拷贝 snapshot，使终态 driver 之后对 live bean
 * 的 mutate 不影响已保存 snapshot。这正是真实 {@link io.nop.task.dao.store.DaoTaskStateStore}（DB-backed）的行为。
 *
 * <p>这使得 plan 259 的 cross-restart resume E2E 可观测：
 * <ul>
 *   <li>fresh execute（saveState=true 经 persist store）：newTaskState ACTIVE → execute → 终态 driver → saveTaskState snapshot 终态</li>
 *   <li>resume（getTaskRuntime 经 persist store）：loadTaskState 取回终态 snapshot → recoverMode=true → execute 短路 → mainStep 不重跑</li>
 * </ul>
 */
public class TaskResumeSnapshotTaskStateStore extends SnapshotResumeTaskStateStore {
    public static final String BEAN_NAME = "testTaskResumeSnapshotStore";

    private final Map<String, List<ITaskState>> taskSnapshotHistory = new HashMap<>();
    private final Map<String, Integer> taskSaveCounts = new HashMap<>();

    @Override
    public boolean isSupportPersist() {
        return true;
    }

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
     * 手动注入一个 task state snapshot（用于模拟 in-progress / 非终态 task 的 resume 场景）。
     */
    public void injectTaskSnapshot(ITaskState state) {
        if (state == null || state.getTaskInstanceId() == null)
            return;
        taskSnapshotHistory.computeIfAbsent(state.getTaskInstanceId(), k -> new ArrayList<>())
                .add(copyTaskSnapshot(state));
        taskSaveCounts.merge(state.getTaskInstanceId(), 1, Integer::sum);
    }

    @Override
    public void reset() {
        super.reset();
        taskSnapshotHistory.clear();
        taskSaveCounts.clear();
    }

    /**
     * 深拷贝 task state snapshot：创建独立 {@link TaskStateBean}，拷贝终态 driver 依赖字段。
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
        copy.exception(src.exception());
        return copy;
    }
}
