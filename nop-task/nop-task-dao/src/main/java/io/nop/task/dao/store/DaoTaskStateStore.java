package io.nop.task.dao.store;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.task.dao.entity._gen._NopTaskInstance.PROP_NAME_taskInstanceId;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;

/**
 * DB-backed {@link ITaskStateStore}：基于 {@link NopTaskInstance} + {@link NopTaskStepInstance} 持久化 task / step state。
 *
 * <p>本类闭合 plans 252-257 的 read-side Anti-Hollow 顾虑——plan 257 continuation-skip reader 所依赖的
 * step-state 持久化 + resume load 路径。save→load round-trip 后 {@link ITaskStepState#isDone()} 反映持久化终态
 * （COMPLETED / FAILED），使 reader 在 resume 时命中缓存结果跳过 step body。
 *
 * <p>持久化范围：reader + FAILED-重抛所依赖的字段（{@code stepStatus} / {@code resultValue}（经 JSON 序列化到
 * {@code stateBeanData}）/ {@code exception}（经 {@link ErrorBean} 提取 errCode + errMsg + 完整 errorBeanData）），
 * 以及既有 entity 列的 round-trip 补齐——{@code internal} + {@code tagSet}（经 {@link TagsHelper} CSV 序列化到
 * {@code tagText}）+ {@code createTime}/{@code updateTime} 时间历史读回（plan 264，闭合 7× carry-over）。
 */
public class DaoTaskStateStore extends AbstractDaoHandler implements ITaskStateStore {

    static final Logger LOG = LoggerFactory.getLogger(DaoTaskStateStore.class);

    /**
     * plan 261: errorBeanData 列允许的最大 JSON 长度（与 step 级 errMsg / stateBeanData precision 对齐）。
     */
    static final int ERROR_BEAN_DATA_MAX_LEN = 4000;

    /**
     * plan 265: errorStack 列允许的最大长度（与 errorBeanData precision 对齐）。
     */
    static final int ERROR_STACK_MAX_LEN = 4000;

    /**
     * plan 265: loaded exception 携带原始 stack 诊断的 param key（设计裁定 4：恢复形式属 execution 裁定，
     * 经 exception param 可观测，对称既有诊断属性传递方式）。plan 266 Phase 2 复用此 key 附加 cause 各层 stack。
     */
    static final String PARAM_ERROR_STACK = "errorStack";

    /**
     * plan 266 Phase 1: errorBeanData 内 reserved param key，编码原始 exception 的 FQCN（精确子类恢复用）。
     * 编入 ErrorBean.params 通路（设计裁定 2：不新增列），rebuild 时读出 consult registry 后过滤，
     * 不污染 loaded exception 的用户可见 params。
     */
    static final String PARAM_EXCEPTION_CLASS = "__exceptionClass";

    /**
     * plan 266 Phase 2: 各 cause 层 stack 的 per-cause 预算（截断到 bounded 长度，适配 4000 总预算）。
     * 复用既有 {@link #extractErrorStack} 截断策略，仅改 maxLen。
     */
    static final int PER_CAUSE_STACK_MAX_LEN = 800;

    /**
     * plan 266 Phase 1: exception 类型注册表（FQCN 字符串键 + 反射构造）。rebuild 路径 consult 此 registry
     * 构造精确子类；未注册 / 缺类 / 历史行回退 generic {@link NopException}。
     */
    private final TaskExceptionRegistry exceptionRegistry = new TaskExceptionRegistry();

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
        // plan 262: task 级 afterLoad hook，对称 step 级 loadStepState:188。在 toTaskStateBean 完成
        // 既有内联 reconstruction（result/exception 恢复）之后、返回之前调用，使 custom task state bean
        // 可在 cross-restart resume 时重建 transient 字段、校验状态一致性。
        TaskStateBean state = toTaskStateBean(entity);
        state.afterLoad(taskRt);
        return state;
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
        // plan 262: task 级 beforeSave hook，对称 step 级 saveStepState:202（在 copyStepStateToEntity 之前）。
        // 在既有状态拷贝到 entity 之前调用，使 custom task state bean 可参与 save 前归一化/清理性写入。
        state.beforeSave(taskRt);
        entity.setTaskName(state.getTaskName());
        if (state.getTaskVersion() != null)
            entity.setTaskVersion(state.getTaskVersion());
        if (state.getTaskStatus() != null)
            entity.setStatus(state.getTaskStatus());
        entity.setUpdateTime(CoreMetrics.currentTimestamp());

