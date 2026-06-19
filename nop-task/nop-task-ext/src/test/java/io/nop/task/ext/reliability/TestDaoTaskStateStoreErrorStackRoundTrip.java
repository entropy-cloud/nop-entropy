package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
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
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskConstants.TASK_STATUS_FAILED;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_taskInstanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 265 Phase 1 focused 测试：验证 {@link DaoTaskStateStore} 的 save→load round-trip 能持久化并取回
 * 终态 exception 的原始 stack 诊断（截断后的 {@code errorStack} 字符串），使 cross-restart resume 重抛的
 * exception 保留定位原始失败位置的 stack 信息。
 *
 * <p>所有用例均使用真实 DB-backed {@link DaoTaskStateStore}（{@code @NopTestConfig(localDb=true)}），
 * errorStack 经 entity 列 + DB 序列化往返，非 in-memory 引用直接拷贝。
 *
 * <p>闭合 plan 265 的核心诊断增量：plan 261 前 load 路径丢弃原始 stack（{@code buildErrorMessage(includeStack=false)}
 * 使序列化的 errorBeanData 不含 {@code errorStack}）；本计划新增正交 {@code errorStack} 列持久化截断后的原始 stack，
 * load 侧在 rebuild 的 exception 上恢复该诊断。
 *
 * <p>设计裁定 1 回归守卫：{@code errorBeanData}（params + cause chain）持久化行为与 plan 261 一致，
 * stack 持久化路径正交、不触及 {@code serializeErrorBeanData}/{@code loadException} 的 errorBeanData 处理——
 * 故写入 {@code errorStack} 后 errorBeanData JSON 不含 {@code errorStack} 字段。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreErrorStackRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;

    static final ErrorCode ERR_PLAN_265_MAIN =
            ErrorCode.define("nop.err.test.plan-265.main", "plan 265 main exception with stack");
    static final ErrorCode ERR_PLAN_265_CAUSE =
            ErrorCode.define("nop.err.test.plan-265.cause", "plan 265 cause exception");
    static final ErrorCode ERR_PLAN_265_TRUNC =
            ErrorCode.define("nop.err.test.plan-265.trunc", "plan 265 truncation exception");
    static final ErrorCode ERR_PLAN_265_COMPAT =
            ErrorCode.define("nop.err.test.plan-265.compat", "plan 265 backward-compat fallback");

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testErrorStackTask", 0, runtime);
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

    /**
     * 构造含 stack（xplStack 帧）+ params + cause chain 的 NopException。
     * 顶层 exception 的 xplStack 经 {@code ErrorMessageManager.getStacktrace} 截前 5 帧生成 errorStack。
     */
    private NopException buildExceptionWithStackParamsAndCause() {
        NopException cause = new NopException(ERR_PLAN_265_CAUSE)
                .param("causeParam", "causeValue");
        return new NopException(ERR_PLAN_265_MAIN, cause)
                .param("mainParam", "mainValue")
                .param("mainCount", 42)
                .addXplStack("frame-A")
                .addXplStack("frame-B");
    }

    // ==================== step errorStack round-trip + regression guard ====================

    /**
     * step 级 round-trip（#23 接线验证）：含 stack + params + cause 的 FAILED step → save（写 errorStack + DB）
     * → fresh load → 断言 errorStack 经 entity↔DB 边界 round-trip 等值 + loaded exception 暴露原始 stack 诊断。
     *
     * <p>回归守卫（设计裁定 1）：写入 errorStack 后 errorBeanData JSON 不含 "errorStack" 字段，且 params + cause 行为不变。
     */
    @Test
    public void stepRoundTrip_preservesErrorStackAndErrorBeanDataContract() {
        // 1. 构造含 stack + params + cause 的 FAILED step
        ITaskStepRuntime stepRt = newStepRt("stepWithErrorStack");
        ITaskStepState state = stepRt.getState();
        NopException exp = buildExceptionWithStackParamsAndCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // 2. save（持久化 errorStack + errorBeanData 到 DB entity）
        store.saveStepState(stepRt);

        // 3. entity 字段断言：errorStack 列非空（plan 265 core delta），值 == 截断后的原始 stack
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity, "persisted step entity must exist");
        assertNotNull(entity.getErrorStack(), "errorStack column must be non-null after save (plan 265 core delta)");
        assertEquals("frame-A\nframe-B", entity.getErrorStack(),
                "errorStack must equal the truncated original stack (first 5 xpl frames joined)");

        // 回归守卫（设计裁定 1）：errorBeanData 不含 errorStack 字段——stack 路径正交，不流入 errorBeanData
        assertNotNull(entity.getErrorBeanData(), "errorBeanData must still be written (plan 261 contract unchanged)");
        assertFalse(entity.getErrorBeanData().contains("errorStack"),
                "errorBeanData JSON must NOT contain errorStack field (设计裁定 1: orthogonal column, stack never flows into errorBeanData)");
        assertTrue(entity.getErrorBeanData().contains("mainParam"),
                "errorBeanData must still contain params (plan 261 contract unchanged)");

        // 4. fresh load：loaded exception 暴露原始 stack 诊断（设计裁定 4，经 exception param 可观测）
        ITaskStepState loaded = store.loadStepState(null, "stepWithErrorStack", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state");
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "reconstructed exception must be non-null");
        assertTrue(loadedExp instanceof NopException);

        NopException nopExp = (NopException) loadedExp;
        assertEquals("frame-A\nframe-B", nopExp.getParam("errorStack"),
                "loaded exception must expose original stack diagnostics via errorStack param (plan 265 core delta, was null pre-265)");

        // 回归守卫：params + cause 行为不变（plan 261 契约零回归）
        assertEquals(ERR_PLAN_265_MAIN.getErrorCode(), nopExp.getErrorCode(), "errorCode must round-trip (plan 261 unchanged)");
        assertEquals("mainValue", nopExp.getParam("mainParam"), "params must round-trip (plan 261 contract unchanged)");
        assertNotNull(nopExp.getCause(), "cause chain must round-trip (plan 261 contract unchanged)");
        assertEquals(ERR_PLAN_265_CAUSE.getErrorCode(), ((NopException) nopExp.getCause()).getErrorCode());
    }

    // ==================== task errorStack round-trip (symmetric) ====================

    /**
     * task 级 round-trip：对称 step 级，task exception 经 saveTaskState→loadTaskState 保留 errorStack 诊断。
     */
    @Test
    public void taskRoundTrip_preservesErrorStack() {
        NopException exp = buildExceptionWithStackParamsAndCause();
        taskState.exception(exp);
        taskState.setTaskStatus(TASK_STATUS_FAILED);

        store.saveTaskState(taskRt);

        NopTaskInstance entity = daoProvider.daoFor(NopTaskInstance.class)
                .getEntityById(taskState.getTaskInstanceId());
        assertNotNull(entity, "persisted task entity must exist");
        assertNotNull(entity.getErrorStack(), "task errorStack must be non-null after save (plan 265 core delta)");
        assertEquals("frame-A\nframe-B", entity.getErrorStack(),
                "task errorStack must equal the truncated original stack");
        // 回归守卫
        assertNotNull(entity.getErrorBeanData(), "task errorBeanData must still be written");
        assertFalse(entity.getErrorBeanData().contains("errorStack"),
                "task errorBeanData JSON must NOT contain errorStack field (设计裁定 1)");

        ITaskState loaded = store.loadTaskState(taskState.getTaskInstanceId(), taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp);
        assertEquals("frame-A\nframe-B", ((NopException) loadedExp).getParam("errorStack"),
                "loaded task exception must expose original stack diagnostics (plan 265 core delta)");
    }

    // ==================== truncation boundary ====================

    /**
     * 截断边界（#24 非静默跳过）：构造超长 stack（单帧 > 列 precision 4000）→ 断言 errorStack 被截断到列 precision
     * 且不抛异常（非致命），errorBeanData 不受影响。
     */
    @Test
    public void stepSave_truncatesOverlongErrorStack_noException_errorBeanDataUnaffected() {
        ITaskStepRuntime stepRt = newStepRt("oversizedStackStep");
        ITaskStepState state = stepRt.getState();
        // 单个超长 xpl 帧（5000 字符）—— getStacktrace 取 xplStack join（不截单帧长度，仅截帧数≤5），故 stack 字符串 = 5000
        NopException exp = new NopException(ERR_PLAN_265_TRUNC)
                .addXplStack(new String(new char[5000]).replace('\0', 'X'));
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // save：不抛异常（非致命截断，对称 stateBeanData/errorBeanData 非致命跳过模式）
        store.saveStepState(stepRt);

        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity);
        assertNotNull(entity.getErrorStack(), "errorStack must still be persisted (truncated, not skipped) for overlong stack");
        assertTrue(entity.getErrorStack().length() <= 4000,
                "errorStack must be truncated to column precision (<= 4000), actual=" + entity.getErrorStack().length());
        assertEquals(4000, entity.getErrorStack().length(),
                "errorStack must be truncated to exactly column precision 4000");

        // errorBeanData 不受截断影响（正交路径，设计裁定 1）
        assertNotNull(entity.getErrCode(), "errCode still written");
        assertFalse(entity.getErrorBeanData() != null && entity.getErrorBeanData().contains("errorStack"),
                "errorBeanData must not contain errorStack even when stack is overlong");

        // loaded exception 的 errorStack param 反映截断后的值
        ITaskStepState loaded = store.loadStepState(null, "oversizedStackStep", "xpl", taskRt);
        assertNotNull(loaded);
        Object loadedStack = ((NopException) loaded.exception()).getParam("errorStack");
        assertNotNull(loadedStack, "loaded exception must expose truncated stack diagnostics");
        assertEquals(4000, ((String) loadedStack).length(),
                "loaded errorStack param must reflect truncated value");
    }

    // ==================== cross-restart E2E (Phase 2, Anti-Hollow #22) ====================

    /**
     * 端到端验证（Anti-Hollow #22）：fresh execute（含非空 stack 的 FAILED step → 终态 driver → saveStepState 写 errorStack + DB）
     * → 模拟中断 → fresh {@link DaoTaskStateStore} 实例（模拟进程重启后全新 store 从 DB 读取历史持久化数据）
     * loadTaskState + loadStepState → 断言 errorStack 经 DB 序列化边界存活（save 写入值 == fresh load 读回值，截断后）
     * + loaded exception 原始 stack 诊断可观测 + errorBeanData（params + cause）契约不变。
     *
     * <p>使用独立的 fresh store 实例（非 in-memory 引用），证明 errorStack 经真实 DB 序列化边界 round-trip，
     * 非「写而丢失 / 写而不读」的伪装。本计划前此 round-trip 不成立（errorStack 列不存在，load 重构的 exception
     * 不暴露原始 stack 诊断——pre-265 resume 重抛的 exception 仅保留 errCode + errMsg + errorBeanData params/cause）。
     */
    @Test
    public void crossRestartResume_errorStackSurvivesDbBoundary_diagnosticsObservable() {
        // === Phase A: fresh execute（首次执行：FAILED with stack + params + cause，终态 driver，持久化到 DB）===
        ITaskStepRuntime stepRt = newStepRt("crossRestartErrorStackStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = buildExceptionWithStackParamsAndCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        // 终态 driver → 持久化 errorStack + errorBeanData 到 DB entity
        store.saveStepState(stepRt);

        // 捕获 save 写入的 errorStack 值（用于 cross-restart 后 round-trip 等值断言）
        NopTaskStepInstance savedEntity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(savedEntity.getErrorStack(), "save must persist non-null errorStack");
        String savedStack = savedEntity.getErrorStack();
        assertFalse(savedEntity.getErrorBeanData().contains("errorStack"),
                "save-side errorBeanData must not contain errorStack (设计裁定 1, pre/post cross-restart invariant)");

        // === Phase B: cross-restart（fresh DaoTaskStateStore + fresh runtime 从 DB 反序列化）===
        DaoTaskStateStore restartedStore = new DaoTaskStateStore();
        restartedStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl restartedRuntime = new TaskRuntimeImpl(null, restartedStore, null, XLang.newEvalScope(), false);
        restartedRuntime.setTaskState(restartedStore.loadTaskState(taskState.getTaskInstanceId(), restartedRuntime));

        ITaskStepState resumed = restartedStore.loadStepState(null, "crossRestartErrorStackStep", "xpl", restartedRuntime);
        assertNotNull(resumed, "cross-restart loadStepState must return persisted FAILED state");
        assertTrue(resumed.isDone(), "resumed step must be terminal (FAILED)");

        // === Phase C: Anti-Hollow 断言——errorStack 经 DB 边界存活（非空壳、非占位）===
        NopException resumedExp = (NopException) resumed.exception();
        assertNotNull(resumedExp, "resumed exception must be available for rethrow");

        // errorStack 经 DB 序列化边界 round-trip 等值（plan 265 core delta，pre-265 此处为 null）
        assertEquals(savedStack, resumedExp.getParam("errorStack"),
                "cross-restart: errorStack must survive DB round-trip (save value == fresh load value, plan 265 core delta, was null pre-265)");
        assertEquals("frame-A\nframe-B", resumedExp.getParam("errorStack"),
                "cross-restart: loaded errorStack must be the original stack diagnostics");

        // errorBeanData（params + cause）契约不变（设计裁定 1 回归守卫——cross-restart 后 params/cause 仍 round-trip）
        assertEquals(ERR_PLAN_265_MAIN.getErrorCode(), resumedExp.getErrorCode(),
                "cross-restart: errorCode round-trip unchanged (plan 261 contract)");
        assertEquals("mainValue", resumedExp.getParam("mainParam"),
                "cross-restart: params round-trip unchanged (plan 261 contract, errorStack orthogonal)");
        assertNotNull(resumedExp.getCause(),
                "cross-restart: cause chain round-trip unchanged (plan 261 contract)");
        assertEquals(ERR_PLAN_265_CAUSE.getErrorCode(), ((NopException) resumedExp.getCause()).getErrorCode(),
                "cross-restart: cause errorCode round-trip unchanged");
    }

    // ==================== backward compat: legacy row without errorStack ====================

    /**
     * 向后兼容：errorStack 列为 null（历史行 / pre-265 数据）→ load 重构 exception 但不附加 errorStack 诊断
     * （getParam("errorStack") == null），行为与 plan 261 一致，零回归。
     */
    @Test
    public void stepLoad_legacyRowWithoutErrorStack_loadsCleanlyWithoutStackDiagnostics() {
        ITaskStepRuntime stepRt = newStepRt("legacyStackStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = new NopException(ERR_PLAN_265_COMPAT).description("legacy error");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        // 模拟 pre-265 历史行：清空 errorStack 列
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        entity.setErrorStack(null);
        daoProvider.daoFor(NopTaskStepInstance.class).updateEntityDirectly(entity);

        ITaskStepState loaded = store.loadStepState(null, "legacyStackStep", "xpl", taskRt);
        assertNotNull(loaded);
        NopException loadedExp = (NopException) loaded.exception();
        assertNotNull(loadedExp, "exception still reconstructed from errorBeanData/errCode");
        assertEquals(ERR_PLAN_265_COMPAT.getErrorCode(), loadedExp.getErrorCode());
        assertNull(loadedExp.getParam("errorStack"),
                "legacy row without errorStack: loaded exception carries no stack diagnostics (backward compat, zero-regression)");
    }
}
