package io.nop.task.state;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.utils.TaskStepHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * plan 349 测试基础设施：全字段 snapshot 语义的 in-memory state store。
 *
 * <p>与 {@link SnapshotTaskStateStore}（只拷贝 reader 依赖字段）的区别：本类额外 round-trip
 * {@code stateBean}（suspend 的 first 标记、loop/fork 的迭代位置）与 {@code bodyStepIndex}，
 * 模拟 plan 349 Phase 6 之后 DaoTaskStateStore 的 stateBean 持久化行为，使 suspend→resume E2E
 * 测试可以在内存 store 上验证引擎契约。
 */
public class FullSnapshotTaskStateStore extends DefaultTaskStateStore {
    private final Map<String, List<ITaskStepState>> stepSnapshots = new HashMap<>();
    private final Map<String, List<ITaskState>> taskSnapshots = new HashMap<>();
    private final Map<String, Integer> taskSaveCounts = new HashMap<>();

    @Override
    public boolean isSupportPersist() {
        return true;
    }

    @Override
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType,
                                        ITaskRuntime taskRt) {
        String parentPath = parentState == null ? null : parentState.getStepPath();
        String stepPath = TaskStepHelper.buildStepPath(parentPath, stepName);
        List<ITaskStepState> history = stepSnapshots.get(stepPath);
        if (history == null || history.isEmpty())
            return null;
        return copyStep(history.get(history.size() - 1));
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {
        ITaskStepState state = stepRt.getState();
        if (state == null || state.getStepPath() == null)
            return;
        stepSnapshots.computeIfAbsent(state.getStepPath(), k -> new ArrayList<>()).add(copyStep(state));
    }

    @Override
    public ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt) {
        List<ITaskState> history = taskSnapshots.get(taskInstanceId);
        if (history == null || history.isEmpty())
            return null;
        return copyTask(history.get(history.size() - 1));
    }

    @Override
    public void saveTaskState(ITaskRuntime taskRt) {
        ITaskState state = taskRt.getTaskState();
        if (state == null || state.getTaskInstanceId() == null)
            return;
        taskSnapshots.computeIfAbsent(state.getTaskInstanceId(), k -> new ArrayList<>()).add(copyTask(state));
        taskSaveCounts.merge(state.getTaskInstanceId(), 1, Integer::sum);
    }

    public ITaskState getLatestTaskSnapshot(String taskInstanceId) {
        List<ITaskState> history = taskSnapshots.get(taskInstanceId);
        if (history == null || history.isEmpty())
            return null;
        return history.get(history.size() - 1);
    }

    public int getTaskSaveCount(String taskInstanceId) {
        return taskSaveCounts.getOrDefault(taskInstanceId, 0);
    }

    public ITaskStepState getLatestStepSnapshot(String stepPath) {
        List<ITaskStepState> history = stepSnapshots.get(stepPath);
        if (history == null || history.isEmpty())
            return null;
        return history.get(history.size() - 1);
    }

    static ITaskStepState copyStep(ITaskStepState src) {
        TaskStepStateBean copy = new TaskStepStateBean();
        copy.setStepInstanceId(src.getStepInstanceId());
        copy.setTaskInstanceId(src.getTaskInstanceId());
        copy.setStepPath(src.getStepPath());
        copy.setRunId(src.getRunId());
        copy.setStepType(src.getStepType());
        copy.setStepStatus(src.getStepStatus());
        copy.setBodyStepIndex(src.getBodyStepIndex());
        copy.setResultValue(src.getResultValue());
        copy.setRetryAttempt(src.getRetryAttempt());
        // stateBean（suspend first 标记 / loop 迭代位置）与 exception 引用一并快照
        copy.setStateBean(src.getStateBean(Object.class));
        copy.exception(src.exception());
        return copy;
    }

    static ITaskState copyTask(ITaskState src) {
        TaskStateBean copy = new TaskStateBean();
        copy.setTaskInstanceId(src.getTaskInstanceId());
        copy.setTaskName(src.getTaskName());
        copy.setTaskVersion(src.getTaskVersion());
        copy.setTaskStatus(src.getTaskStatus());
        copy.setResultValue(src.getResultValue());
        copy.exception(src.exception());
        return copy;
    }
}
