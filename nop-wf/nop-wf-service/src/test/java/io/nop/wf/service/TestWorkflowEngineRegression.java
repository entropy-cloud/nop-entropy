/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.api.WfStepReference;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowCoordinatorImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.model.utils.WfModelHelper;
import io.nop.wf.service.mock.MockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_INVALID_STEP_STATUS_TRANSITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流引擎核心缺陷的回归测试：joinGroup持久化、source重复执行、signal提前激活、
 * 显式rejectSteps、模型分析器出边清空等
 */
public class TestWorkflowEngineRegression extends BaseTestCase {

    private static final AtomicInteger SOURCE_RUN_COUNT = new AtomicInteger();

    /** sourceCall()前N次抛异常，0表示恒成功，大数表示恒失败 */
    private static volatile int failFirstN = 0;

    public static void incSourceRunCount() {
        SOURCE_RUN_COUNT.incrementAndGet();
    }

    public static void resetSourceCalls(int failFirstN) {
        SOURCE_RUN_COUNT.set(0);
        TestWorkflowEngineRegression.failFirstN = failFirstN;
    }

    public static int getSourceRunCount() {
        return SOURCE_RUN_COUNT.get();
    }

    public static void sourceCall() {
        int n = SOURCE_RUN_COUNT.incrementAndGet();
        if (n <= failFirstN)
            throw new NopException("test-source-original-boom", null, false, false);
    }

    public static void throwSecondary() {
        throw new NopException("test-source-secondary-boom", null, false, false);
    }

