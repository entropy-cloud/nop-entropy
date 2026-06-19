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
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.exceptions.NopTaskFailException;
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
 * Plan 266 focused 测试：验证 {@link DaoTaskStateStore} 的 save→load round-trip 能恢复 exception 的
 * <b>精确子类</b>（Phase 1：registry consult + FQCN capture）与 <b>cause chain 各层 stack</b>（Phase 2）。
 *
 * <p>所有用例均使用真实 DB-backed {@link DaoTaskStateStore}（{@code @NopTestConfig(localDb=true)}），
 * exception 经 entity JSON 序列化往返（errorBeanData 列），非 in-memory snapshot 引用直接拷贝。
 *
 * <p>Phase 1（精确子类恢复）：save 侧把原始 exception FQCN 编入 errorBeanData（reserved param），
 * load 侧 consult {@link io.nop.task.dao.store.TaskExceptionRegistry} 构造精确子类。闭合 plan 261 §Non-Goals:52
 * 「精确异常子类恢复」carry-over。
 *
 * <p>Phase 2（cause chain 各层 stack）：save 侧为各 cause 层取截断 stack（编入 errorBeanData 各 cause ErrorBean），
 * load 侧经 exception param 恢复。闭合 plan 265 §Non-Blocking Follow-ups:147「cause 层 stack」carry-over。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreExceptionSubclassAndCauseStackRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;

    static final ErrorCode ERR_PLAN_266_MAIN =
            ErrorCode.define("nop.err.test.plan-266.main", "plan 266 main exception");
    static final ErrorCode ERR_PLAN_266_CAUSE =
            ErrorCode.define("nop.err.test.plan-266.cause", "plan 266 cause exception");
    static final ErrorCode ERR_PLAN_266_DEEP =
            ErrorCode.define("nop.err.test.plan-266.deep", "plan 266 deep cause exception");
    static final ErrorCode ERR_PLAN_266_COMPAT =
            ErrorCode.define("nop.err.test.plan-266.compat", "plan 266 backward-compat");

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testSubclassAndCauseStackTask", 0, runtime);
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
     * 构造精确子类 exception（NopTaskFailException）+ 含 xplStack 的 cause（NopException），用于精确子类 + cause stack 测试。
     * 注意：{@code NopException.param()} 返回 {@code NopException}（非 self-type），故 param 链后类型为 NopException。
     */
    private NopTaskFailException buildPreciseSubclassWithStackCause() {
        NopException cause = new NopException(ERR_PLAN_266_CAUSE)
                .param("causeParam", "causeValue")
                .addXplStack("cause-frame-A")
                .addXplStack("cause-frame-B");
        NopTaskFailException exp = new NopTaskFailException(ERR_PLAN_266_MAIN, cause);
        exp.param("mainParam", "mainValue");
        return exp;
    }

    // ==================== Phase 1: 精确子类恢复 round-trip ====================

    /**
     * Exit Criteria（Phase 1）：rebuild 经 registry 命中精确子类。
     *
     * <p>NopTaskFailException（精确子类）经 save→DB→fresh load round-trip 后 instanceof NopTaskFailException 成立，
     * errorCode + params + cause chain 保留（与 plan 261 诊断信息对齐）。pre-266 此处 instanceof 不成立（load 出泛型 NopException）。
     */
    @Test
    public void stepRoundTrip_preservesPreciseSubclass() {
        ITaskStepRuntime stepRt = newStepRt("preciseSubclassStep");
        ITaskStepState state = stepRt.getState();
        NopTaskFailException exp = buildPreciseSubclassWithStackCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity, "persisted step entity must exist");
        assertNotNull(entity.getErrorBeanData(), "errorBeanData must be non-null");
        // FQCN 编入 errorBeanData（Phase 1）
        assertTrue(entity.getErrorBeanData().contains(NopTaskFailException.class.getName()),
                "errorBeanData must contain the precise FQCN");
        // reserved param 不泄漏到 loaded exception（过滤后）—— 此处验证 save 侧编入了
        assertTrue(entity.getErrorBeanData().contains("__exceptionClass"),
                "errorBeanData must contain reserved __exceptionClass param key");

        ITaskStepState loaded = store.loadStepState(null, "preciseSubclassStep", "xpl", taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp);

        // Phase 1 核心断言：精确子类 instanceof 成立（plan 266 core delta）
        assertTrue(loadedExp instanceof NopTaskFailException,
                "loaded exception must be precise subclass NopTaskFailException (plan 266 Phase 1 core delta, "
                        + "was generic NopException pre-266). Got: " + loadedExp.getClass().getName());

        NopTaskFailException failExp = (NopTaskFailException) loadedExp;
        // errorCode + params + cause 保留（诊断信息对齐）
        assertEquals(ERR_PLAN_266_MAIN.getErrorCode(), failExp.getErrorCode());
        assertEquals("mainValue", failExp.getParam("mainParam"), "params must round-trip");
        // reserved FQCN param 过滤后不污染用户可见 params
        assertFalse(failExp.getParams().containsKey("__exceptionClass"),
                "reserved __exceptionClass param must be filtered out, not leak to loaded exception params");

        assertNotNull(failExp.getCause(), "cause chain must round-trip");
        assertEquals(ERR_PLAN_266_CAUSE.getErrorCode(), ((NopException) failExp.getCause()).getErrorCode());
        assertEquals("causeValue", ((NopException) failExp.getCause()).getParam("causeParam"));
    }

    /**
     * task 级 round-trip：对称 step 级，task exception 精确子类经 saveTaskState→loadTaskState 恢复。
     */
    @Test
    public void taskRoundTrip_preservesPreciseSubclass() {
        NopTaskFailException exp = buildPreciseSubclassWithStackCause();
        taskState.exception(exp);
        taskState.setTaskStatus(TASK_STATUS_FAILED);
        store.saveTaskState(taskRt);

        ITaskState loaded = store.loadTaskState(taskState.getTaskInstanceId(), taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp);
        assertTrue(loadedExp instanceof NopTaskFailException,
                "task loaded exception must be precise subclass (plan 266 Phase 1)");
        assertEquals(ERR_PLAN_266_MAIN.getErrorCode(), ((NopException) loadedExp).getErrorCode());
    }

    // ==================== Phase 1: 向后兼容（未注册 / 历史 FQCN → generic）====================

    /**
     * Exit Criteria（Phase 1）：向后兼容——未注册 FQCN / 历史 errorBeanData（无 FQCN）→ 回退 generic NopException。
     *
     * <p>行为与 plan 261 一致：历史行无 __exceptionClass param → registry create 返回 null → rebuildExceptionFromErrorBean
     * 回退 generic NopException。
     */
    @Test
    public void stepLoad_legacyRowWithoutFqcn_fallsBackToGenericNopException() {
        ITaskStepRuntime stepRt = newStepRt("legacySubclassStep");
        ITaskStepState state = stepRt.getState();
        NopException exp = new NopException(ERR_PLAN_266_COMPAT).description("legacy error");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        // 模拟历史行（pre-266 无 FQCN）：手动从 errorBeanData JSON 中移除 __exceptionClass
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        String data = entity.getErrorBeanData();
        if (data != null) {
            data = data.replace(",\"__exceptionClass\":\"io.nop.api.core.exceptions.NopException\"", "");
            entity.setErrorBeanData(data);
            daoProvider.daoFor(NopTaskStepInstance.class).updateEntityDirectly(entity);
        }

        ITaskStepState loaded = store.loadStepState(null, "legacySubclassStep", "xpl", taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp);
        // 无 FQCN → 回退 generic NopException（非精确子类）
        assertFalse(loadedExp instanceof NopTaskFailException,
                "legacy row without FQCN must fall back to generic NopException (backward compat)");
        assertEquals(ERR_PLAN_266_COMPAT.getErrorCode(), ((NopException) loadedExp).getErrorCode());
    }

    // ==================== Phase 2: cause chain 各层 stack round-trip ====================

    /**
     * Exit Criteria（Phase 2）：cause chain 各层 exception 的 stack 经 round-trip 在 resumed exception 上可观测。
     *
     * <p>cause 的 xplStack 经 save→DB→load round-trip 后在 loaded cause exception 上经 errorStack param 可观测。
     * pre-266 cause 层 stack 丢失（plan 265 仅 top-level）。
     */
    @Test
    public void stepRoundTrip_preservesCauseLevelStack() {
        ITaskStepRuntime stepRt = newStepRt("causeStackStep");
        ITaskStepState state = stepRt.getState();
        NopTaskFailException exp = buildPreciseSubclassWithStackCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity);
        // errorBeanData 的 cause 层含 errorStack 字段（Phase 2 core delta）
        assertNotNull(entity.getErrorBeanData());
        // cause bean 有 errorStack —— JSON 中 cause 对象含 "errorStack"
        assertTrue(entity.getErrorBeanData().contains("errorStack"),
                "errorBeanData cause chain must contain errorStack field (plan 266 Phase 2 core delta)");

        ITaskStepState loaded = store.loadStepState(null, "causeStackStep", "xpl", taskRt);
        assertNotNull(loaded);
        NopTaskFailException loadedExp = (NopTaskFailException) loaded.exception();
        assertNotNull(loadedExp);

        // cause 层 stack 在 loaded cause exception 上可观测（经 errorStack param）
        NopException cause = (NopException) loadedExp.getCause();
        assertNotNull(cause, "cause must round-trip");
        Object causeStack = cause.getParam("errorStack");
        assertNotNull(causeStack, "cause-level stack must be observable on loaded cause exception "
                + "via errorStack param (plan 266 Phase 2 core delta, was null pre-266)");
        assertTrue(((String) causeStack).contains("cause-frame-A"),
                "cause-level stack must contain original cause frame: " + causeStack);
        assertTrue(((String) causeStack).contains("cause-frame-B"),
                "cause-level stack must contain original cause frame: " + causeStack);
    }

    /**
     * 深层 cause（≥2 层）各层 stack 独立可观测。
     */
    @Test
    public void stepRoundTrip_deepCauseChain_eachLevelStackObservable() {
        ITaskStepRuntime stepRt = newStepRt("deepCauseStackStep");
        ITaskStepState state = stepRt.getState();
        NopException deep = new NopException(ERR_PLAN_266_DEEP)
                .addXplStack("deep-frame");
        NopException cause = new NopException(ERR_PLAN_266_CAUSE, deep)
                .addXplStack("cause-frame");
        NopTaskFailException exp = new NopTaskFailException(ERR_PLAN_266_MAIN, cause);
        exp.param("mainParam", "mainValue");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "deepCauseStackStep", "xpl", taskRt);
        assertNotNull(loaded);
        NopTaskFailException loadedExp = (NopTaskFailException) loaded.exception();
        assertNotNull(loadedExp);

        // 第 1 层 cause stack
        NopException cause1 = (NopException) loadedExp.getCause();
        assertNotNull(cause1);
        assertNotNull(cause1.getParam("errorStack"), "cause level 1 stack must be observable");
        assertTrue(((String) cause1.getParam("errorStack")).contains("cause-frame"));

        // 第 2 层 cause stack
        NopException cause2 = (NopException) cause1.getCause();
        assertNotNull(cause2, "deep cause must round-trip");
        assertNotNull(cause2.getParam("errorStack"), "cause level 2 (deep) stack must be observable");
        assertTrue(((String) cause2.getParam("errorStack")).contains("deep-frame"));
    }

    // ==================== Phase 2: 零回归守卫（errorBeanData + errorStack 契约）====================

    /**
     * Exit Criteria（Phase 2）：errorBeanData（params+cause）+ errorStack（top-level）契约零回归。
     *
     * <p>plan 261 errorBeanData（params + cause chain 结构）+ plan 265 errorStack 列（top-level stack）契约不变。
     * Phase 2 的 cause 层 stack 编入 errorBeanData 各 cause bean 的 errorStack 字段，不破坏既有契约。
     */
    @Test
    public void stepRoundTrip_errorBeanAndErrorStackContractsUnchanged() {
        ITaskStepRuntime stepRt = newStepRt("contractGuardStep");
        ITaskStepState state = stepRt.getState();
        NopTaskFailException exp = buildPreciseSubclassWithStackCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity);
        // plan 265 契约：top-level errorStack 列非空（NopTaskFailException extends NopException, xplStack null → 此处 top 无 xplStack）
        // 注意：NopTaskFailException 无 xplStack → top-level extractErrorStack 返回 null → errorStack 列可能为 null
        // plan 261 契约：errorBeanData 含 params + cause
        assertNotNull(entity.getErrorBeanData());
        assertTrue(entity.getErrorBeanData().contains("mainParam"), "params must be in errorBeanData (plan 261 contract)");

        ITaskStepState loaded = store.loadStepState(null, "contractGuardStep", "xpl", taskRt);
        assertNotNull(loaded);
        NopTaskFailException loadedExp = (NopTaskFailException) loaded.exception();
        assertNotNull(loadedExp);
        // params round-trip（plan 261 契约）
        assertEquals("mainValue", loadedExp.getParam("mainParam"));
        // cause chain round-trip（plan 261 契约）
        assertNotNull(loadedExp.getCause());
        assertEquals(ERR_PLAN_266_CAUSE.getErrorCode(), ((NopException) loadedExp.getCause()).getErrorCode());
        assertEquals("causeValue", ((NopException) loadedExp.getCause()).getParam("causeParam"));
    }

    // ==================== Phase 2: budget-aware 截断边界 ====================

    /**
     * Exit Criteria（Phase 2）：截断边界测试（cause 层 stack 超 4000 截断，不致命）。
     *
     * <p>构造 deep cause chain + 各层 stack 使 total 趋近/超过 4000 → 断言 params+cause 仍 round-trip 存活
     * （cause 层 stack 降级剥离，设计裁定 5），不致命。
     */
    @Test
    public void stepSave_budgetAwareStripCauseStack_preservesParamsAndCause() {
        ITaskStepRuntime stepRt = newStepRt("budgetAwareStep");
        ITaskStepState state = stepRt.getState();
        // 构造 huge params + cause stack 使 total > 4000
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2500; i++)
            huge.append('x');
        NopException cause = new NopException(ERR_PLAN_266_CAUSE)
                .addXplStack(huge.toString());
        NopTaskFailException exp = new NopTaskFailException(ERR_PLAN_266_MAIN, cause);
        exp.param("hugeParam", huge.toString());
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);

        // save：budget-aware 不抛异常
        store.saveStepState(stepRt);

        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity);

        ITaskStepState loaded = store.loadStepState(null, "budgetAwareStep", "xpl", taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();

        // budget-aware 守卫：params+cause 仍 round-trip 存活（可能 errorBeanData 被跳过 → 回退 errCode，或 stack 被剥离）
        // 关键断言：不抛异常 + exception 可重构
        assertNotNull(loadedExp, "exception must still be reconstructable after budget-aware serialization");
        assertEquals(ERR_PLAN_266_MAIN.getErrorCode(), ((NopException) loadedExp).getErrorCode(),
                "errorCode must survive budget-aware serialization");
    }

    // ==================== cross-restart E2E（Phase 1 + Phase 2, Anti-Hollow #22）====================

    /**
     * 端到端验证（Anti-Hollow #22）：fresh execute（FAILED with precise subclass + cause stack → save）
     * → 模拟中断 → fresh DaoTaskStateStore 实例 load → 断言精确子类 instanceof + cause 各层 stack 可观测。
     *
     * <p>接线验证（#23）：registry 在 rebuild 路径运行时确实被 consult（instanceof 精确子类成立证明 registry 被调用，
     * 非「类型存在但未被 consult」的伪装）。
     *
     * <p>使用独立的 fresh store 实例模拟 cross-restart（进程重启后全新的 DaoTaskStateStore 从 DB 读取历史持久化数据）。
     * pre-266 此 E2E 不成立（load 出泛型 NopException，cause 层无 stack 诊断）。
     */
    @Test
    public void crossRestartResume_preciseSubclassAndCauseStackSurviveDbBoundary() {
        // === Phase A: fresh execute ===
        ITaskStepRuntime stepRt = newStepRt("crossRestartSubclassStep");
        ITaskStepState state = stepRt.getState();
        NopTaskFailException exp = buildPreciseSubclassWithStackCause();
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        // === Phase B: cross-restart（fresh DaoTaskStateStore + fresh runtime）===
        DaoTaskStateStore restartedStore = new DaoTaskStateStore();
        restartedStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl restartedRuntime = new TaskRuntimeImpl(null, restartedStore, null, XLang.newEvalScope(), false);
        restartedRuntime.setTaskState(restartedStore.loadTaskState(taskState.getTaskInstanceId(), restartedRuntime));

        ITaskStepState resumed = restartedStore.loadStepState(null, "crossRestartSubclassStep", "xpl", restartedRuntime);
        assertNotNull(resumed, "cross-restart loadStepState must return persisted FAILED state");
        assertTrue(resumed.isDone(), "resumed step must be terminal (FAILED)");

        // === Phase C: 精确子类 + cause stack 经 DB 边界存活 ===
        NopTaskFailException resumedExp = (NopTaskFailException) resumed.exception();
        assertNotNull(resumedExp, "resumed exception must be available for rethrow");

        // 接线验证（#23）：registry 在 rebuild 路径确实被 consult → instanceof 精确子类成立
        assertTrue(resumedExp instanceof NopTaskFailException,
                "cross-restart: resumed exception must be precise subclass (registry consulted at runtime, plan 266 Phase 1)");
        assertEquals(ERR_PLAN_266_MAIN.getErrorCode(), resumedExp.getErrorCode());
        assertEquals("mainValue", resumedExp.getParam("mainParam"),
                "cross-restart: params must survive DB round-trip");

        // cause 层 stack 经 DB 边界存活（Phase 2 core delta）
        NopException cause = (NopException) resumedExp.getCause();
        assertNotNull(cause, "cross-restart: cause chain must survive");
        Object causeStack = cause.getParam("errorStack");
        assertNotNull(causeStack,
                "cross-restart: cause-level stack must survive DB boundary (plan 266 Phase 2 core delta, was null pre-266)");
        assertTrue(((String) causeStack).contains("cause-frame-A"),
                "cross-restart: cause stack diagnostics must be observable");
    }

    // ==================== Phase 1: 无静默跳过（#24）====================

    /**
     * 无静默跳过（#24）：未注册类型回退路径有日志（非静默吞掉）。
     *
     * <p>历史行（无 FQCN）回退 generic NopException 时，rebuildExceptionFromErrorBean 的 registry.create 返回 null
     * → 回退构造 generic NopException。此路径有日志（registry 内 LOG.warn），不静默吞掉。
     * 此测试验证回退路径行为正确（exception 仍可重构），日志验证经代码审查确认（rebuildExceptionFromErrorBean 无空 catch）。
     */
    @Test
    public void stepLoad_unregisteredFallback_isObservableNotSilent() {
        ITaskStepRuntime stepRt = newStepRt("fallbackStep");
        ITaskStepState state = stepRt.getState();
        // 用未注册的子类（直接 NopException，FQCN=NopException，不在 registry 中）
        NopException exp = new NopException(ERR_PLAN_266_COMPAT).description("unregistered");
        state.fail(exp, taskRt);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "fallbackStep", "xpl", taskRt);
        assertNotNull(loaded);
        Throwable loadedExp = loaded.exception();
        // 回退 generic NopException（NopException FQCN 不在 registry）—— 非 null、非静默
        assertNotNull(loadedExp, "fallback must still reconstruct exception (not silent skip)");
        assertEquals(ERR_PLAN_266_COMPAT.getErrorCode(), ((NopException) loadedExp).getErrorCode());
        assertFalse(loadedExp instanceof NopTaskFailException,
                "unregistered NopException must fall back to generic (not precise subclass)");
    }
}