        // plan 259 设计裁定 4: 终态 result/exception 持久化，使 cross-restart resume 可恢复并被短路逻辑消费。
        // result（COMPLETED）序列化到 remark（JSON，非致命：超长/序列化失败跳过，与 step 级 stateBeanData 一致）。
        Object resultValue = state.getResultValue();
        if (resultValue != null) {
            try {
                String json = JsonTool.serialize(resultValue, false);
                if (json != null && json.length() <= 4000)
                    entity.setRemark(json);
            } catch (Exception e) {
                // 非致命：跳过 result 序列化（plan Non-Goals：不优化序列化细节）
            }
        }

        // exception（FAILED）提取 errCode + errMsg（镜像 step 级 copyStepStateToEntity exception 持久化）
        // plan 261: 追加完整 ErrorBean JSON（含 params + cause chain）持久化到 errorBeanData，使 cross-restart resume
        // 重抛的 exception 保留诊断属性与 cause chain。errCode + errMsg 仍写入（设计裁定 5：向后兼容 + 简单查询）。
        Throwable exp = state.exception();
        if (exp != null) {
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false);
            if (errorBean != null) {
                entity.setErrCode(errorBean.getErrorCode());
                entity.setErrMsg(errorBean.getDescription());
                String errorBeanJson = serializeErrorBeanData(exp, errorBean);
                if (errorBeanJson != null)
                    entity.setErrorBeanData(errorBeanJson);
            }
            // plan 265: 独立持久化截断后的原始 stack 到 errorStack 列（正交于 errorBeanData——设计裁定 1：既有
            // includeStack=false 的 errorBean 不变、不流入 errorBeanData；stack 经独立 includeStack=true 的 ErrorBean
            // 取得，设计裁定 3：经 public 入口取得因 getStacktrace 为 protected 跨包不可达）。
            String stack = extractErrorStack(exp);
            if (stack != null)
                entity.setErrorStack(stack);
        }

        // 终态时记录完成时间
        Integer status = state.getTaskStatus();
        if (status != null && isTerminalTaskStatus(status)) {
            entity.setEndTime(CoreMetrics.currentTimestamp());
        }

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

    /**
     * plan 263: 按 path {@code @main} 加载持久化的 mainStep envelope 状态。
     *
     * <p>resume 路径（recoverMode=true）下经 {@code TaskRuntimeImpl.newMainStepRuntime} 调用，使 composite mainStep
     * （Sequential/Selector/Loop/LoopN/Graph，经 {@code TaskStepBuilder.buildMainStep} 构造）从其执行中经
     * {@code stepRt.saveState()} 持久化的 {@code bodyStepIndex}（flow 位置）续跑。复用 {@link #toStepStateBean}
     * （已 round-trip {@code bodyStepIndex}/{@code stepStatus}/result/{@code errorBeanData}）+ step 级
     * {@code afterLoad(taskRt)} hook（对称 {@link #loadStepState} 的 afterLoad 调用，plan 262 扩展点）。
     *
     * <p>无持久化行（fresh execute 时 {@code newTaskState} 只写 task instance 行不写 mainStep 行）返回 null，
     * 使调用方回退 {@link #newMainStepState}（零回归）。
     */
    @Override
    public ITaskStepState loadMainStepState(ITaskState taskState, ITaskRuntime taskRt) {
        NopTaskStepInstance entity = findStepEntity(taskState.getTaskInstanceId(), TaskConstants.MAIN_STEP_NAME);
        if (entity == null)
            return null;
        TaskStepStateBean state = toStepStateBean(entity);
        state.afterLoad(taskRt);
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

    /**
     * plan 262: 构造 task state bean 的工厂方法。默认返回 {@link TaskStateBean}，子类可 override 返回可观测
     * 的 custom 子类（如测试 spy），使 {@link #loadTaskState} 的 {@code afterLoad} hook 调用可被 runtime 观测
     * （load 路径 state 经 store 内部构造，外部无法注入可观测实例）。
     */
    protected TaskStateBean newTaskStateBean() {
        return new TaskStateBean();
    }

    protected TaskStateBean toTaskStateBean(NopTaskInstance entity) {
        TaskStateBean state = newTaskStateBean();
        state.setTaskInstanceId(entity.getTaskInstanceId());
        state.setTaskName(entity.getTaskName());
        if (entity.getTaskVersion() != null)
            state.setTaskVersion(entity.getTaskVersion());
        if (entity.getStatus() != null)
            state.setTaskStatus(entity.getStatus());

        // plan 259 设计裁定 4: 终态 result/exception 恢复。result 从 remark 反序列化（JSON）。
        String remark = entity.getRemark();
        if (!StringHelper.isEmpty(remark)) {
            try {
                state.setResultValue(JsonTool.parse(remark));
            } catch (Exception e) {
                // 非致命：保留原始文本作为 resultValue（plan Non-Goals：不优化序列化细节）
                state.setResultValue(remark);
            }
        }

        // plan 261: exception 优先从 errorBeanData 重构（保留 params + cause chain）；为空时回退 errCode + errMsg（兼容历史行）
        // plan 265: 附加从正交 errorStack 列读回的原始 stack 诊断（task 级对称 step 级）。
        NopException exp = loadException(entity.getErrorBeanData(), entity.getErrCode(), entity.getErrMsg(),
                entity.getErrorStack());
        if (exp != null)
            state.exception(exp);

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

        // plan 261: exception 优先从 errorBeanData 重构（保留 params + cause chain）；为空时回退 errCode + errMsg（兼容历史行）
        // plan 265: 附加从正交 errorStack 列读回的原始 stack 诊断（step 级对称 task 级，loadMainStepState 复用此路径）。
        NopException exp = loadException(entity.getErrorBeanData(), entity.getErrCode(), entity.getErrMsg(),
                entity.getErrorStack());
        if (exp != null)
            state.exception(exp);

        if (entity.getRetryCount() != null)
            state.setRetryAttempt(entity.getRetryCount());

        // plan 264: Group A round-trip read — internal + tagText→tagSet（既有 entity 列，闭合 7× carry-over）。
        // TagsHelper.parse 为 TagsHelper.toString 的逆运算，经真实 entity↔bean 边界 round-trip 可逆。
        state.setInternal(entity.getInternal());
        state.setTagSet(TagsHelper.parse(entity.getTagText(), ','));

        // plan 264: Group B 时间历史读回（save 侧 copyStepStateToEntity 已写 createTime/updateTime，
        // 补 load 侧读回，使 loaded bean 反映 entity 已记录的时间历史，消除「写而不读」）。
        if (entity.getCreateTime() != null)
            state.setCreateTime(entity.getCreateTime().toLocalDateTime());
        if (entity.getUpdateTime() != null)
            state.setUpdateTime(entity.getUpdateTime().toLocalDateTime());

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

        // plan 264: Group A round-trip write — internal + tagSet→tagText（既有 entity 列，闭合 7× carry-over）。
        // TagsHelper.toString 产出既定 CSV 格式 `,tag1,tag2,`（与 NopWfStepInstance.getTagSet 同构，设计裁定 2）。
        entity.setInternal(state.getInternal());
        entity.setTagText(TagsHelper.toString(state.getTagSet()));

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
        // plan 261: 追加完整 ErrorBean JSON（含 params + cause chain）持久化到 errorBeanData，使 cross-restart resume
        // 重抛的 exception 保留诊断属性与 cause chain。errCode + errMsg 仍写入（设计裁定 5：向后兼容 + 简单查询）。
        Throwable exp = state.exception();
        if (exp != null) {
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false);
            if (errorBean != null) {
                entity.setErrCode(errorBean.getErrorCode());
                entity.setErrMsg(errorBean.getDescription());
                String errorBeanJson = serializeErrorBeanData(exp, errorBean);
                if (errorBeanJson != null)
                    entity.setErrorBeanData(errorBeanJson);
            }
            // plan 265: 独立持久化截断后的原始 stack 到 errorStack 列（对称 task 级 saveTaskState，正交于 errorBeanData）。
            String stack = extractErrorStack(exp);
            if (stack != null)
                entity.setErrorStack(stack);
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

    /**
     * plan 259: task 级终态判定（基于 {@link TaskConstants} 状态常量，与 step 级不同的状态码体系）。
     */
    protected boolean isTerminalTaskStatus(int status) {
        return status == TaskConstants.TASK_STATUS_COMPLETED
                || status == TaskConstants.TASK_STATUS_KILLED
                || status == TaskConstants.TASK_STATUS_FAILED
                || status == TaskConstants.TASK_STATUS_TIMEOUT;
    }

    // ==================== ErrorBean persistence helpers (plan 261) ====================
    //
    // 闭合 cross-restart exception 持久化的 transient lossy gap：save 时序列化完整 ErrorBean（params + cause chain）
    // 到 errorBeanData 列，load 时优先从 errorBeanData 重构含 params + cause chain 的 NopException，使 resume 重抛的
    // exception 诊断信息与 in-process 执行时对齐。

    /**
     * plan 265: 从 exception 提取截断后的原始 stack 字符串，用于持久化到正交 errorStack 列。
     *
     * <p>设计裁定 1/3：与「喂给 {@link #serializeErrorBeanData} 的 errorBean」分离的 stack 来源——额外独立调用一次
     * {@code defaultBuildErrorMessage(null, exp, true)}（public，includeStack=true，内部调 {@code getStacktrace} 截前 5 帧），
     * 从返回 ErrorBean 的 {@link ErrorBean#getErrorStack()} 取 stack 字符串。{@code ErrorMessageManager.getStacktrace} 为
     * {@code protected} 且位于 {@code io.nop.core.exceptions}，本类（{@code io.nop.task.dao.store}）跨包不可直接调用，故经
     * public 入口间接获取。此额外 build 的 ErrorBean <b>只用于取 errorStack，绝不传入 serializeErrorBeanData</b>（设计裁定 1），
     * 保证 stack 不流入 errorBeanData JSON。
     *
     * <p>截断到 {@link #ERROR_STACK_MAX_LEN}（极端边界，非致命——对称既有 stateBeanData/errorBeanData 非致命跳过模式，
     * Minimum Rules #24：取 stack 失败有日志不静默吞掉）。
     */
    protected String extractErrorStack(Throwable exp) {
        return extractErrorStack(exp, ERROR_STACK_MAX_LEN);
    }

    /**
     * plan 265 + plan 266 Phase 2: 从 exception 提取截断后的原始 stack 字符串。
     *
     * <p>{@code maxLen} 控制截断长度：top-level 用 {@link #ERROR_STACK_MAX_LEN}（4000，写入独立 errorStack 列），
     * cause 各层用 {@link #PER_CAUSE_STACK_MAX_LEN}（800，编入 errorBeanData 各 cause ErrorBean.errorStack 字段）。
     *
     * <p>设计裁定 1/3（plan 265）：与「喂给 {@link #serializeErrorBeanData} 的 errorBean」分离的 stack 来源——额外独立调用一次
     * {@code defaultBuildErrorMessage(null, exp, true)}（public，includeStack=true，内部调 {@code getStacktrace} 截前 5 帧），
     * 从返回 ErrorBean 的 {@link ErrorBean#getErrorStack()} 取 stack 字符串。
     *
     * <p>NopException 的 XPL stack（getXplStack）为 null 时，平台 getStacktrace 的 NopException 分支会 NPE
     * （`!stack.isEmpty()` 对 null 求值）——此为平台既有行为（nop-core，非本计划 scope）。NopException 无 xplStack
     * 即无 XPL 诊断栈可捕获，故直接返回 null。非 NopException / NopException-with-xplStack 走 public includeStack=true build。
     * 截断失败有日志不静默吞掉（Minimum Rules #24）。
     */
    protected String extractErrorStack(Throwable exp, int maxLen) {
        if (exp == null)
            return null;
        if (exp instanceof NopException && ((NopException) exp).getXplStack() == null)
            return null;
        try {
            ErrorBean stackBean = ErrorMessageManager.instance().defaultBuildErrorMessage(null, exp, true);
            if (stackBean == null)
                return null;
            String stack = stackBean.getErrorStack();
            if (StringHelper.isEmpty(stack))
                return null;
            if (stack.length() > maxLen)
                stack = stack.substring(0, maxLen);
            return stack;
        } catch (Exception e) {
            LOG.warn("nop.task.extract-error-stack-failed:skip persisting errorStack", e);
            return null;
        }
    }

    /**
     * plan 261 + plan 266 Phase 1/2: 序列化完整 ErrorBean（params + cause chain + 各层 FQCN + cause 各层 stack）为 JSON。
     *
     * <p>{@code ErrorMessageManager.buildErrorMessage} 仅在存在 {@code includeCause=true} 的 ErrorCodeMapping 时才填充
     * {@link ErrorBean#getCause()}，普通场景下 cause 为 null。此处从 {@code throwable.getCause()} 递归补充 cause chain，
     * 使 cross-restart 持久化保留完整诊断链。
     *
     * <p>plan 266 Phase 1: 各层 ErrorBean 追加 reserved param {@code __exceptionClass}（FQCN），rebuild 时 consult
     * registry 恢复精确子类（设计裁定 2：经 params 通路编码，不新增列）。
     *
     * <p>plan 266 Phase 2: 各 cause 层 ErrorBean 追加截断 stack（{@code errorStack} 字段，{@link #PER_CAUSE_STACK_MAX_LEN}
     * 预算）。top-level bean 的 errorStack 仍为 null（plan 265 契约：top-level stack 在独立 errorStack 列）。
     *
     * <p>budget-aware 序列化（设计裁定 5）：超 {@link #ERROR_BEAN_DATA_MAX_LEN} 时<b>优先剥离 cause 层 stack</b>重试
     * （绝不因 cause stack 挤占而丢弃 params+cause）；剥离后仍超长（如 params 本身巨大）→ 返回 null（非致命跳过，
     * 调用方仅写 errCode + errMsg，行为与 plan 261/265 一致，#24 有日志）。
     */
    protected String serializeErrorBeanData(Throwable exp, ErrorBean errorBean) {
        if (errorBean == null || StringHelper.isEmpty(errorBean.getErrorCode()))
            return null;
        ErrorBean full = errorBean.cloneInstance();
        // Phase 1: top-level FQCN
        captureExceptionClass(full, exp);
        if (full.getCause() == null)
            full.setCause(buildCauseErrorBean(exp.getCause()));

        // 第一次尝试：含 cause 各层 stack
        String json = trySerialize(full);
        if (json != null)
            return json;

        // budget-aware：超 4000 → 剥离 cause 各层 stack 重试（保留 params+cause，设计裁定 5）。
        // full 为局部变量不再复用，in-place 剥离安全。
        stripCauseStacksInPlace(full);
        json = trySerialize(full);
        if (json != null)
            return json;

        // 仍超长（params 本身巨大）→ total-discard fallback（行为与 plan 261 一致）
        LOG.warn("nop.task.error-bean-data-too-long:skip persisting errorBeanData,errCode={},length>{}",
                errorBean.getErrorCode(), ERROR_BEAN_DATA_MAX_LEN);
        return null;
    }

    /**
     * 序列化尝试：成功（<= cap）返回 JSON，否则返回 null（不记日志，由调用方决定 fallback 策略）。
     */
    private String trySerialize(ErrorBean bean) {
        try {
            String json = JsonTool.serialize(bean, false);
            if (json != null && json.length() <= ERROR_BEAN_DATA_MAX_LEN)
                return json;
        } catch (Exception e) {
            LOG.warn("nop.task.serialize-error-bean-failed:skip persisting errorBeanData,errCode={}",
                    bean.getErrorCode(), e);
        }
        return null;
    }

    /**
     * plan 266 Phase 1: 把 exception FQCN 编入 ErrorBean 的 reserved param（{@code __exceptionClass}）。
     * 复制 params map 避免 polluting 原始 exception 的 params（cloneInstance/ defaultBuildErrorMessage 共享 map 引用）。
     */
    private void captureExceptionClass(ErrorBean bean, Throwable exp) {
        if (exp == null)
            return;
        Map<String, Object> params = bean.getParams();
        if (params != null)
            bean.setParams(new LinkedHashMap<>(params));
        bean.param(PARAM_EXCEPTION_CLASS, exp.getClass().getName());
    }

    /**
     * plan 266 Phase 2: 剥离 ErrorBean 链各 cause 层的 errorStack（budget-aware 降级，设计裁定 5）。
     * top-level 的 errorStack 不动（plan 265 契约下 top-level 为 null，此操作 no-op）。
     */
    private void stripCauseStacksInPlace(ErrorBean bean) {
        ErrorBean cause = bean.getCause();
        while (cause != null) {
            if (!StringHelper.isEmpty(cause.getErrorStack()))
                cause.setErrorStack(null);
            cause = cause.getCause();
        }
    }

    /**
     * plan 261 + plan 266 Phase 1/2: 递归从 throwable 的 cause chain 构建 {@link ErrorBean} 链。
     *
     * <p>复用 {@link ErrorMessageManager#defaultBuildErrorMessage} 提取每层 cause 的 errorCode / description / params。
     * Java {@link Throwable} 不允许循环 cause（initCause 会检测），递归安全。
     *
     * <p>plan 266 Phase 1: 各 cause 层追加 FQCN（{@code __exceptionClass} reserved param）。
     * <p>plan 266 Phase 2: 各 cause 层追加截断 stack（{@code errorStack} 字段，{@link #PER_CAUSE_STACK_MAX_LEN} 预算），
     * 复用 {@link #extractErrorStack(Throwable, int)} 截断策略。stack 捕获失败非致命（降级无 stack，#24 有日志）。
     */
    protected ErrorBean buildCauseErrorBean(Throwable cause) {
        if (cause == null)
            return null;
        Throwable next = cause.getCause();
        if (cause == next)
            return null;
        ErrorBean causeBean = ErrorMessageManager.instance().defaultBuildErrorMessage(null, cause, false);
        // Phase 1: cause FQCN
        captureExceptionClass(causeBean, cause);
        // Phase 2: cause 层 stack（budget-aware，截断到 per-cause 预算）
        String causeStack = extractErrorStack(cause, PER_CAUSE_STACK_MAX_LEN);
        if (causeStack != null)
            causeBean.setErrorStack(causeStack);
        causeBean.setCause(buildCauseErrorBean(next));
        return causeBean;
    }

    /**
     * plan 261: 从 entity 的 errorBeanData / errCode / errMsg 重构 exception。
     *
     * <p>优先从 {@code errorBeanData}（完整 ErrorBean JSON）重构（保留 params + cause chain）；为 null 或解析失败时回退
     * errCode + errMsg 重构（兼容历史行，行为与 plan 258/259 一致）。解析失败有日志（非静默吞掉——Minimum Rules #24）。
     *
     * <p>plan 265: {@code errorStack}（从正交 errorStack 列读回）非空时附加到重构的 exception 上，使 cross-restart
     * resume 重抛的 exception 暴露原始失败位置的 stack 诊断（设计裁定 4：errorStack 经新列读回，errorBeanData 处理逻辑不动）。
     *
     * @return 重构的 exception；errCode 与 errorBeanData 均为空时返回 null
     */
    protected NopException loadException(String errorBeanData, String errCode, String errMsg, String errorStack) {
        NopException exp = null;
        if (!StringHelper.isEmpty(errorBeanData)) {
            try {
                ErrorBean errorBean = JsonTool.parseBeanFromText(errorBeanData, ErrorBean.class);
                if (errorBean != null && !StringHelper.isEmpty(errorBean.getErrorCode()))
                    exp = rebuildExceptionFromErrorBean(errorBean);
            } catch (Exception e) {
                LOG.warn("nop.task.parse-error-bean-failed:fallback to errCode+errMsg,errCode={}", errCode, e);
            }
        }
        if (exp == null && !StringHelper.isEmpty(errCode)) {
            exp = new NopException(errCode, null, true, true);
            if (!StringHelper.isEmpty(errMsg))
                exp.description(errMsg);
        }
        // plan 265: 附加从正交 errorStack 列读回的原始 stack 诊断（设计裁定 4，经 exception param 可观测）。
        if (exp != null && !StringHelper.isEmpty(errorStack))
            exp.param(PARAM_ERROR_STACK, errorStack);
        return exp;
    }

    /**
     * plan 261 设计裁定 3 选项 (b) + plan 266 Phase 1/2：从 {@link ErrorBean} 递归重构含 params + cause chain 的 {@link NopException}。
     *
     * <p>{@link io.nop.api.core.exceptions.NopRebuildException#rebuild(ErrorBean)}（kernel public static，全仓库多处调用）
     * 重构时 cause 传 null，不恢复 cause chain。增强它会改变所有调用方行为，属跨模块公共 API 变更，不在本计划 scope。
     * 故在 DaoTaskStateStore 内部递归构造并通过 {@link Throwable#initCause(Throwable)} 恢复 cause chain，
     * 将变更限定在 nop-task-dao 模块内。
     *
     * <p>plan 266 Phase 1: consult {@link #exceptionRegistry} 恢复精确子类（从 reserved param {@code __exceptionClass}
     * 读 FQCN）。命中 → 构造精确子类；未注册 / 缺类 / 无 FQCN（历史行）→ 回退 generic {@link NopException}（向后兼容）。
     * reserved FQCN param 过滤后不污染 loaded exception 的用户可见 params。
     *
     * <p>plan 266 Phase 2: 各 cause 层 ErrorBean 的 {@code errorStack} 字段非空时附加到该层 exception 的
     * {@code errorStack} param（对称 plan 265 top-level PARAM_ERROR_STACK 模式）。top-level bean 无 errorStack
     * （includeStack=false，plan 265 契约），top-level stack 由 {@code loadException} 从独立 errorStack 列附加。
     */
    protected NopException rebuildExceptionFromErrorBean(ErrorBean errorBean) {
        // 先递归构造 cause（如有），再直接传入构造器。
        // 不可先构造 null-cause 再调 initCause：Throwable(String, Throwable, boolean, boolean) 构造器即使传 null
        // 也会将 cause 标记为「已初始化」，导致后续 initCause 抛 IllegalStateException("Can't overwrite cause")。
        // 故将 cause 经构造器传入，绕过 initCause 的状态检查。
        ErrorBean causeBean = errorBean.getCause();
        Throwable cause = null;
        if (causeBean != null && !StringHelper.isEmpty(causeBean.getErrorCode())) {
            cause = rebuildExceptionFromErrorBean(causeBean);
        }

        String errorCode = errorBean.getErrorCode();

        // Phase 1: consult registry 恢复精确子类（reserved param 读 FQCN）
        String fqcn = readExceptionClass(errorBean);
        NopException exp = exceptionRegistry.create(fqcn, errorCode, cause);
        if (exp == null) {
            // 未注册 / 缺类 / 历史 FQCN → 回退 generic NopException（向后兼容，行为与 plan 261 一致）
            exp = new NopException(errorCode, cause, true, true);
        }

        if (!StringHelper.isEmpty(errorBean.getDescription()))
            exp.description(errorBean.getDescription());

        // Phase 1: 过滤 reserved FQCN param 后应用其余 params（不污染用户可见 params）
        Map<String, Object> params = errorBean.getParams();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                if (PARAM_EXCEPTION_CLASS.equals(key))
                    continue;
                exp.param(key, entry.getValue());
            }
        }

        if (errorBean.isBizFatal())
            exp.bizFatal(errorBean.isBizFatal());

        // Phase 2: cause 各层 stack 附加到该层 exception（top-level bean 无 errorStack → 跳过，plan 265 契约）
        if (!StringHelper.isEmpty(errorBean.getErrorStack()))
            exp.param(PARAM_ERROR_STACK, errorBean.getErrorStack());

        return exp;
    }

    /**
     * plan 266 Phase 1: 从 ErrorBean params 读回 reserved FQCN（save 侧 {@link #captureExceptionClass} 编入）。
     */
    private String readExceptionClass(ErrorBean bean) {
        Map<String, Object> params = bean.getParams();
        if (params == null)
            return null;
        Object val = params.get(PARAM_EXCEPTION_CLASS);
        return val == null ? null : val.toString();
    }
}