    WorkflowManagerImpl workflowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManagerImpl();

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new MockWfActorResolver());
        workflowManager.setWorkflowEngine(engine);
        MockWorkflowStore store = new MockWorkflowStore();
        workflowManager.setWorkflowStore(store);

        workflowManager.init();
        ContextProvider.getOrCreateContext().setUserId("1");
    }

    /**
     * 配置了join-group-expr的join步骤：两条入边必须合并到同一个join步骤实例。
     * 此前joinGroup从未持久化到步骤记录，getNextJoinStepRecord按计算值与存储值(null)比较永远不匹配，
     * 每条入边都新建join实例，join后的步骤被执行N次
     */
    @Test
    public void testJoinGroupExprMergesToSingleInstance() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/joinGroupExpr", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getActivatedSteps();
        assertEquals(2, activeSteps.size());
        IWorkflowStep ysh1 = null;
        IWorkflowStep ysh2 = null;
        for (IWorkflowStep step : activeSteps) {
            if ("ysh1".equals(step.getStepName())) {
                ysh1 = step;
            } else if ("ysh2".equals(step.getStepName())) {
                ysh2 = step;
            }
        }
        assertNotNull(ysh1);
        assertNotNull(ysh2);

        ysh1.invokeAction("sp1", null, context);
        workflow.runAutoTransitions(context);

        // 第一条入边创建join实例，且joinGroup计算值必须持久化到记录上
        List<? extends IWorkflowStep> joinSteps = workflow.getStepsByName("join");
        assertEquals(1, joinSteps.size());
        assertEquals("g1", joinSteps.get(0).getRecord().getJoinGroup());

        ysh2.invokeAction("sp2", null, context);
        workflow.runAutoTransitions(context);

        // 第二条入边复用已存在的join实例而不是新建
        assertEquals(1, workflow.getStepsByName("join").size());
        assertTrue(workflow.isEnded());
    }

    /**
     * 迁移条件不满足而停留在EXECUTED状态的步骤，source不允许在后续自动迁移轮次中被重复执行。
     * 此前条件为 status <= EXECUTED，EXECUTED步骤每轮都重新执行source（重复副作用）
     */
    @Test
    public void testSourceNotReexecutedWhenTransitionBlocked() {
        SOURCE_RUN_COUNT.set(0);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/sourceBlocked", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        // 多轮自动迁移，onAppStates永不满足，source只能执行一次
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);

        assertEquals(1, SOURCE_RUN_COUNT.get());

        IWorkflowStep autoStep = workflow.getLatestStepByName("auto-step");
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_EXECUTED, autoStep.getRecord().getStatus());
        assertFalse(workflow.isEnded());
    }

    /**
     * SEQ_GROUP（串签）中处于WAITING的成员步骤不允许被无关的signal事件提前激活，
     * 顺序推进只由ExecGroupSupport.activateNextSeqStep驱动。
     * 此前任意signalWf调用会把所有WAITING步骤激活，串签退化为并行
     */
    @Test
    public void testSignalDoesNotActivateSeqGroupWaitingSteps() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/execGroupSeq", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        assertEquals(1, workflow.getActivatedSteps().size());
        assertEquals(2, workflow.getWaitingSteps().size());

        // 与串签步骤完全无关的信号
        workflow.turnSignalOn(Set.of("unrelated-signal"), context);

        assertEquals(1, workflow.getActivatedSteps().size(),
                "seq-group waiting steps should not be activated by unrelated signals");
        assertEquals(2, workflow.getWaitingSteps().size());
    }

    /**
     * 转办后进入WAITING的原步骤（exitCurrentStep=false）不允许被无关signal事件激活，
     * 否则会出现新旧办理人同时办理。此类步骤moveStepToWaiting时已记录finishTime
     */
    @Test
    public void testSignalDoesNotActivateTransferWaitingStep() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        context.getContext().setUserId("2");
        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        assertNotNull(ysh);

        WfActorAndOwner actorAndOwner = new WfActorAndOwner();
        actorAndOwner.setActorId("3");
        actorAndOwner.setActorType("user");
        IWorkflowStep nextStep = ysh.transferToActor(actorAndOwner, false, context);
        assertTrue(ysh.isWaiting());

        workflow.turnSignalOn(Set.of("unrelated-signal"), context);

        assertTrue(ysh.isWaiting(), "transferred-from step should stay waiting");
        List<? extends IWorkflowStep> activated = workflow.getActivatedSteps();
        assertEquals(1, activated.size());
        assertEquals(nextStep.getStepId(), activated.get(0).getStepId());
    }

    /**
     * 显式指定rejectSteps（按步骤名）的驳回：驳回目标按步骤名解析并重建该步骤。
     * 此前实现把同一个字符串既当【步骤实例ID】查找、又当【模型名】做祖先校验，
     * 两种口径不可能同时满足，任何输入下都抛ERR_WF_UNKNOWN_STEP
     */
    @Test
    public void testRejectToExplicitStepName() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        workflow.start(null, context);
        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("sh", null, context);

        context.getContext().setUserId("2");
        IWorkflowStep ysh = workflow.getLatestStepByName("ysh");
        ysh.invokeAction("sp", null, context);

        IWorkflowStep ysp = workflow.getLatestStepByName("ysp");
        assertNotNull(ysp);

        Map<String, Object> args = new HashMap<>();
        args.put(NopWfCoreConstants.VAR_REJECT_STEPS, "wf-start");
        ysp.invokeAction("_rejectAction", args, context);

        List<? extends IWorkflowStep> activeSteps = workflow.getSteps(false);
        assertEquals(1, activeSteps.size());
        assertEquals("wf-start", activeSteps.get(0).getStepName());
    }

    /**
     * 模型分析器不得清空起始步骤的出边信息。
     * 此前initTransitionFromSteps中对无入边步骤误写setTransitionToSteps(emptyList)，
     * 清掉了buildDag已经填好的transitionToSteps
     */
    @Test
    public void testStartStepTransitionToStepsNotCleared() {
        IWorkflow workflow = workflowManager.newWorkflow("test/reject", 1L);
        WfModel model = (WfModel) workflow.getModel();

        WfStepModel startModel = model.getStep("wf-start");
        assertNotNull(startModel.getTransitionToSteps());
        assertFalse(startModel.getTransitionToSteps().isEmpty(),
                "start step's outgoing transitions must not be cleared by analyzer");
        assertNotNull(startModel.getTransitionFromSteps());
        assertTrue(startModel.getTransitionFromSteps().isEmpty());
    }

    /**
     * 步骤source按<retry>配置重试：maxRetryCount=2时前2次瞬时失败后第3次成功，
     * 流程正常结束，execCount记录每次尝试（含重试）
     */
    @Test
    public void testSourceRetrySucceedsAfterTransientFailures() {
        resetSourceCalls(2);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/sourceRetry", 1L);
        workflow.start(null, context);
        workflow.getLatestStartStep().invokeAction("sh", null, context);

        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
        assertEquals(3, getSourceRunCount());
        IWorkflowStep autoStep = workflow.getLatestStepByName("auto-step");
        assertEquals(Integer.valueOf(3), autoStep.getRecord().getExecCount());
    }

    /**
     * exception-filter返回false表示异常不可恢复，立即终止重试：
     * 即使maxRetryCount=3也只执行1次
     */
    @Test
    public void testSourceRetryExceptionFilterStopsRetry() {
        resetSourceCalls(99);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/sourceRetryFilter", 1L);
        workflow.start(null, context);
        workflow.getLatestStartStep().invokeAction("sh", null, context);

        assertThrows(NopException.class, () -> workflow.runAutoTransitions(context));
        assertEquals(1, getSourceRunCount());
        assertEquals(Integer.valueOf(1),
                workflow.getLatestStepByName("auto-step").getRecord().getExecCount());
    }

    /**
     * 重试耗尽后抛出原始异常：maxRetryCount=1时共执行2次（首次+1次重试）
     */
    @Test
    public void testSourceRetryExhaustedThrows() {
        resetSourceCalls(99);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/sourceRetryExhausted", 1L);
        workflow.start(null, context);
        workflow.getLatestStartStep().invokeAction("sh", null, context);

        NopException e = assertThrows(NopException.class, () -> workflow.runAutoTransitions(context));
        assertEquals("test-source-original-boom", e.getErrorCode());
        assertEquals(2, getSourceRunCount());
        assertEquals(Integer.valueOf(2),
                workflow.getLatestStepByName("auto-step").getRecord().getExecCount());
    }

    /**
     * onError XPL自身抛异常不得掩盖原始异常：
     * 此前onError的异常会直接传播，导致source的真实失败原因丢失
     */
    @Test
    public void testOnErrorFailureDoesNotMaskOriginalException() {
        resetSourceCalls(99);

        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/onErrorThrows", 1L);
        workflow.start(null, context);
        workflow.getLatestStartStep().invokeAction("sh", null, context);

        NopException e = assertThrows(NopException.class, () -> workflow.runAutoTransitions(context));
        assertEquals("test-source-original-boom", e.getErrorCode());
    }

    /**
     * 路径无目录分隔符时guessWfNameFromFilePath显式报错而非substring越界
     */
    @Test
    public void testGuessWfNameFromFilePathNoSlash() {
        assertEquals("test/join", WfModelHelper.guessWfNameFromFilePath("/nop/wf/test/join/v1.xwf"));
        assertThrows(IllegalArgumentException.class,
                () -> WfModelHelper.guessWfNameFromFilePath("v1.xwf"));
    }

    /**
     * 标准启动参数做安全类型转换（非字符串值不抛ClassCastException），
     * 且参数缺省时不覆盖调用方在record上已设置的bizObjName
     */
    @Test
    public void testStartStdParamsTypeSafe() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);
        workflow.getRecord().setBizObjName("KEEP-ME");

        Map<String, Object> args = new HashMap<>();
        args.put("title", 123);
        workflow.start(args, context);

        assertEquals("123", workflow.getRecord().getTitle());
        assertEquals("KEEP-ME", workflow.getRecord().getBizObjName());
    }

    /**
     * 父流程已删除/父步骤实例不存在时endSubFlow只记警告并跳过，
     * 子流程的结束事务不再失败
     */
    @Test
    public void testEndSubFlowParentMissingSkips() {
        WorkflowCoordinatorImpl coordinator = new WorkflowCoordinatorImpl(workflowManager);
        IServiceContext context = new ServiceContextImpl();

        // 父流程不存在
        WfStepReference missingWfStep = new WfStepReference("test/testBasic", 1L, "missing-wf-id", "step-1");
        coordinator.endSubFlow(null, NopWfCoreConstants.WF_STATUS_COMPLETED, missingWfStep, null, context);

        // 父流程存在但父步骤实例不存在
        IWorkflow parent = workflowManager.newWorkflow("test/testBasic", 1L);
        parent.start(null, context);
        WfStepReference bogusStep = new WfStepReference("test/testBasic", 1L, parent.getWfId(), "bogus-step-id");
        coordinator.endSubFlow(parent.getWfReference(), NopWfCoreConstants.WF_STATUS_COMPLETED, bogusStep,
                null, context);
    }

    /**
     * 步骤history状态（>=COMPLETED）不允许回退到非history状态：
     * 此前transitToStatus为裸setStatus，终态可被任意回退
     */
    @Test
    public void testStepHistoryStatusCannotRevert() {
        IServiceContext context = new ServiceContextImpl();
        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);
        workflow.start(null, context);

        IWorkflowStep step = workflow.getActivatedSteps().get(0);
        step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_COMPLETED);

        NopException e = assertThrows(NopException.class,
                () -> step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
        assertEquals(ERR_WF_INVALID_STEP_STATUS_TRANSITION.getErrorCode(), e.getErrorCode());

        // history之间的前进方向仍然允许
        step.getRecord().transitToStatus(NopWfCoreConstants.WF_STEP_STATUS_KILLED);
    }
}
