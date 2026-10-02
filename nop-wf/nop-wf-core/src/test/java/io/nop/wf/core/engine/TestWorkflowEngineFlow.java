package io.nop.wf.core.engine;

import io.nop.api.core.auth.IUserDelegateService;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.mock.InMemoryWfStore;
import io.nop.wf.core.mock.SimpleWfActorResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI5: 引擎流转/回退语义测试。使用手写 InMemoryWfStore + SimpleWfActorResolver
 * 隔离 WorkflowManagerImpl/WorkflowEngineImpl，断言状态机语义：
 * 启动激活、动作迁移、流程终止、驳回回退、撤回、非法操作拒绝。
 * 模型定义见 src/test/resources/_vfs/nop/wf/test/engineFlow/v1.xwf。
 */
public class TestWorkflowEngineFlow extends BaseTestCase {
    private WorkflowManagerImpl workflowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        workflowManager = new WorkflowManagerImpl();

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new SimpleWfActorResolver());
        engine.setUserDelegateService(new IUserDelegateService() {
            @Override
            public boolean canDelegate(String userId, String ownerId, String scope) {
                return false;
            }

            @Override
            public java.util.Set<String> getDelegateOwnerIds(String userId, String scope) {
                return java.util.Collections.emptySet();
            }
        });
        workflowManager.setWorkflowEngine(engine);
        workflowManager.setWorkflowStore(new InMemoryWfStore());
        workflowManager.init();
    }

    private IServiceContext ctx(String userId) {
        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId(userId);
        return context;
    }

    private IWorkflow newFlow() {
        return workflowManager.newWorkflow("test/engineFlow", 1L);
    }

    private IWorkflowStep activeStep(IWorkflow wf, String stepName) {
        for (IWorkflowStep step : wf.getActivatedSteps()) {
            if (step.getStepName().equals(stepName))
                return step;
        }
        return null;
    }

    @Test
    public void testStartActivatesFirstStepWithAssignedActor() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));

        // 启动后首个步骤激活，执行者为 assignment 中配置的 user:1
        assertEquals(1, wf.getActivatedSteps().size());
        IWorkflowStep start = activeStep(wf, "wf-start");
        assertNotNull(start);
        assertEquals("1", start.getActor().getActorId());
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED, start.getRecord().getStatus());
        assertEquals(NopWfCoreConstants.WF_STATUS_ACTIVATED, wf.getRecord().getStatus());
        assertFalse(wf.isEnded());
    }

    @Test
    public void testActionTransitionsToNextStep() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));

        IWorkflowStep start = activeStep(wf, "wf-start");
        start.invokeAction("sh", null, ctx("1"));

        // sh 动作迁移到 ysh，原步骤进入历史，新步骤执行者为 user:2
        IWorkflowStep ysh = activeStep(wf, "ysh");
        assertNotNull(ysh);
        assertEquals("2", ysh.getActor().getActorId());
        assertTrue(start.isHistory());
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_COMPLETED, start.getRecord().getStatus());
        assertFalse(wf.isEnded());
    }

    @Test
    public void testFinalActionEndsFlow() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));
        activeStep(wf, "wf-start").invokeAction("sh", null, ctx("1"));
        activeStep(wf, "ysh").invokeAction("sp", null, ctx("2"));

        // 最后一步 to-end：流程整体完成，全部步骤进入历史
        assertTrue(wf.isEnded());
        assertEquals(NopWfCoreConstants.WF_STATUS_COMPLETED, wf.getRecord().getStatus());
        assertEquals(0, wf.getActivatedSteps().size());
        assertTrue(wf.getSteps(false).isEmpty());
    }

    @Test
    public void testRejectReturnsToPreviousStep() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));
        activeStep(wf, "wf-start").invokeAction("sh", null, ctx("1"));

        IWorkflowStep ysh = activeStep(wf, "ysh");
        assertNotNull(ysh);
        ysh.invokeAction("_rejectAction", null, ctx("2"));

        // 驳回后回到上一步骤 wf-start，被驳回步骤进入 REJECTED 历史
        assertEquals(1, wf.getActivatedSteps().size());
        assertEquals("wf-start", wf.getActivatedSteps().get(0).getStepName());
        assertEquals("1", wf.getActivatedSteps().get(0).getActor().getActorId());
        assertTrue(ysh.isHistory());
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_REJECTED, ysh.getRecord().getStatus());
        assertFalse(wf.isEnded());

        // 回退后流程可继续推进到结束
        activeStep(wf, "wf-start").invokeAction("sh", null, ctx("1"));
        activeStep(wf, "ysh").invokeAction("sp", null, ctx("2"));
        assertTrue(wf.isEnded());
    }

    @Test
    public void testWithdrawReturnsFlowToStartStep() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));
        activeStep(wf, "wf-start").invokeAction("sh", null, ctx("1"));

        // 后续步骤尚未完成时，发起人可从历史起始步骤撤回
        IWorkflowStep ysh = activeStep(wf, "ysh");
        assertNotNull(ysh);
        IWorkflowStep prevStart = ysh.getPrevSteps().get(0);
        assertEquals("wf-start", prevStart.getStepName());
        assertTrue(prevStart.getModel().isAllowWithdraw());

        // 撤回动作 _withdrawAction 必须在该历史起始步骤上可用
        String withdrawAction = null;
        for (io.nop.wf.core.model.IWorkflowActionModel action : prevStart.getAllowedActions(ctx("1"))) {
            if ("_withdrawAction".equals(action.getName()))
                withdrawAction = action.getName();
        }
        assertEquals("_withdrawAction", withdrawAction);
        prevStart.invokeAction(withdrawAction, null, ctx("1"));

        assertEquals(1, wf.getActivatedSteps().size());
        IWorkflowStep restart = activeStep(wf, "wf-start");
        assertNotNull(restart);
        assertNotNull(restart.getActor().getActorId());
        assertFalse(wf.isEnded());
    }

    @Test
    public void testUnknownActionRejected() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));

        IWorkflowStep start = activeStep(wf, "wf-start");
        try {
            start.invokeAction("notExistAction", null, ctx("1"));
            fail("expect ERR_WF_UNKNOWN_ACTION");
        } catch (NopException e) {
            assertEquals(ERR_WF_UNKNOWN_ACTION.getErrorCode(), e.getErrorCode());
        }
        // 非法操作不改变流程状态
        assertTrue(start.isActivated());
    }

    @Test
    public void testActionNotAssignedToCurrentStepRejected() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));
        activeStep(wf, "wf-start").invokeAction("sh", null, ctx("1"));

        // sh 动作只被 wf-start 引用，在 ysh 步骤不可执行
        IWorkflowStep ysh = activeStep(wf, "ysh");
        try {
            ysh.invokeAction("sh", null, ctx("2"));
            fail("expect ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP");
        } catch (NopException e) {
            assertEquals(ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP.getErrorCode(), e.getErrorCode());
        }
        assertTrue(ysh.isActivated());
    }

    @Test
    public void testSuspendBlocksAndResumeRestores() {
        IWorkflow wf = newFlow();
        wf.start(null, ctx("1"));
        wf.suspend(null, ctx("1"));
        assertEquals(NopWfCoreConstants.WF_STATUS_SUSPENDED, wf.getRecord().getStatus());

        wf.resume(null, ctx("1"));
        assertEquals(NopWfCoreConstants.WF_STATUS_ACTIVATED, wf.getRecord().getStatus());
        assertFalse(wf.isEnded());
    }
}
