package io.nop.task.dao.store;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.AbstractDaoHandler;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.state.TaskStateBean;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.utils.TaskStepHelper;

import java.sql.Timestamp;

import static io.nop.task.dao.entity._gen._NopTaskInstance.PROP_NAME_taskInstanceId;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;

/**
 * DB-backed {@link ITaskStateStore}：基于 {@link NopTaskInstance} + {@link NopTaskStepInstance} 持久化 task / step state。
 *
 * <p>本类闭合 plans 252-257 的 read-side Anti-Hollow 顾虑——plan 257 continuation-skip reader 所依赖的
 * step-state 持久化 + resume load 路径。save→load round-trip 后 {@link ITaskStepState#isDone()} 反映持久化终态
 * （COMPLETED / FAILED），使 reader 在 resume 时命中缓存结果跳过 step body。
 *
 * <p>持久化范围限定为 reader + FAILED-重抛所依赖的字段：{@code stepStatus} / {@code resultValue}（经 JSON 序列化到
 * {@code stateBeanData}）/ {@code exception}（经 {@link ErrorBean} 提取 errCode + errMsg）。全量 step-state 字段
 * 持久化与完整历史 entity 模型为独立优化（plan 257 Non-Goals）。
 */
public class DaoTaskStateStore extends AbstractDaoHandler implements ITaskStateStore {

    protected IEntityDao<NopTaskInstance> taskDao() {
        return daoFor(NopTaskInstance.class);
    }

    protected IEntityDao<NopTaskStepInstance> stepDao() {
        return daoFor(NopTaskStepInstance.class);
    }

    @Override
    public boolean isSupportPersist() {
        return true;
    }

    // ==================== Task state ====================

