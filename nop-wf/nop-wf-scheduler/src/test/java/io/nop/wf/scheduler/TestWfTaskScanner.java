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

    /**
     * 回归 check2 P2：getDueAction 阶段单条失败（如实例已被删除）不得中断整批到期扫描。
     */
    @Test
    public void testScanDueTasksContinuesAfterGetDueActionFailure() {
        WorkflowStepRecordBean broken = dueRecord("wf-broken", "step-b");
        WorkflowStepRecordBean healthy = dueRecord("wf-ok", "step-o");

        List<WfActionRequestBean> invoked = new ArrayList<>();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(broken, healthy), List.of()));
        scanner.setWorkflowManager(proxy(IWorkflowManager.class, (p, method, args) -> {
            if ("getWorkflow".equals(method.getName())) {
                if ("wf-broken".equals(args[0]))
                    throw new NopException(NopWfCoreErrors.ERR_WF_MISSING_WF_INSTANCE)
                            .param(NopWfCoreErrors.ARG_WF_ID, args[0]);
                return workflowProxy(stepProxy(healthy), workflowModelProxy(stepModelProxy("approve")));
            }
            return defaultValue(method.getReturnType());
        }));
        scanner.setWorkflowService(recordingWorkflowService(invoked, request -> CompletableFuture.completedFuture(null)));
        scanner.setOrmTemplate(ormTemplateProxy());

        assertDoesNotThrow(scanner::scanDueTasks);
        assertEquals(1, invoked.size());
        assertEquals("wf-ok", invoked.get(0).getWfId());
    }

    /**
     * 钉死行为：到期动作执行抛出非 NopException 的 RuntimeException（经 FutureHelper.syncGet
     * 适配为 NopException 或由 catch(Exception) 兜底）同样不得中断整批到期扫描。
     */
    @Test
    public void testScanDueTasksContinuesAfterNonNopActionFailure() {
        WorkflowStepRecordBean broken = dueRecord("wf-broken", "step-b");
        WorkflowStepRecordBean healthy = dueRecord("wf-ok", "step-o");

        List<WfActionRequestBean> invoked = new ArrayList<>();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(broken, healthy), List.of()));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(healthy), workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(recordingWorkflowService(invoked, request -> {
            if ("wf-broken".equals(request.getWfId()))
                return CompletableFuture.failedFuture(new IllegalStateException("due-action-boom"));
            return CompletableFuture.completedFuture(null);
        }));
        scanner.setOrmTemplate(ormTemplateProxy());

        assertDoesNotThrow(scanner::scanDueTasks);
        // 两条任务的到期动作都被触发，第一条失败不影响第二条
        assertEquals(2, invoked.size());
        assertEquals("wf-ok", invoked.get(1).getWfId());
    }

    /**
     * 回归 check2 P2：提醒扫描中单条失败（实例加载抛错 / listener 抛错）不得中断整批，
     * 且失败的记录不更新提醒计数（下轮扫描可重试）。
     */
    @Test
    public void testScanRemindTasksContinuesAfterFailure() {
        WorkflowStepRecordBean broken = remindRecord("wf-broken", "step-b");
        WorkflowStepRecordBean healthy = remindRecord("wf-ok", "step-o");

        IWorkflowStep healthyStep = stepProxy(healthy);
        List<IWorkflowStep> reminded = new ArrayList<>();
        AtomicInteger saveCount = new AtomicInteger();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(broken, healthy), stepRecord -> {
            saveCount.incrementAndGet();
            return null;
        }));
        scanner.setWorkflowManager(proxy(IWorkflowManager.class, (p, method, args) -> {
            if ("getWorkflow".equals(method.getName())) {
                if ("wf-broken".equals(args[0]))
                    throw new NopException(NopWfCoreErrors.ERR_WF_MISSING_WF_INSTANCE)
                            .param(NopWfCoreErrors.ARG_WF_ID, args[0]);
                return workflowProxy(healthyStep, workflowModelProxy(stepModelProxy("approve")));
            }
            return defaultValue(method.getReturnType());
        }));
        scanner.setWorkflowService(unsupportedWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.setReminderListeners(List.of(step -> {
            if (step == healthyStep)
                reminded.add(step);
        }));

        assertDoesNotThrow(scanner::scanRemindTasks);
        assertEquals(1, reminded.size());
        assertEquals(1, saveCount.get());
        assertEquals(1, healthy.getRemindCount());
    }

    /**
     * 回归 check2 P2：listener 抛错被隔离后，该记录的提醒计数不更新（未成功送达不计数）。
     */
    @Test
    public void testScanRemindTasksListenerFailureDoesNotBreakBatch() {
        WorkflowStepRecordBean first = remindRecord("wf-1", "step-1");
        WorkflowStepRecordBean second = remindRecord("wf-2", "step-2");

        List<IWorkflowStep> reminded = new ArrayList<>();
        AtomicInteger saveCount = new AtomicInteger();

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(first, second), stepRecord -> {
            saveCount.incrementAndGet();
            return null;
        }));
        scanner.setWorkflowManager(proxy(IWorkflowManager.class, (p, method, args) -> {
            if ("getWorkflow".equals(method.getName())) {
                WorkflowStepRecordBean record = "wf-1".equals(args[0]) ? first : second;
                return workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy("approve")));
            }
            return defaultValue(method.getReturnType());
        }));
        scanner.setWorkflowService(unsupportedWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.setReminderListeners(List.of(step -> {
            if (step.getWfId().equals("wf-1"))
                throw new IllegalStateException("remind-listener-boom");
            reminded.add(step);
        }));

        assertDoesNotThrow(scanner::scanRemindTasks);
        assertEquals(1, reminded.size());
        assertEquals("wf-2", reminded.get(0).getWfId());
        assertEquals(1, saveCount.get());
        assertNull(first.getRemindCount());
        assertEquals(1, second.getRemindCount());
    }

    private static WorkflowStepRecordBean dueRecord(String wfId, String stepId) {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId(wfId);
        record.setStepId(stepId);
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        return record;
    }

    private static WorkflowStepRecordBean remindRecord(String wfId, String stepId) {
        WorkflowStepRecordBean record = dueRecord(wfId, stepId);
        record.setRemindTime(new Timestamp(System.currentTimeMillis()));
        return record;
    }

    private WorkflowServiceSpi recordingWorkflowService(List<WfActionRequestBean> captured,
                                                        java.util.function.Function<WfActionRequestBean,
                                                                java.util.concurrent.CompletionStage<Object>> handler) {
        return new WorkflowServiceSpi() {
            @Override
            public java.util.concurrent.CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                captured.add(request);
                return handler.apply(request);
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
        };
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
