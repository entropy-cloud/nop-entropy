package io.nop.task.state;

import io.nop.commons.util.StringHelper;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.utils.TaskStepHelper;

/**
 * 不支持任务状态持久化
 */
public class DefaultTaskStateStore implements ITaskStateStore {
    public static DefaultTaskStateStore INSTANCE = new DefaultTaskStateStore();

    @Override
    public boolean isSupportPersist() {
        return false;
    }

    @Override
    public ITaskStepState newMainStepState(ITaskState taskState) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepPath(TaskConstants.MAIN_STEP_NAME);
        state.setRunId(0);
        state.setStepType(TaskConstants.STEP_TYPE_TASK);
        state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);
        return state;
    }

    @Override
    public ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepInstanceId(StringHelper.generateUUID());
        state.setTaskInstanceId(taskRt.getTaskInstanceId());
        String parentPath = parentState == null ? null : parentState.getStepPath();
        state.setStepPath(TaskStepHelper.buildStepPath(parentPath, stepName));
        state.setRunId(taskRt.newRunId());
        state.setStepType(stepType);
        state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);

        if (parentState != null) {
            state.setParentStepPath(parentState.getParentStepPath());
            state.setParentRunId(parentState.getRunId());
        }
        return state;
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
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {

    }

    @Override
    public ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public void saveTaskState(ITaskRuntime taskRt) {

    }
}
