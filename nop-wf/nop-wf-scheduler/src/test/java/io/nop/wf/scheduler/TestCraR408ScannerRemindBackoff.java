package io.nop.wf.scheduler;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.core.context.IServiceContext;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.48 R4-08（plan 2541）W-01（1.5-MA-06 残留面，裁定 E）红①：remind 半区失败退避 +
 * due 扫描 backlog gauge。2514 裁定④已收敛 due 半区主面（本钉零触 due 退避/fencing/分批——
 * diff 分组禁交叉；{@code testScanDueTasksIgnoresCurrentStepStatusRace} 与
 * {@code TestWfTaskScannerFencing} 零改写由既有钉族承载）。
 *
 * <p>红① 测试缝 = remindTime 注入行突破「恒空集前置」（业务侧写入形态）+ listener 持续失败；
 * 断言：失败条目 remindTime 持久化推进退出工作集（与 due 半区收敛语义等价）+ 两枚 gauge
 * 真实注册且值反映（接线断言——gauge 真实注册非字段存在）。
 */
public class TestCraR408ScannerRemindBackoff extends BaseTestCase {

    private static final ErrorCode TEST_PROBE_ERROR =
            define("nop.err.test.r408.probe", "r408 test probe");


    @Test
    public void remindListenerFailure_backsOffAndExitsWorkSet() {
        // 红①(a)：remind listener 持续失败 → remindTime 持久化推进（修复前 catch 仅 LOG.error，
        // 失败记录不退出工作集、每轮重扫重放 ERROR）
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId("wf-r408");
        record.setStepId("step-r408");
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        Timestamp injectedRemindTime = new Timestamp(System.currentTimeMillis() - 1000);
        record.setRemindTime(injectedRemindTime);

        AtomicInteger saveCount = new AtomicInteger();
        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(record),
                stepRecord -> {
                    saveCount.incrementAndGet();
                    return null;
                }));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy(null)))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setReminderListeners(List.of(step -> {
            throw new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR);
        }));
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanRemindTasks();

        Timestamp remindTime = record.getRemindTime();
        assertNotNull(remindTime, "failed remind step must have its remindTime postponed (W-01(a))");
        assertTrue(remindTime.getTime() >= System.currentTimeMillis() + 30_000,
                "remindTime must advance into the future (exit the scan work set, due-semantics equivalent); "
                        + "got " + remindTime + " vs injected " + injectedRemindTime);
        assertTrue(saveCount.get() >= 1,
                "postponement must be persisted via saveStepRecord (default postponeStepRemindTime path)");
    }

    @Test
    public void remindListenerSuccess_noBackoffAndRemindTimeCleared() {
        // 对照腿：listener 成功 → incrementRemindCount 清空 remindTime（既有语义零回退）+
        // 失败计数退出（无退避）
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId("wf-r408-ok");
        record.setStepId("step-r408-ok");
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        record.setRemindTime(new Timestamp(System.currentTimeMillis() - 1000));

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(), List.of(record), stepRecord -> null));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy(null)))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setReminderListeners(List.of(step -> { /* success */ }));
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanRemindTasks();

        assertNull(record.getRemindTime(),
                "successful remind must clear remindTime via incrementRemindCount (legacy semantics preserved)");
    }

    @Test
    public void dueScanGauges_registeredAndReflectRound() {
        // 红①(b)：due 扫描 backlog gauge 真实注册（接线断言）+ 值反映最近一轮消费/失败
        WorkflowStepRecordBean dueRecord = new WorkflowStepRecordBean();
        dueRecord.setWfId("wf-r408-g");
        dueRecord.setStepId("step-r408-g");
        dueRecord.setStepName("review");
        dueRecord.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(storeProxy(List.of(dueRecord), List.of(), stepRecord -> null));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(dueRecord), workflowModelProxy(stepModelProxy("approve")))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());
        scanner.init(); // gauge 注册挂点（@PostConstruct 生产形态）

        MeterRegistry registry = GlobalMeterRegistry.instance();
        assertNotNull(registry.find("nop.wf.scheduler.due-scan-scanned").gauge(),
                "due-scan-scanned gauge must be registered at scanner init (W-01(b); pre-fix: absent)");
        assertNotNull(registry.find("nop.wf.scheduler.due-scan-failures").gauge(),
                "due-scan-failures gauge must be registered at scanner init (W-01(b); pre-fix: absent)");

        Number scannedBefore = registry.find("nop.wf.scheduler.due-scan-scanned").gauge().value();
        scanner.scanDueTasks();
        Number scannedAfter = registry.find("nop.wf.scheduler.due-scan-scanned").gauge().value();
        assertEquals(scannedBefore.doubleValue() + 1.0, scannedAfter.doubleValue(),
                "scanned gauge must reflect the round's consumed count (wiring: real registration + real update)");
    }

    // ==================== proxy harness（沿 TestWfTaskScanner 形态，自包含） ====================

    private WorkflowServiceSpi inertWorkflowService() {
        return new WorkflowServiceSpi() {
            @Override
            public java.util.concurrent.CompletionStage<Object> invokeActionAsync(
                    io.nop.wf.api.beans.WfActionRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(
                    io.nop.wf.api.beans.WfStartRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> notifySubFlowEndAsync(
                    io.nop.wf.api.beans.WfSubFlowEndRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> killWorkflowAsync(
                    io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> suspendWorkflowAsync(
                    io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> resumeWorkflowAsync(
                    io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> signalWfAsync(
                    io.nop.wf.api.beans.WfSignalRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(
                    io.nop.wf.api.beans.WfTransferActorsRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private io.nop.wf.core.store.IWorkflowStore storeProxy(List<IWorkflowStepRecord> dueSteps,
                                                           List<IWorkflowStepRecord> remindSteps,
                                                           Function<IWorkflowStepRecord, Object> onSave) {
        return proxy(io.nop.wf.core.store.IWorkflowStore.class, (proxy, method, args) -> {
            String name = method.getName();
            if (method.isDefault()) {
                // 2514 裁定④/⑥ + plan 2541 W-01/W-02/W-12 新增接口缺省方法在内存替身上走
                // 产品缺省实现：tryClaim=true、postponeStepRemindTime 经 saveStepRecord 落盘
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
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
            if ("getActions".equals(name) || "getTransitionFromSteps".equals(name)
                    || "getTransitionToSteps".equals(name)) {
                return List.of();
            }
            if ("getTransitionFromStepNames".equals(name) || "getTransitionToStepNames".equals(name)
                    || "getWaitStepNames".equals(name) || "getWaitSignals".equals(name)) {
                return java.util.Set.of();
            }
            return defaultValue(method.getReturnType());
        });
    }

    private io.nop.orm.IOrmTemplate ormTemplateProxy() {
        return proxy(io.nop.orm.IOrmTemplate.class, (proxy, method, args) -> {
            if (method.getName().equals("runInNewSession")) {
                java.util.function.Function<?, ?> fn = (java.util.function.Function<?, ?>) args[0];
                return fn.apply(null);
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("toString"))
                            return type.getSimpleName() + "@r408-proxy";
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(proxy);
                        if (method.getName().equals("equals"))
                            return proxy == args[0];
                        return null;
                    }
                    return handler.invoke(proxy, method, args);
                });
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
