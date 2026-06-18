package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskConstants.TASK_STATUS_FAILED;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_taskInstanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 261 Phase 2 focused 测试：验证 {@link DaoTaskStateStore} 的 save→load round-trip 能完整持久化并取回
 * 终态 exception 的诊断属性（{@code .param(...)}）与 cause chain，使 cross-restart resume 重抛的 exception
 * 与 in-process 执行时诊断信息对齐。
 *
 * <p>所有用例均使用真实 DB-backed {@link DaoTaskStateStore}（{@code @NopTestConfig(localDb=true)}），
 * exception 经 entity JSON 序列化往返，非 in-memory snapshot 引用直接拷贝。
 *
 * <p>闭合 plan 261 的核心诊断增量：plan 258/259 前的 load 路径只从 errCode + errMsg 重构泛型 NopException，
 * params + cause 全部丢失；本计划新增 {@code errorBeanData} 列持久化完整 {@link ErrorBean} JSON。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreErrorBeanRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;

    static final ErrorCode ERR_PLAN_261_MAIN =
            ErrorCode.define("nop.err.test.plan-261.main", "plan 261 main exception");
    static final ErrorCode ERR_PLAN_261_CAUSE =
            ErrorCode.define("nop.err.test.plan-261.cause", "plan 261 cause exception");
    static final ErrorCode ERR_PLAN_261_COMPAT =
            ErrorCode.define("nop.err.test.plan-261.compat", "plan 261 backward-compat fallback");

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testErrorBeanTask", 0, runtime);
        runtime.setTaskState(taskState);
        taskRt = runtime;
    }

    private ITaskStepRuntime newStepRt(String stepName) {
        ITaskStepState state = store.newStepState(null, stepName, "xpl", taskRt);
        TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(taskRt, store, XLang.newEvalScope());
        stepRt.setState(state);
        return stepRt;
    }

    private NopTaskStepInstance loadStepEntity(String taskInstanceId, String stepPath) {
        IEntityDao<NopTaskStepInstance> dao = daoProvider.daoFor(NopTaskStepInstance.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_taskInstanceId, taskInstanceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_stepPath, stepPath));
        return dao.findFirstByQuery(query);
    }

    private NopException buildExceptionWithParamsAndCause() {
        NopException cause = new NopException(ERR_PLAN_261_CAUSE)
                .param("causeParam", "causeValue");
        return new NopException(ERR_PLAN_261_MAIN, cause)
                .param("mainParam", "mainValue")
                .param("mainCount", 42);
    }

    // ==================== step round-trip ====================

    /**
     * step 级 round-trip：含 params + cause chain 的 NopException 经 save→DB→load 后保留 params key/value + getCause() 非 null + cause errorCode 匹配。
     */
    @Test
    public void stepRoundTrip_preservesParamsAndCauseChain() {
        // 1. 构造含 params + cause 的 FAILED step
        ITaskStepRuntime stepRt = newStepRt("stepWithParamsAndCause");
        ITaskStepState state = stepRt.getState();
        NopException exp = buildExceptionWithParamsAndCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // 2. save（持久化 errorBeanData 到 DB entity）
        store.saveStepState(stepRt);

        // 3. entity 字段断言：errorBeanData 非空（含完整 ErrorBean JSON）
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity, "persisted step entity must exist");
        assertNotNull(entity.getErrorBeanData(), "errorBeanData column must be non-null after save (plan 261 core delta)");
        assertTrue(entity.getErrorBeanData().contains("mainParam"),
                "errorBeanData JSON must contain param keys");
        assertNotNull(entity.getErrCode(), "errCode still written for backward compat (设计裁定 5)");

        // 4. fresh load（从 DB entity 反序列化 ErrorBean）
        ITaskStepState loaded = store.loadStepState(null, "stepWithParamsAndCause", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state");
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "reconstructed exception must be non-null");
        assertTrue(loadedExp instanceof NopException, "reconstructed exception must be a NopException");

        NopException nopExp = (NopException) loadedExp;
        // params 保留（设计裁定 4：params 经 round-trip 可观测）
        assertEquals(ERR_PLAN_261_MAIN.getErrorCode(), nopExp.getErrorCode(), "errorCode must round-trip");
        assertEquals("mainValue", nopExp.getParam("mainParam"), "param mainParam must round-trip (plan 261 core delta)");
        assertEquals(42, nopExp.getParam("mainCount"), "param mainCount (Number) must round-trip");

        // cause chain 保留（设计裁定 3：cause 经 round-trip 可恢复）
        Throwable cause = nopExp.getCause();
        assertNotNull(cause, "getCause() must be non-null after round-trip when original had cause (plan 261 core delta)");
        assertTrue(cause instanceof NopException, "cause must be a NopException");
        assertEquals(ERR_PLAN_261_CAUSE.getErrorCode(), ((NopException) cause).getErrorCode(),
                "cause errorCode must round-trip");
        assertEquals("causeValue", ((NopException) cause).getParam("causeParam"),
                "cause param causeParam must round-trip");
    }

    // ==================== task round-trip ====================

    /**
     * task 级 round-trip：对称 step 级，task exception 经 saveTaskState→loadTaskState 保留 params + cause。
     */
    @Test
    public void taskRoundTrip_preservesParamsAndCauseChain() {
        // 1. 构造含 params + cause 的 FAILED task
        NopException exp = buildExceptionWithParamsAndCause();
        taskState.exception(exp);
        taskState.setTaskStatus(TASK_STATUS_FAILED);

        // 2. save（持久化 errorBeanData 到 DB entity）
        store.saveTaskState(taskRt);

        // 3. entity 字段断言：errorBeanData 非空
        NopTaskInstance entity = daoProvider.daoFor(NopTaskInstance.class)
                .getEntityById(taskState.getTaskInstanceId());
        assertNotNull(entity, "persisted task entity must exist");
        assertNotNull(entity.getErrorBeanData(), "task errorBeanData must be non-null after save");
        assertNotNull(entity.getErrCode(), "errCode still written for backward compat");

        // 4. fresh load
        ITaskState loaded = store.loadTaskState(taskState.getTaskInstanceId(), taskRt);
        assertNotNull(loaded, "loadTaskState must return persisted state");
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "reconstructed task exception must be non-null");

        NopException nopExp = (NopException) loadedExp;
        assertEquals(ERR_PLAN_261_MAIN.getErrorCode(), nopExp.getErrorCode());
        assertEquals("mainValue", nopExp.getParam("mainParam"), "task param must round-trip");
        assertEquals(42, nopExp.getParam("mainCount"), "task param (Number) must round-trip");

        Throwable cause = nopExp.getCause();
        assertNotNull(cause, "task getCause() must be non-null after round-trip");
        assertEquals(ERR_PLAN_261_CAUSE.getErrorCode(), ((NopException) cause).getErrorCode(),
                "task cause errorCode must round-trip");
        assertEquals("causeValue", ((NopException) cause).getParam("causeParam"),
                "task cause param must round-trip");
    }

    // ==================== backward compat ====================

    /**
     * 向后兼容：errorBeanData 为 null（历史行）→ load 回退 errCode + errMsg 重构（行为与 plan 258/259 一致）。
     */
    @Test
    public void stepLoad_fallsBackToErrCodeErrMsg_whenErrorBeanDataNull() {
        // 1. 构造 FAILED step 但模拟历史行：save 后手动清空 errorBeanData（模拟 plan 258/259 的历史数据）
        ITaskStepRuntime stepRt = newStepRt("legacyStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = new NopException(ERR_PLAN_261_COMPAT).description("legacy error message");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        // 模拟历史行：清空 errorBeanData，仅保留 errCode + errMsg（plan 258/259 写入格式）
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        entity.setErrorBeanData(null);
        daoProvider.daoFor(NopTaskStepInstance.class).updateEntityDirectly(entity);

        // 2. load：errorBeanData 为 null → 回退 errCode + errMsg 重构
        ITaskStepState loaded = store.loadStepState(null, "legacyStep", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state even for legacy row");
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "fallback path must still reconstruct exception from errCode+errMsg");
        assertTrue(loadedExp instanceof NopException);

        NopException nopExp = (NopException) loadedExp;
        assertEquals(ERR_PLAN_261_COMPAT.getErrorCode(), nopExp.getErrorCode(),
                "errCode must round-trip via fallback path");
        assertEquals("legacy error message", nopExp.getDescription(),
                "errMsg must round-trip via fallback path");
        // fallback 路径无 params（与 plan 258/259 行为一致）
        assertNull(nopExp.getParam("anyParam"), "fallback path has no params (legacy behavior)");
        assertNull(nopExp.getCause(), "fallback path has no cause (legacy behavior)");
    }

    // ==================== serialization failure tolerance ====================

    /**
     * 序列化失败容错：ErrorBean JSON 超 4000 字符 → save 非致命跳过 errorBeanData（errCode + errMsg 仍写入）→ load 回退 errCode + errMsg（非静默失败）。
     */
    @Test
    public void stepSave_skipsErrorBeanData_whenTooLong_butKeepsErrCodeErrMsg() {
        // 1. 构造超大 params 使 ErrorBean JSON 超过 4000 字符
        ITaskStepRuntime stepRt = newStepRt("oversizedErrorBeanStep");
        ITaskStepState state = stepRt.getState();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++)
            huge.append('x');
        NopException exp = new NopException(ERR_PLAN_261_MAIN)
                .param("hugeParam", huge.toString())
                .description("oversized error bean");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // 2. save
        store.saveStepState(stepRt);

        // 3. entity 断言：errorBeanData 被跳过（null），errCode + errMsg 仍写入
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity);
        assertNotNull(entity.getErrCode(), "errCode must still be written when errorBeanData skipped");
        assertEquals(ERR_PLAN_261_MAIN.getErrorCode(), entity.getErrCode());
        assertNull(entity.getErrorBeanData(), "errorBeanData must be skipped (null) when JSON exceeds 4000 chars");

        // 4. load：errorBeanData 为 null → 回退 errCode + errMsg（非静默失败，行为与向后兼容一致）
        ITaskStepState loaded = store.loadStepState(null, "oversizedErrorBeanStep", "xpl", taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "load must still reconstruct exception via fallback when errorBeanData skipped");
        assertEquals(ERR_PLAN_261_MAIN.getErrorCode(), ((NopException) loadedExp).getErrorCode());
        assertEquals("oversized error bean", ((NopException) loadedExp).getDescription());
    }

    // ==================== cross-restart E2E ====================

    /**
     * 端到端验证（Anti-Hollow #22）：fresh execute（step FAILED with params + cause → 终态 driver → saveStepState 持久化 errorBeanData）
     * → fresh DaoTaskStateStore.loadStepState（从 DB entity 反序列化 ErrorBean）→ resume 重抛 exception 含 params + cause chain。
     *
     * <p>此诊断增量在 plan 258/259 后不成立（load 只从 errCode + errMsg 重构泛型 NopException，params + cause 丢失）。
     * 使用独立的 fresh store 实例模拟 cross-restart（进程重启后全新的 DaoTaskStateStore 从 DB 读取历史持久化数据）。
     */
    @Test
    public void crossRestartResume_rethrowsExceptionWithParamsAndCause() {
        // === Phase A: fresh execute（模拟首次执行，抛含 params + cause 的 exception）===
        ITaskStepRuntime stepRt = newStepRt("crossRestartStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = buildExceptionWithParamsAndCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        // 终态 driver → 持久化 errorBeanData 到 DB entity
        store.saveStepState(stepRt);

        // === Phase B: cross-restart（fresh DaoTaskStateStore 从 DB 反序列化 ErrorBean）===
        DaoTaskStateStore restartedStore = new DaoTaskStateStore();
        restartedStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl restartedRuntime = new TaskRuntimeImpl(null, restartedStore, null, XLang.newEvalScope(), false);
        restartedRuntime.setTaskState(restartedStore.loadTaskState(taskState.getTaskInstanceId(), restartedRuntime));

        // resume：loadStepState 从 DB entity 的 errorBeanData 反序列化完整 ErrorBean
        ITaskStepState resumed = restartedStore.loadStepState(null, "crossRestartStep", "xpl", restartedRuntime);
        assertNotNull(resumed, "cross-restart loadStepState must return persisted FAILED state");
        assertTrue(resumed.isDone(), "resumed step must be terminal (FAILED)");

        // === Phase C: resume 重抛 exception（reader 消费 state.exception()）含 params + cause chain ===
        Throwable resumedExp = resumed.exception();
        assertNotNull(resumedExp, "resumed exception must be available for rethrow");
        NopException nopExp = (NopException) resumedExp;

        // plan 261 核心诊断增量：params 保留（plan 258/259 后此处为 null）
        assertEquals("mainValue", nopExp.getParam("mainParam"),
                "cross-restart resumed exception must preserve params (plan 261 core delta, was null pre-261)");
        // plan 261 核心诊断增量：cause chain 保留（plan 258/259 后此处为 null）
        assertNotNull(nopExp.getCause(),
                "cross-restart resumed exception must preserve cause chain (plan 261 core delta, was null pre-261)");
        assertEquals(ERR_PLAN_261_CAUSE.getErrorCode(), ((NopException) nopExp.getCause()).getErrorCode(),
                "cross-restart resumed cause errorCode must match");
    }
}
