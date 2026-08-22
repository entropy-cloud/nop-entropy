package io.nop.wf.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.WfUserActorBean;
import io.nop.wf.api.beans.WfSignalRequestBean;
import io.nop.wf.api.beans.WfTransferActorsRequestBean;
import io.nop.wf.api.beans.WfTransferFailedItemBean;
import io.nop.wf.api.beans.WfTransferResultBean;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.engine.DefaultWorkflowExecutor;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.service.impl.WorkflowServiceImpl;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import io.nop.wf.service.mock.EnhancedMockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWorkflowServiceImpl extends BaseTestCase {
    static final String MISSING_USER_ID = "missing-user";

    WorkflowManagerImpl workflowManager;
    WorkflowServiceImpl workflowService;
    MockWorkflowStore workflowStore;

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

        EnhancedMockWfActorResolver resolver = new EnhancedMockWfActorResolver() {
            @Override
            public IWfActor resolveUser(String userId) {
                if (MISSING_USER_ID.equals(userId))
                    return null;
                return super.resolveUser(userId);
            }
        };

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(resolver);
        engine.setUserDelegateService(new io.nop.api.core.auth.IUserDelegateService() {
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

        workflowStore = new MockWorkflowStore();
        workflowManager.setWorkflowStore(workflowStore);
        workflowManager.init();

        DefaultWorkflowExecutor executor = new DefaultWorkflowExecutor();
        executor.setWorkflowManager(workflowManager);

        workflowService = new WorkflowServiceImpl();
        workflowService.setWorkflowExecutor(executor);
        workflowService.setWorkflowStore(workflowStore);
        workflowService.setWfActorResolver(resolver);

        ContextProvider.getOrCreateContext().setUserId("1");
    }

    private IServiceContext newContext(String userId) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.getContext().setUserId(userId);
        return context;
    }

    private IWorkflow startWorkflow(String wfName, String userId) {
        IServiceContext context = newContext(userId);
        IWorkflow workflow = workflowManager.newWorkflow(wfName, 1L);
        workflow.start(new HashMap<>(), context);
        return workflow;
    }

    private void invokeAction(IWorkflow workflow, String stepName, String userId, String actionName) {
        IServiceContext context = newContext(userId);
        IWorkflowStep step = workflow.getActivatedSteps().stream()
                .filter(it -> stepName.equals(it.getStepName()))
                .findFirst().orElse(null);
        assertNotNull(step);
        step.invokeAction(actionName, null, context);
        workflow.runAutoTransitions(context);
    }

    @Test
    public void testSignalWfActivatesWaitingStep() {
        IWorkflow workflow = startWorkflow("test/waitSignal", "1");

        invokeAction(workflow, "wf-start", "1", "submit");
        assertTrue(workflow.getWaitingSteps().stream().anyMatch(step -> "wait-review".equals(step.getStepName())));
        assertFalse(workflow.getActivatedSteps().stream().anyMatch(step -> "wait-review".equals(step.getStepName())));

        WfSignalRequestBean request = new WfSignalRequestBean();
        request.setWfName("test/waitSignal");
        request.setWfVersion(1L);
        request.setWfId(workflow.getWfId());
        request.setSignals(Set.of("ai-done"));
        request.setOn(true);

        FutureHelper.syncGet(workflowService.signalWfAsync(request, null, newContext("system")));

        assertTrue(workflow.isEnded());
        assertFalse(workflow.getWaitingSteps().stream().anyMatch(step -> "wait-review".equals(step.getStepName())));
    }

    @Test
    public void testSignalWfRejectsEndedWorkflow() {
        IWorkflow workflow = startWorkflow("test/testBasic", "1");
        invokeAction(workflow, "wf-start", "1", "action0");
        assertTrue(workflow.isEnded());

        WfSignalRequestBean request = new WfSignalRequestBean();
        request.setWfName("test/testBasic");
        request.setWfVersion(1L);
        request.setWfId(workflow.getWfId());
        request.setSignals(Set.of("done"));
        request.setOn(true);

        assertThrows(NopException.class,
                () -> FutureHelper.syncGet(workflowService.signalWfAsync(request, null, newContext("system"))));
    }

    @Test
    public void testTransferActorsChangesOwnerAndRecordsFailure() {
        IWorkflow workflow1 = startWorkflow("test/execGroupVote", "1");
        invokeAction(workflow1, "wf-start", "1", "sh");

        IWorkflow workflow2 = startWorkflow("test/execGroupVote", "1");
        invokeAction(workflow2, "wf-start", "1", "sh");

        WorkflowRecordBean wfRecord2 = (WorkflowRecordBean) workflowStore.getWfRecord("test/execGroupVote", 1L, workflow2.getWfId());
        WorkflowStepRecordBean removedStep = wfRecord2.getSteps().stream()
                .filter(step -> "2".equals(step.getOwnerId()))
                .findFirst().orElse(null);
        assertNotNull(removedStep);
        removedStep.setWfId("missing-wf");

        WfTransferActorsRequestBean request = new WfTransferActorsRequestBean();
        request.setFromUserId("2");
        request.setToUserId("9");

        WfTransferResultBean result = FutureHelper.syncGet(workflowService.transferActorsAsync(request, null, newContext("2")));

        assertTrue(result.getSuccessCount() > 0);
        assertEquals(1, result.getFailedItems().size());
        WfTransferFailedItemBean failedItem = (WfTransferFailedItemBean) result.getFailedItems().get(0);
        assertEquals("missing-wf", failedItem.getWfId());

        IWorkflowStep transferredStep = workflow1.getActivatedSteps().stream()
                .filter(step -> "9".equals(step.getRecord().getOwnerId()))
                .findFirst().orElse(null);
        assertNotNull(transferredStep);

        WorkflowStepRecordBean transferredRecord = (WorkflowStepRecordBean) transferredStep.getRecord();
        assertTrue(transferredRecord.getActions().stream().anyMatch(action -> "transfer".equals(action.getActionName())));
    }

    @Test
    public void testTransferActorsRejectsCallerOtherThanOwnerOrManager() {
        IWorkflow workflow = startWorkflow("test/execGroupVote", "1");
        invokeAction(workflow, "wf-start", "1", "sh");
        assertNotNull(findActivatedStepByOwner(workflow, "2"));

        WfTransferActorsRequestBean request = new WfTransferActorsRequestBean();
        request.setFromUserId("2");
        request.setToUserId("9");

        NopException e = assertThrows(NopException.class,
                () -> FutureHelper.syncGet(workflowService.transferActorsAsync(request, null, newContext("3"))));
        assertEquals(NopWfCoreErrors.ERR_WF_NOT_ALLOW_TRANSFER_ACTORS_BY_USER.getErrorCode(), e.getErrorCode());

        // 非本人、非管理员调用时 owner 不被改派
        assertNotNull(findActivatedStepByOwner(workflow, "2"));
    }

    @Test
    public void testTransferActorsAllowsManager() {
        IWorkflow workflow = startWorkflow("test/execGroupVote", "1");
        invokeAction(workflow, "wf-start", "1", "sh");

        WorkflowRecordBean wfRecord = (WorkflowRecordBean) workflowStore.getWfRecord(
                "test/execGroupVote", 1L, workflow.getWfId());
        WfUserActorBean manager = new WfUserActorBean();
        manager.setActorId("10");
        wfRecord.setManager(manager);

        WfTransferActorsRequestBean request = new WfTransferActorsRequestBean();
        request.setFromUserId("2");
        request.setToUserId("9");

        WfTransferResultBean result = FutureHelper.syncGet(
                workflowService.transferActorsAsync(request, null, newContext("10")));
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedItems().size());
        assertNotNull(findActivatedStepByOwner(workflow, "9"));
    }

    @Test
    public void testTransferActorsRejectsNonExistentToUser() {
        IWorkflow workflow = startWorkflow("test/execGroupVote", "1");
        invokeAction(workflow, "wf-start", "1", "sh");
        assertNotNull(findActivatedStepByOwner(workflow, "2"));

        WfTransferActorsRequestBean request = new WfTransferActorsRequestBean();
        request.setFromUserId("2");
        request.setToUserId(MISSING_USER_ID);

        NopException e = assertThrows(NopException.class,
                () -> FutureHelper.syncGet(workflowService.transferActorsAsync(request, null, newContext("2"))));
        assertEquals(NopWfCoreErrors.ERR_WF_USER_NOT_EXISTS.getErrorCode(), e.getErrorCode());

        // toUserId 不存在时 owner 不被清空
        assertNotNull(findActivatedStepByOwner(workflow, "2"));
    }

    private IWorkflowStep findActivatedStepByOwner(IWorkflow workflow, String ownerId) {
        return workflow.getActivatedSteps().stream()
                .filter(step -> ownerId.equals(step.getRecord().getOwnerId()))
                .findFirst().orElse(null);
    }
}