    @Override
    public ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime taskRt) {
        NopTaskInstance entity = taskDao().newEntity();
        entity.setTaskName(taskName);
        entity.setTaskVersion(taskVersion);
        entity.setTaskGroup(TaskConstants.STATE_ID_DEFAULT);
        entity.setStatus(TaskConstants.TASK_STATUS_ACTIVE);
        entity.setPriority(0);
        entity.setStartTime(CoreMetrics.currentTimestamp());
        taskDao().saveEntityDirectly(entity);

        return toTaskStateBean(entity);
    }

    @Override
    public ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt) {
        NopTaskInstance entity = taskDao().getEntityById(taskInstanceId);
        if (entity == null)
            return null;
        return toTaskStateBean(entity);
    }

    @Override
    public void saveTaskState(ITaskRuntime taskRt) {
        ITaskState state = taskRt.getTaskState();
        NopTaskInstance entity = taskDao().getEntityById(state.getTaskInstanceId());
        boolean isNew = entity == null;
        if (isNew) {
            entity = taskDao().newEntity();
            entity.setTaskInstanceId(state.getTaskInstanceId());
        }
        entity.setTaskName(state.getTaskName());
        if (state.getTaskVersion() != null)
            entity.setTaskVersion(state.getTaskVersion());
        if (state.getTaskStatus() != null)
            entity.setStatus(state.getTaskStatus());
        entity.setUpdateTime(CoreMetrics.currentTimestamp());
        if (isNew)
            taskDao().saveEntityDirectly(entity);
        else
            taskDao().updateEntityDirectly(entity);
    }

    // ==================== Step state ====================

    @Override
    public ITaskStepState newMainStepState(ITaskState taskState) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepInstanceId(StringHelper.generateUUID());
        state.setTaskInstanceId(taskState.getTaskInstanceId());
        state.setStepPath(TaskConstants.MAIN_STEP_NAME);
        state.setRunId(0);
        state.setStepType(TaskConstants.STEP_TYPE_TASK);
        state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);
        return state;
    }

    @Override
    public ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepInstanceId(taskRt.newStepInstanceId());
        state.setTaskInstanceId(taskRt.getTaskInstanceId());
        String parentPath = parentState == null ? null : parentState.getStepPath();
        state.setStepPath(TaskStepHelper.buildStepPath(parentPath, stepName));
        state.setRunId(taskRt.newRunId());
        state.setStepType(stepType);
        state.setStepStatus(TaskConstants.TASK_STEP_STATUS_ACTIVE);

        if (parentState != null) {
            state.setParentStepPath(parentState.getStepPath());
            state.setParentRunId(parentState.getRunId());
        }
        return state;
    }

    @Override
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        String parentPath = parentState == null ? null : parentState.getStepPath();
        String stepPath = TaskStepHelper.buildStepPath(parentPath, stepName);

        NopTaskStepInstance entity = findStepEntity(taskRt.getTaskInstanceId(), stepPath);
        if (entity == null)
            return null;

        TaskStepStateBean state = toStepStateBean(entity);
        state.afterLoad(taskRt);
        return state;
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {
        ITaskStepState state = stepRt.getState();
        NopTaskStepInstance entity = findStepEntity(state.getTaskInstanceId(), state.getStepPath());
        boolean isNew = entity == null;
        if (isNew) {
            entity = stepDao().newEntity();
            entity.setStepInstanceId(StringHelper.isEmpty(state.getStepInstanceId())
                    ? StringHelper.generateUUID() : state.getStepInstanceId());
        }
        state.beforeSave(stepRt.getTaskRuntime());
        copyStepStateToEntity(state, entity);
        if (isNew)
            stepDao().saveEntityDirectly(entity);
        else
            stepDao().updateEntityDirectly(entity);
    }

    // ==================== helpers ====================

    protected NopTaskStepInstance findStepEntity(String taskInstanceId, String stepPath) {
        if (StringHelper.isEmpty(taskInstanceId) || StringHelper.isEmpty(stepPath))
            return null;
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_taskInstanceId, taskInstanceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_stepPath, stepPath));
        return stepDao().findFirstByQuery(query);
    }

    protected TaskStateBean toTaskStateBean(NopTaskInstance entity) {
        TaskStateBean state = new TaskStateBean();
        state.setTaskInstanceId(entity.getTaskInstanceId());
        state.setTaskName(entity.getTaskName());
        if (entity.getTaskVersion() != null)
            state.setTaskVersion(entity.getTaskVersion());
        if (entity.getStatus() != null)
            state.setTaskStatus(entity.getStatus());
        return state;
    }

    protected TaskStepStateBean toStepStateBean(NopTaskStepInstance entity) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepInstanceId(entity.getStepInstanceId());
        state.setTaskInstanceId(entity.getTaskInstanceId());
        state.setStepPath(entity.getStepPath());
        if (entity.getRunId() != null)
            state.setRunId(entity.getRunId());
        state.setStepType(entity.getStepType());
        state.setStepStatus(entity.getStepStatus());
        if (entity.getBodyStepIndex() != null)
            state.setBodyStepIndex(entity.getBodyStepIndex());
        state.setWorkerId(entity.getWorkerId());

        // resultValue 从 stateBeanData 反序列化（JSON）
        String data = entity.getStateBeanData();
        if (!StringHelper.isEmpty(data)) {
            try {
                state.setResultValue(JsonTool.parse(data));
            } catch (Exception e) {
                // 非致命：保留原始文本作为 resultValue（plan 257 Non-Goals：不优化序列化细节）
                state.setResultValue(data);
            }
        }

        // exception 从 errCode + errMsg 重构（plan 257 Non-Goals：不优化 exception 持久化序列化细节）
        String errCode = entity.getErrCode();
        if (!StringHelper.isEmpty(errCode)) {
            NopException exp = new NopException(errCode, null, true, true);
            if (!StringHelper.isEmpty(entity.getErrMsg()))
                exp.description(entity.getErrMsg());
            state.exception(exp);
        }

        if (entity.getRetryCount() != null)
            state.setRetryAttempt(entity.getRetryCount());

        return state;
    }

    protected void copyStepStateToEntity(ITaskStepState state, NopTaskStepInstance entity) {
        entity.setTaskInstanceId(state.getTaskInstanceId());
        String stepType = state.getStepType();
        entity.setStepType(StringHelper.isEmpty(stepType) ? TaskConstants.STEP_TYPE_SIMPLE : stepType);
        String stepName = state.getStepName();
        entity.setStepName(StringHelper.isEmpty(stepName) ? entity.getStepName() : stepName);
        if (StringHelper.isEmpty(entity.getDisplayName()))
            entity.setDisplayName(entity.getStepName());
        entity.setStepStatus(state.getStepStatus() == null
                ? TaskConstants.TASK_STEP_STATUS_ACTIVE : state.getStepStatus());
        String stepPath = state.getStepPath();
        entity.setStepPath(StringHelper.isEmpty(stepPath) ? entity.getStepName() : stepPath);
        entity.setRunId(state.getRunId());
        entity.setBodyStepIndex(state.getBodyStepIndex());
        entity.setPriority(0);
        entity.setWorkerId(state.getWorkerId());
        entity.setRetryCount(state.getRetryAttempt() == null ? 0 : state.getRetryAttempt());

        // resultValue 序列化到 stateBeanData（JSON）
        Object resultValue = state.getResultValue();
        if (resultValue != null) {
            try {
                String json = JsonTool.serialize(resultValue, false);
                if (json != null && json.length() <= 4000)
                    entity.setStateBeanData(json);
            } catch (Exception e) {
                // 非致命：跳过 resultValue 序列化（plan 257 Non-Goals：不优化序列化细节）
            }
        }

        // exception 提取 errCode + errMsg
        Throwable exp = state.exception();
        if (exp != null) {
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false);
            if (errorBean != null) {
                entity.setErrCode(errorBean.getErrorCode());
                entity.setErrMsg(errorBean.getDescription());
            }
        }

        Timestamp now = CoreMetrics.currentTimestamp();
        if (entity.getCreateTime() == null)
            entity.setCreateTime(now);
        entity.setUpdateTime(now);

        // 终态时记录完成时间（reader 消费侧可观测终态生命周期）
        Integer status = state.getStepStatus();
        if (status != null && isTerminalStatus(status)) {
            entity.setFinishTime(now);
        }
    }

    protected boolean isTerminalStatus(int status) {
        return status == _NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED
                || status == _NopTaskCoreConstants.TASK_STEP_STATUS_FAILED
                || status == _NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED
                || status == _NopTaskCoreConstants.TASK_STEP_STATUS_KILLED;
    }
}
