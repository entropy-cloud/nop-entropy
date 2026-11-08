package io.nop.wf.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.metrics.GlobalMeterRegistry;
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
import io.nop.wf.core.store.IWorkflowStore;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）裁定 A：W-06（1.5-C4-007；C10-012 宿主）红⑥ 韧性契约 +
 * W-09（1.5-C7-004）红⑨ 逐项 deadline。
 *
 * <p>使用面限定（形态 A 宿主面禁造）：本钉断言扫描器使用面无状态残留可再次触发 + 挂死条目
 * 的 deadline 跳条与退避推进；job fire 失败终态/FAILED 恢复语义归 nop-job-C7-006
 * 测试面（交叉引用登记，本类禁造宿主面断言）。
 */
public class TestWfScanJobResilienceContractR409 extends BaseTestCase {

    /**
     * 红⑥(a)：扫描体失败（分批查询异常）不外泄——入口异常边界收敛后 fire 正常收尾，
     * 且无状态残留、可再次触发。
     */
    @Test
    public void scanRoundFailure_doesNotPropagateAndIsRetriggerable() {
        AtomicInteger queryAttempts = new AtomicInteger();
        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(failingQueryStore(queryAttempts));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(newRecord("wf-r409-fail")), workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.init();

        MeterRegistry registry = GlobalMeterRegistry.instance();
        assertNotNull(registry.find("nop.wf.scheduler.scan-round-failures").gauge(),
                "scan-round-failures gauge must be registered at scanner init (W-06(a); pre-fix: absent)");
        double before = registry.find("nop.wf.scheduler.scan-round-failures").gauge().value();

        // pre-fix：查询异常穿透入口，本调用直接抛出（job fire → ERROR/FAILED 永久停摆）
        scanner.scanDueTasks();
        scanner.scanDueTasks();

        assertEquals(2, queryAttempts.get(),
                "scanner must remain retriggerable after a failed round (no state residue; W-06(a) contract)");
        assertEquals(before + 2.0,
                registry.find("nop.wf.scheduler.scan-round-failures").gauge().value(),
                "each failed round must be counted by scan-round-failures gauge (real wiring)");
    }

