package io.nop.wf.service;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.service.mock.MockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWorkflowActionRecordFacade extends BaseTestCase {
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
        workflowManager.setWorkflowStore(new MockWorkflowStore());
        workflowManager.init();
    }

    @Test
    public void testWorkflowAndStepActionRecordsFacade() {
        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId("1");
        IWorkflow workflow = workflowManager.newWorkflow("test/testBasic", 1L);
        workflow.start(null, context);

        IWorkflowStep startStep = workflow.getLatestStartStep();
        startStep.invokeAction("action0", null, context);
        workflow.runAutoTransitions(context);

        List<? extends IWorkflowActionRecord> wfActions = workflow.getActionRecords();
        assertFalse(wfActions.isEmpty());
        assertEquals("action0", wfActions.get(0).getActionName());

        List<? extends IWorkflowActionRecord> stepActions = startStep.getActionRecords();
        assertEquals(1, stepActions.size());
        assertEquals("action0", stepActions.get(0).getActionName());

        IWorkflowStep endStep = workflow.getLatestStepByName("wf-end");
        assertNotNull(endStep);
        assertTrue(endStep.getActionRecords().isEmpty());
    }
}
