package io.nop.wf.service;

import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.api.actor.WfActorAndOwner;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.support.ApprovalFlowHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.nop.wf.service.DaoTestHelper.saveUser;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractWorkflowTestCase extends JunitAutoTestCase {
    @Inject
    IWorkflowManager workflowManager;

    @Inject
    IOrmTemplate ormTemplate;

    @BeforeEach
    public void init() {
        saveUser("test001");
        saveUser("test002");
        saveUser("test003");
        saveUser("test004");
        saveUser("test005");
    }

    protected <T> T run(Supplier<T> task) {
        return ormTemplate.runInNewSession(session -> {
            return task.get();
        });
    }

    protected IServiceContext newServiceContext(String userId) {
        ServiceContextImpl ctx = new ServiceContextImpl();
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName("User" + userId);
        ctx.setUserContext(userContext);
        return ctx;
    }

    protected String startWorkflow(String wfName, String userId, Map<String, Object> args) {
        return run(() -> {
            IServiceContext ctx = newServiceContext(userId);
            IWorkflow wf = workflowManager.newWorkflow(wfName, null);
            ApprovalFlowHelper.start(wf, args, ctx);
            return wf.getWfId();
        });
    }

    protected void executeTask(String wfId, String userId, String stepName, Consumer<IWorkflowStep> task) {
        run(() -> {
            IWorkflow wf = workflowManager.getWorkflow(wfId);
            Optional<? extends IWorkflowStep> userStep = wf.getActivatedSteps().stream().filter(step -> {
                if (stepName != null && !stepName.equals(step.getStepName()))
                    return false;
                return step.getActor().containsUser(userId);
            }).findFirst();
            assertTrue(userStep.isPresent());
            userStep.ifPresent(task);
            return null;
        });
    }

    protected void executeActiveTasks(String wfId, String userId, String stepName, Consumer<IWorkflowStep> task) {
        run(() -> {
            IWorkflow wf = workflowManager.getWorkflow(wfId);
            List<? extends IWorkflowStep> steps = wf.getActivatedSteps().stream().filter(step -> {
                if (stepName != null && !stepName.equals(step.getStepName()))
                    return false;
                return step.getActor().containsUser(userId);
            }).collect(Collectors.toList());
            assertFalse(steps.isEmpty());
            steps.forEach(task);
            return null;
        });
    }

    protected void executeActiveTasks(String wfId, String userId, String stepName) {
        IServiceContext ctx = newServiceContext(userId);
        executeActiveTasks(wfId, userId, stepName, step -> {
            step.invokeAction("complete", null, ctx);
            step.getWorkflow().runAutoTransitions(ctx);
        });
    }

    protected void executeTask(String wfId, String userId, String stepName) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            step.invokeAction("complete", null, ctx);
            step.getWorkflow().runAutoTransitions(ctx);
        });
    }

    protected void executeJump(String wfId, String userId, String stepName, String targetStepName) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            ApprovalFlowHelper.jump(step, targetStepName, ctx);
        });
    }

    protected void executeTransferToUser(String wfId, String userId, String stepName, String nextUserId) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            ApprovalFlowHelper.transferToUser(step, nextUserId, ctx);
        });
    }

    protected void executeDelegate(String wfId, String userId, String stepName, String nextUserId) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            WfActorAndOwner actorAndOwner = new WfActorAndOwner();
            actorAndOwner.setActorId(nextUserId);
            actorAndOwner.setActorType("user");
            step.transferToActor(actorAndOwner, ctx);
            step.getWorkflow().runAutoTransitions(ctx);
        });
    }


    protected void executeReject(String wfId, String userId, String stepName) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            step.invokeAction("reject", null, ctx);
            step.getWorkflow().runAutoTransitions(ctx);
        });
    }

    protected void executeRejectTo(String wfId, String userId, String stepName, String targetStepName) {
        IServiceContext ctx = newServiceContext(userId);
        executeTask(wfId, userId, stepName, step -> {
            step.invokeAction("reject", null, ctx);
            step.getWorkflow().runAutoTransitions(ctx);
        });
    }

    protected List<? extends IWorkflowStep> getActivatedSteps(String wfId) {
        return workflowManager.getWorkflow(wfId).getActivatedSteps();
    }
}