    /**
     * 红⑥(b) 对照腿：成功轮不增轮失败计数（防「一律计数」伪绿）。
     */
    @Test
    public void scanRoundSuccess_doesNotIncrementRoundFailureGauge() {
        WorkflowStepRecordBean record = newRecord("wf-r409-ok");
        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(), r -> null));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy(null)))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.init();

        MeterRegistry registry = GlobalMeterRegistry.instance();
        double before = registry.find("nop.wf.scheduler.scan-round-failures").gauge().value();
        scanner.scanDueTasks();
        assertEquals(before, registry.find("nop.wf.scheduler.scan-round-failures").gauge().value(),
                "successful round must not increment scan-round-failures");
    }

    /**
     * 红⑨：dueAction 永不完成 → 扫描轮在 action-deadline-ms 内返回、该条跳条并退避推进
     * dueTime（pre-fix：syncGet 无 deadline，本调用永久挂起）。
     */
    @Test
    public void dueActionHang_deadlineSkipsAndPostpones() throws Exception {
        setTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS, 500);
        try {
            WorkflowStepRecordBean record = newRecord("wf-r409-hang");
            CountDownLatch neverCompletes = new CountDownLatch(1);
            AtomicInteger saveCount = new AtomicInteger();

            WfTaskScanner scanner = new WfTaskScanner();
            scanner.setWorkflowStore(storeProxy(List.of(record), List.of(), r -> {
                saveCount.incrementAndGet();
                return null;
            }));
            scanner.setWorkflowManager(workflowManagerProxy(
                    workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy("approve")))));
            scanner.setWorkflowService(hangingWorkflowService(neverCompletes));
            scanner.setOrmTemplate(ormTemplateProxy());
            scanner.init();

            long start = System.currentTimeMillis();
            scanner.scanDueTasks();
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 30_000,
                    "scan round must return within the watchdog bound; hung action must not block forever (W-09)");
            assertTrue(elapsed >= 400,
                    "deadline wait must have actually engaged (not a zero-wait shortcut); elapsed=" + elapsed);
            assertNotNull(record.getDueTime(), "deadline-exceeded entry must be postponed");
            assertTrue(record.getDueTime().getTime() >= System.currentTimeMillis() + 30_000,
                    "postponed dueTime must advance into the future (exit scan work set, W-09 backoff)");
            assertTrue(saveCount.get() >= 1, "postponement must persist via saveStepRecord");
        } finally {
            clearTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS.getName());
        }
    }

    /**
     * 红⑨ 对照腿：deadline=0 → 禁用超时（长动作部署显式逃生门），无 deadline 语义介入。
     */
    @Test
    public void actionDeadlineDisabled_usesLegacySyncGetSemantics() {
        setTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS, 0);
        try {
            WorkflowStepRecordBean record = newRecord("wf-r409-disable");
            WfTaskScanner scanner = new WfTaskScanner();
            scanner.setWorkflowStore(storeProxy(List.of(record), List.of(), r -> null));
            scanner.setWorkflowManager(workflowManagerProxy(
                    workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy("approve")))));
            scanner.setWorkflowService(inertWorkflowService());
            scanner.setOrmTemplate(ormTemplateProxy());
            scanner.init();

            scanner.scanDueTasks();
            // 成功路径退出失败计数、不推进 dueTime（既有语义零回退）
            assertEquals(null, record.getDueTime(),
                    "disabled deadline keeps legacy behavior: success path must not postpone");
        } finally {
            clearTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS.getName());
        }
    }

    /**
     * 装配期校验：action-deadline-ms &lt; 0 必须 fail-fast（显式失败，非静默钳制）。
     */
    @Test
    public void negativeDeadline_failsFastAtInit() {
        setTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS, -1);
        try {
            WfTaskScanner scanner = new WfTaskScanner();
            scanner.setWorkflowStore(storeProxy(List.of(), List.of(), r -> null));
            scanner.setOrmTemplate(ormTemplateProxy());
            NopException ex = org.junit.jupiter.api.Assertions.assertThrows(NopException.class, scanner::init);
            assertEquals(NopWfCoreErrors.ERR_WF_INVALID_SCHEDULER_CONFIG.getErrorCode(), ex.getErrorCode());
        } finally {
            clearTestConfig(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS.getName());
        }
    }

    // ==================== harness ====================

    private WorkflowStepRecordBean newRecord(String wfId) {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId(wfId);
        record.setStepId("step-" + wfId);
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        return record;
    }

    private IWorkflowStore failingQueryStore(AtomicInteger attempts) {
        return proxy(IWorkflowStore.class, (proxy, method, args) -> {
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            if ("findDueActivatedSteps".equals(method.getName())) {
                attempts.incrementAndGet();
                throw new IllegalStateException("simulated batch query failure");
            }
            return defaultValue(method.getReturnType());
        });
    }

    private WorkflowServiceSpi hangingWorkflowService(CountDownLatch neverCompletes) {
        return new BaseWorkflowService() {
            @Override
            public CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, FieldSelectionBean selection,
                                                             IServiceContext ctx) {
                CompletableFuture<Object> f = new CompletableFuture<>();
                // 永不完成（模拟挂死外部动作）；延迟线程仅为防测试自身泄漏闩带
                CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
                    neverCompletes.countDown();
                    f.complete(null);
                });
                return f;
            }
        };
    }

    private WorkflowServiceSpi inertWorkflowService() {
        return new BaseWorkflowService();
    }

    private static class BaseWorkflowService implements WorkflowServiceSpi {
        @Override
        public CompletionStage<Object> invokeActionAsync(WfActionRequestBean request, FieldSelectionBean selection,
                                                         IServiceContext ctx) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(
                io.nop.wf.api.beans.WfStartRequestBean request, FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> notifySubFlowEndAsync(io.nop.wf.api.beans.WfSubFlowEndRequestBean request,
                                                          FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> killWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                                                       FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> suspendWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                                                          FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> resumeWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                                                         FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> signalWfAsync(io.nop.wf.api.beans.WfSignalRequestBean request,
                                                   FieldSelectionBean selection, IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(
                io.nop.wf.api.beans.WfTransferActorsRequestBean request, FieldSelectionBean selection,
                IServiceContext ctx) {
            throw new UnsupportedOperationException();
        }
    }

    private IOrmTemplate ormTemplateProxy() {
        return proxy(IOrmTemplate.class, (proxy, method, args) -> {
            if (method.getName().equals("runInNewSession")) {
                @SuppressWarnings("unchecked")
                Function<IOrmSession, Object> callback = (Function<IOrmSession, Object>) args[0];
                return callback.apply(null);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStore storeProxy(List<IWorkflowStepRecord> dueSteps, List<IWorkflowStepRecord> remindSteps,
                                      Function<IWorkflowStepRecord, Object> onSave) {
        return proxy(IWorkflowStore.class, (proxy, method, args) -> {
            String name = method.getName();
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            if ("findDueActivatedSteps".equals(name))
                return dueSteps;
            if ("findRemindActivatedSteps".equals(name))
                return remindSteps;
            if ("saveStepRecord".equals(name))
                return onSave.apply((IWorkflowStepRecord) args[0]);
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowManager workflowManagerProxy(IWorkflow workflow) {
        return proxy(IWorkflowManager.class, (proxy, method, args) -> {
            if ("getWorkflow".equals(method.getName()))
                return workflow;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflow workflowProxy(IWorkflowStep step, IWorkflowModel model) {
        return proxy(IWorkflow.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getStepById".equals(name))
                return Objects.equals(step.getStepId(), args[0]) ? step : null;
            if ("getModel".equals(name))
                return model;
            if ("getWfId".equals(name))
                return step.getWfId();
            if ("getWfName".equals(name))
                return "test/scheduler";
            if ("getWfVersion".equals(name))
                return 1L;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStep stepProxy(WorkflowStepRecordBean record) {
        return proxy(IWorkflowStep.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getRecord".equals(name))
                return record;
            if ("getStepId".equals(name))
                return record.getStepId();
            if ("getStepName".equals(name))
                return record.getStepName();
            if ("getWfId".equals(name))
                return record.getWfId();
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowModel workflowModelProxy(IWorkflowStepModel stepModel) {
        return proxy(IWorkflowModel.class, (proxy, method, args) -> {
            if ("getStep".equals(method.getName()))
                return Objects.equals(stepModel.getName(), args[0]) ? stepModel : null;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStepModel stepModelProxy(String dueAction) {
        return proxy(IWorkflowStepModel.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getName".equals(name))
                return "review";
            if ("getDueAction".equals(name))
                return dueAction;
            if ("getActions".equals(name) || "getTransitionFromSteps".equals(name)
                    || "getTransitionToSteps".equals(name))
                return List.of();
            if ("getTransitionFromStepNames".equals(name) || "getTransitionToStepNames".equals(name)
                    || "getWaitStepNames".equals(name) || "getWaitSignals".equals(name))
                return java.util.Set.of();
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE)
            return null;
        if (!returnType.isPrimitive()) {
            if (List.class.isAssignableFrom(returnType))
                return new ArrayList<>();
            if (Map.class.isAssignableFrom(returnType))
                return Map.of();
            return null;
        }
        if (returnType == boolean.class)
            return false;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        if (returnType == double.class)
            return 0D;
        if (returnType == float.class)
            return 0F;
        if (returnType == short.class)
            return (short) 0;
        if (returnType == byte.class)
            return (byte) 0;
        if (returnType == char.class)
            return (char) 0;
        return null;
    }
}
