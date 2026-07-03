package io.nop.wf.scheduler;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.service.WorkflowServiceSpi;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestWfTaskScanner extends BaseTestCase {
    @Test
    public void testScanDueTasksInvokesDueAction() {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId("wf-1");
        record.setStepId("step-1");
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);

        AtomicReference<WfActionRequestBean> requestRef = new AtomicReference<>();
        AtomicReference<IServiceContext> contextRef = new AtomicReference<>();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(record), List.of()));
        scanner.setWorkflowManager(workflowManagerProxy(workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(new WorkflowServiceSpi() {
            @Override
            public java.util.concurrent.CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                requestRef.set(request);
                contextRef.set(ctx);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(io.nop.wf.api.beans.WfStartRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> notifySubFlowEndAsync(io.nop.wf.api.beans.WfSubFlowEndRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> killWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> suspendWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> resumeWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> signalWfAsync(io.nop.wf.api.beans.WfSignalRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(io.nop.wf.api.beans.WfTransferActorsRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }
        });
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanDueTasks();

        assertEquals("wf-1", requestRef.get().getWfId());
        assertEquals("step-1", requestRef.get().getStepId());
        assertEquals("approve", requestRef.get().getActionName());
        assertEquals("wf-scheduler", contextRef.get().getContext().getUserId());
    }

    @Test
    public void testScanDueTasksIgnoresCurrentStepStatusRace() {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId("wf-2");
        record.setStepId("step-2");
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(record), List.of()));
        scanner.setWorkflowManager(workflowManagerProxy(workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(new WorkflowServiceSpi() {
            @Override
            public java.util.concurrent.CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                NopException error = new NopException(NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS)
                        .param(NopWfCoreErrors.ARG_WF_NAME, "test")
                        .param(NopWfCoreErrors.ARG_STEP_NAME, "review")
                        .param(NopWfCoreErrors.ARG_ACTION_NAME, "approve")
                        .param(NopWfCoreErrors.ARG_STEP_STATUS, record.getStatus());
                return CompletableFuture.failedFuture(error);
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(io.nop.wf.api.beans.WfStartRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> notifySubFlowEndAsync(io.nop.wf.api.beans.WfSubFlowEndRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> killWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> suspendWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> resumeWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> signalWfAsync(io.nop.wf.api.beans.WfSignalRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(io.nop.wf.api.beans.WfTransferActorsRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }
        });
        scanner.setOrmTemplate(ormTemplateProxy());

        assertDoesNotThrow(scanner::scanDueTasks);
    }

    @Test
    public void testScanRemindTasksNotifiesAndUpdatesRecord() {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId("wf-3");
        record.setStepId("step-3");
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        record.setRemindTime(new Timestamp(System.currentTimeMillis()));

        IWorkflowStep step = stepProxy(record);
        AtomicReference<IWorkflowStep> remindedStep = new AtomicReference<>();
        AtomicInteger saveCount = new AtomicInteger();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(record), stepRecord -> {
            saveCount.incrementAndGet();
            return null;
        }));
        scanner.setWorkflowManager(workflowManagerProxy(workflowProxy(step, workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(unsupportedWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.setReminderListeners(List.of(remindedStep::set));

        scanner.scanRemindTasks();

        assertSame(step, remindedStep.get());
        assertEquals(1, saveCount.get());
        assertEquals(1, record.getRemindCount());
        assertNull(record.getRemindTime());
    }

    private WorkflowServiceSpi unsupportedWorkflowService() {
        return new WorkflowServiceSpi() {
            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(io.nop.wf.api.beans.WfStartRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> notifySubFlowEndAsync(io.nop.wf.api.beans.WfSubFlowEndRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> killWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> suspendWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> resumeWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> signalWfAsync(io.nop.wf.api.beans.WfSignalRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(io.nop.wf.api.beans.WfTransferActorsRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private IOrmTemplate ormTemplateProxy() {
        return proxy(IOrmTemplate.class, (proxy, method, args) -> {
            if (method.getName().equals("runInNewSession")) {
                @SuppressWarnings("unchecked")
                Function<IOrmSession, Object> callback = (Function<IOrmSession, Object>) args[0];
                return callback.apply(null);
            }
            if (method.getReturnType() == boolean.class) {
                return false;
            }
            if (method.getReturnType() == int.class) {
                return 0;
            }
            if (method.getReturnType() == long.class) {
                return 0L;
            }
            return null;
        });
    }

    private io.nop.wf.core.store.IWorkflowStore storeProxy(List<IWorkflowStepRecord> dueSteps,
                                                            List<IWorkflowStepRecord> remindSteps) {
        return storeProxy(dueSteps, remindSteps, stepRecord -> null);
    }

    private io.nop.wf.core.store.IWorkflowStore storeProxy(List<IWorkflowStepRecord> dueSteps,
                                                            List<IWorkflowStepRecord> remindSteps,
                                                            Function<IWorkflowStepRecord, Object> onSave) {
        return proxy(io.nop.wf.core.store.IWorkflowStore.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findDueActivatedSteps".equals(name)) {
                return dueSteps;
            }
            if ("findRemindActivatedSteps".equals(name)) {
                return remindSteps;
            }
            if ("saveStepRecord".equals(name)) {
                return onSave.apply((IWorkflowStepRecord) args[0]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowManager workflowManagerProxy(IWorkflow workflow) {
        return proxy(IWorkflowManager.class, (proxy, method, args) -> {
            if (method.getName().equals("getWorkflow")) {
                return workflow;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflow workflowProxy(IWorkflowStep step, IWorkflowModel model) {
        return proxy(IWorkflow.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getStepById".equals(name)) {
                return Objects.equals(step.getStepId(), args[0]) ? step : null;
            }
            if ("getModel".equals(name)) {
                return model;
            }
            if ("getWfId".equals(name)) {
                return step.getWfId();
            }
            if ("getWfName".equals(name)) {
                return "test/scheduler";
            }
            if ("getWfVersion".equals(name)) {
                return 1L;
            }
            if ("getRecord".equals(name)) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStep stepProxy(WorkflowStepRecordBean record) {
        return proxy(IWorkflowStep.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getRecord".equals(name)) {
                return record;
            }
            if ("getStepId".equals(name)) {
                return record.getStepId();
            }
            if ("getStepName".equals(name)) {
                return record.getStepName();
            }
            if ("getWfId".equals(name)) {
                return record.getWfId();
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowModel workflowModelProxy(IWorkflowStepModel stepModel) {
        return proxy(IWorkflowModel.class, (proxy, method, args) -> {
            if (method.getName().equals("getStep")) {
                return Objects.equals(stepModel.getName(), args[0]) ? stepModel : null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStepModel stepModelProxy(String dueAction) {
        return proxy(IWorkflowStepModel.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getName".equals(name)) {
                return "review";
            }
            if ("getDueAction".equals(name)) {
                return dueAction;
            }
            if ("getActions".equals(name) || "getTransitionFromSteps".equals(name) || "getTransitionToSteps".equals(name)) {
                return List.of();
            }
            if ("getTransitionFromStepNames".equals(name) || "getTransitionToStepNames".equals(name)
                    || "getWaitStepNames".equals(name) || "getWaitSignals".equals(name)) {
                return java.util.Set.of();
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            if (List.class.isAssignableFrom(returnType)) {
                return new ArrayList<>();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return null;
    }
}
